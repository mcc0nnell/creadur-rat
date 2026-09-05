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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.rat.ExclusionConfigurationIO;
import org.apache.rat.ReportConfiguration;

/**
 * Exports the effective RAT exclusion configuration resolved by the Maven
 * frontend so it can be replayed by another RAT frontend.
 *
 * <p>The output uses RAT's native exclusion serialization rather than flattening
 * the configuration to a list of paths. This preserves explicit patterns,
 * standard collections, include overrides, and SCM ignore processors across the
 * frontend boundary.</p>
 */
@Mojo(name = "effective-exclusions", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class RatEffectiveExclusionsMojo extends AbstractRatMojo {

    /**
     * File that receives the effective exclusion configuration.
     */
    @Parameter(
            property = "rat.effectiveExclusionsFile",
            defaultValue = "${project.build.directory}/rat-exclusions.xml",
            required = true)
    private File effectiveExclusionsFile;

    /**
     * Invoked by Maven to export the resolved exclusion configuration.
     *
     * @throws MojoExecutionException if the configuration cannot be written
     * @throws MojoFailureException if Maven reports a configuration failure
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("RAT will not export exclusions since it is configured to be skipped via system property 'rat.skip'.");
            return;
        }

        final ReportConfiguration configuration = getConfiguration();
        final Path output = effectiveExclusionsFile.toPath();
        final Path parent = output.getParent();

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                ExclusionConfigurationIO.write(configuration, writer);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write effective RAT exclusions to " + output, e);
        }

        getLog().info("Wrote effective RAT exclusions to " + output);
    }
}
