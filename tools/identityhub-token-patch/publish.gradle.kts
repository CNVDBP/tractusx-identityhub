/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 * Copyright (c) 2026 VDBP
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.Sign

val patchedModules = setOf("common-lib", "common-core", "identity-hub-core", "dcp-issuer-core", "dcp-identityhub-core")

gradle.beforeProject {
    if (name in patchedModules) {
        version = System.getProperty("cx.patch.version") ?: error("cx.patch.version is required")
        configurations.configureEach {
            // The autodoc plugin otherwise derives the processor version from the patched project version.
            resolutionStrategy.force("org.eclipse.edc:autodoc-processor:1.0.0-RC5")
        }
        // These artifacts stay in the project-local build repository, not a release repository.
        tasks.withType<Sign>().configureEach { enabled = false }
        plugins.withId("maven-publish") {
            extensions.configure<PublishingExtension> {
                repositories.maven {
                    name = "CxTokenPatch"
                    url = uri(System.getProperty("cx.patch.repository") ?: error("cx.patch.repository is required"))
                }
            }
        }
    }
}
