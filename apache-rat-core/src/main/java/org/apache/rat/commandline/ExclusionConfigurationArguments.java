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
package org.apache.rat.commandline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.Option;
import org.apache.rat.utils.StandardXmlFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Expands a serialized RAT exclusion configuration into the equivalent CLI
 * exclusion arguments.
 */
public final class ExclusionConfigurationArguments {

    /** CLI option used to import a serialized exclusion configuration. */
    public static final String OPTION = "input-exclusion-config";

    /** Root element used by the exclusion configuration serialization. */
    private static final String ROOT = "ExclusionProcessor";

    private ExclusionConfigurationArguments() {
        // utility class
    }

    /**
     * Creates the CLI option description used by the command-line frontend.
     *
     * @return a new option instance
     */
    public static Option option() {
        return Option.builder()
                .longOpt(OPTION)
                .argName("File")
                .hasArg()
                .desc("Load exclusion policy exported by another RAT frontend")
                .build();
    }

    /**
     * Replaces each {@code --input-exclusion-config} option with the RAT CLI
     * arguments represented by that file.
     *
     * <p>RAT's include and exclude options accept multiple values. If a
     * positional source immediately follows the imported configuration, a
     * standard {@code --} delimiter is inserted so Commons CLI does not consume
     * that source as another exclusion value.</p>
     *
     * @param args original CLI arguments
     * @return expanded CLI arguments
     * @throws IOException if an exclusion configuration cannot be read
     */
    public static String[] expand(final String[] args) throws IOException {
        List<String> result = new ArrayList<>();
        String option = "--" + OPTION;
        String optionPrefix = option + "=";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (option.equals(arg)) {
                if (++i >= args.length) {
                    throw new IOException(option + " requires a file argument");
                }
                append(result, Path.of(args[i]));
                terminateBeforePositional(result, args, i + 1);
            } else if (arg.startsWith(optionPrefix)) {
                append(result, Path.of(arg.substring(optionPrefix.length())));
                terminateBeforePositional(result, args, i + 1);
            } else {
                result.add(arg);
            }
        }

        return result.toArray(String[]::new);
    }

    private static void terminateBeforePositional(final List<String> result, final String[] args, final int nextIndex) {
        if (nextIndex < args.length && !args[nextIndex].startsWith("-")) {
            result.add("--");
        }
    }

    private static void append(final List<String> args, final Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            args.addAll(read(input));
        }
    }

    static List<String> read(final InputStream input) throws IOException {
        final org.w3c.dom.Document document;
        try {
            document = StandardXmlFactory.documentBuilder().parse(input);
        } catch (SAXException e) {
            throw new IOException("Unable to read exclusion configuration", e);
        }

        Node root = document.getDocumentElement();
        if (root == null || !ROOT.equals(root.getNodeName())) {
            throw new IOException("Invalid exclusion configuration: expected " + ROOT);
        }

        List<String> result = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeName()) {
                case "excludedPattern" -> add(result, Arg.EXCLUDE, child, "pattern");
                case "excludedCollection" -> add(result, Arg.EXCLUDE_STD, child, "name");
                case "includedPattern" -> add(result, Arg.INCLUDE, child, "pattern");
                case "includedCollection" -> add(result, Arg.INCLUDE_STD, child, "name");
                case "fileProcessor" -> add(result, Arg.EXCLUDE_PARSE_SCM, child, "name");
                case "excludedPath", "includedPath" -> throw new IOException(
                        "Exclusion configuration contains a runtime path matcher that cannot be replayed by the CLI");
                case "#text", "#comment" -> {
                    // formatting only
                }
                default -> throw new IOException("Unknown exclusion configuration element: " + child.getNodeName());
            }
        }
        return result;
    }

    private static void add(final List<String> args, final Arg arg, final Node node, final String attribute)
            throws IOException {
        if (!(node instanceof Element element)) {
            throw new IOException("Invalid exclusion configuration element: " + node.getNodeName());
        }
        String value = element.getAttribute(attribute);
        if (value == null || value.isBlank()) {
            throw new IOException(
                    "Missing " + attribute + " attribute on exclusion configuration element " + node.getNodeName());
        }
        args.add("--" + arg.option().getLongOpt());
        args.add(value);
    }
}
