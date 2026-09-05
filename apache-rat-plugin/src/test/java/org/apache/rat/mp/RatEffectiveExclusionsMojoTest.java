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
package org.apache.rat.mp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.rat.ExclusionConfigurationIO;
import org.apache.rat.ReportConfiguration;
import org.apache.rat.commandline.Arg;
import org.apache.rat.document.DocumentName;
import org.apache.rat.test.utils.Resources;
import org.junit.jupiter.api.Test;

class RatEffectiveExclusionsMojoTest extends BetterAbstractMojoTestCase {

    @Test
    void exportsInheritedMavenExclusions() throws Exception {
        setUp();
        Arg.reset();
        File pom = Resources.getResourceFile("unit/RAT-554/child/pom.xml");
        RatEffectiveExclusionsMojo mojo =
                (RatEffectiveExclusionsMojo) lookupConfiguredMojo(pom, "effective-exclusions");

        assertThat(mojo).isNotNull();
        mojo.execute();

        Path output = pom.getParentFile().toPath().resolve("target/rat-exclusions.xml");
        assertThat(output).exists();
        assertThat(Files.readString(output)).contains("**/from-parent/**");

        ReportConfiguration replayed = new ReportConfiguration();
        try (InputStream input = Files.newInputStream(output)) {
            ExclusionConfigurationIO.read(replayed, input);
        }

        DocumentName base = DocumentName.builder(pom.getParentFile()).build();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("src/from-parent/evidence.txt")))
                .isFalse();
        assertThat(replayed.getDocumentExcluder(base).matches(base.resolve("src/kept.txt")))
                .isTrue();
    }
}
