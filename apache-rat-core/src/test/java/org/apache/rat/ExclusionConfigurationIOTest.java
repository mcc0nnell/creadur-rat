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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.rat.config.exclusion.StandardCollection;
import org.apache.rat.document.DocumentName;
import org.apache.rat.document.DocumentNameMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusionConfigurationIOTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsPortableExclusionSemantics() throws Exception {
        ReportConfiguration original = new ReportConfiguration();
        original.addExcludedPatterns(List.of("**/generated/**"));
        original.addIncludedPatterns(List.of("**/generated/keep.txt"));
        original.addExcludedCollection(StandardCollection.MAVEN);

        StringWriter serialized = new StringWriter();
        ExclusionConfigurationIO.write(original, serialized);

        ReportConfiguration replayed = new ReportConfiguration();
        ExclusionConfigurationIO.read(
                replayed,
                new ByteArrayInputStream(serialized.toString().getBytes(StandardCharsets.UTF_8)));

        DocumentName base = DocumentName.builder(tempDir.toFile()).build();
        DocumentNameMatcher originalMatcher = original.getDocumentExcluder(base);
        DocumentNameMatcher replayedMatcher = replayed.getDocumentExcluder(base);

        assertSameDecision(originalMatcher, replayedMatcher, base.resolve("src/generated/drop.txt"));
        assertSameDecision(originalMatcher, replayedMatcher, base.resolve("src/generated/keep.txt"));
        assertSameDecision(originalMatcher, replayedMatcher, base.resolve("target/build.log"));
        assertSameDecision(originalMatcher, replayedMatcher, base.resolve("src/keep.txt"));

        assertThat(replayedMatcher.matches(base.resolve("src/generated/drop.txt"))).isFalse();
        assertThat(replayedMatcher.matches(base.resolve("src/generated/keep.txt"))).isTrue();
        assertThat(replayedMatcher.matches(base.resolve("target/build.log"))).isFalse();
        assertThat(replayedMatcher.matches(base.resolve("src/keep.txt"))).isTrue();
    }

    private static void assertSameDecision(
            final DocumentNameMatcher original,
            final DocumentNameMatcher replayed,
            final DocumentName document) {
        assertThat(replayed.matches(document)).isEqualTo(original.matches(document));
    }
}
