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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.rat.ExclusionConfigurationIO;
import org.apache.rat.OptionCollection;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.config.exclusion.StandardCollection;
import org.apache.rat.document.DocumentName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExclusionConfigurationArgumentsTest {

    @TempDir
    Path tempDir;

    @Test
    void expandsExclusionConfigurationBeforeCliSourceConstruction() throws Exception {
        ReportConfiguration exported = new ReportConfiguration();
        exported.addExcludedPatterns(List.of("**/generated/**"));
        exported.addIncludedPatterns(List.of("**/generated/keep.txt"));
        exported.addExcludedCollection(StandardCollection.MAVEN);

        Path configurationFile = tempDir.resolve("rat-exclusions.xml");
        try (Writer writer = Files.newBufferedWriter(configurationFile, StandardCharsets.UTF_8)) {
            ExclusionConfigurationIO.write(exported, writer);
        }

        String[] expanded = ExclusionConfigurationArguments.expand(new String[] {
            "--input-exclusion-config", configurationFile.toString(), tempDir.toString()
        });
        ReportConfiguration replayed = OptionCollection.parseCommands(tempDir.toFile(), expanded, options -> {
        });

        assertThat(replayed).isNotNull();
        DocumentName base = DocumentName.builder(tempDir.toFile()).build();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("src/generated/drop.txt")))
                .isFalse();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("src/generated/keep.txt")))
                .isTrue();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("target/build.log")))
                .isFalse();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("src/keep.txt")))
                .isTrue();
    }

    @Test
    void rejectsRuntimePathMatchersRatherThanSilentlyChangingSemantics() throws Exception {
        Path configurationFile = tempDir.resolve("runtime-matcher.xml");
        Files.writeString(
                configurationFile,
                "<ExclusionProcessor><excludedPath name=\"runtime predicate\"/></ExclusionProcessor>",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ExclusionConfigurationArguments.expand(new String[] {
                    "--input-exclusion-config", configurationFile.toString(), tempDir.toString()
                }))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("cannot be replayed");
    }
}
