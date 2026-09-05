/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.rat;

import java.io.IOException;
import java.io.InputStream;

import org.apache.rat.report.xml.writer.XmlWriter;
import org.apache.rat.utils.StandardXmlFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads and writes the exclusion portion of a {@link ReportConfiguration}.
 *
 * <p>The exclusion state is serialized with RAT's existing
 * {@code ExclusionProcessor} representation. This preserves explicit patterns,
 * standard collections, include overrides, and SCM file processors without
 * requiring another frontend to reconstruct Maven configuration.</p>
 *
 * <p>Arbitrary runtime {@code DocumentNameMatcher} predicates cannot be
 * executed after deserialization. Import therefore fails closed if such a
 * matcher is present instead of silently changing exclusion semantics.</p>
 */
public final class ExclusionConfigurationIO {

    private static final String ROOT = "ExclusionProcessor";

    private ExclusionConfigurationIO() {
        // utility class
    }

    /**
     * Writes the configured exclusions as RAT-native XML.
     *
     * @param configuration the configuration whose exclusions should be written
     * @param appendable the destination
     * @throws IOException if the configuration cannot be written
     */
    public static void write(final ReportConfiguration configuration, final Appendable appendable) throws IOException {
        try (XmlWriter writer = new XmlWriter(appendable)) {
            configuration.getExclusionProcessor().serDes().serialize(writer);
        }
    }

    /**
     * Merges portable exclusions from RAT-native XML into an existing
     * configuration.
     *
     * @param configuration the configuration receiving the exclusions
     * @param input the serialized exclusion configuration
     * @throws IOException if the configuration cannot be read or contains a
     *         runtime path matcher that cannot be replayed
     */
    public static void read(final ReportConfiguration configuration, final InputStream input) throws IOException {
        final org.w3c.dom.Document document;
        try {
            document = StandardXmlFactory.documentBuilder().parse(input);
        } catch (SAXException e) {
            throw new IOException("Unable to read exclusion configuration", e);
        }

        final Node root = document.getDocumentElement();
        if (root == null || !ROOT.equals(root.getNodeName())) {
            throw new IOException("Invalid exclusion configuration: expected " + ROOT);
        }

        rejectRuntimePathMatchers(root);
        configuration.getExclusionProcessor().serDes().deserialize(root);
    }

    private static void rejectRuntimePathMatchers(final Node root) throws IOException {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            String name = children.item(i).getNodeName();
            if ("excludedPath".equals(name) || "includedPath".equals(name)) {
                throw new IOException(
                        "Exclusion configuration contains a runtime path matcher that cannot be replayed");
            }
        }
    }
}
