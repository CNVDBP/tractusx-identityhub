/*
 *   Copyright (c) 2026 Contributors to the Eclipse Foundation
 *   Copyright (c) 2026 VDBP
 *
 *   See the NOTICE file(s) distributed with this work for additional
 *   information regarding copyright ownership.
 *
 *   This program and the accompanying materials are made available under the
 *   terms of the Apache License, Version 2.0 which is available at
 *   https://www.apache.org/licenses/LICENSE-2.0.
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 *
 *   SPDX-License-Identifier: Apache-2.0
 *
 */

package org.eclipse.tractusx.identityhub.did;

import org.eclipse.edc.identityhub.spi.did.DidDocumentPublisherRegistry;
import org.eclipse.edc.identityhub.spi.did.events.DidDocumentObservable;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.identityhub.spi.did.DidConstants.DID_WEB_METHOD;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class CxDidDocumentExtensionTest {

    private final DidDocumentPublisherRegistry registry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(DidDocumentPublisherRegistry.class, registry);
        context.registerService(DidResourceStore.class, mock());
        context.registerService(DidDocumentObservable.class, mock());
    }

    @Test
    void initialize_registersPublisher(CxDidDocumentExtension extension, ServiceExtensionContext context) {
        assertThat(ServiceLoader.load(ServiceExtension.class).stream().map(ServiceLoader.Provider::type))
                .contains(CxDidDocumentExtension.class);
        extension.initialize(context);

        verify(registry).addPublisher(eq(DID_WEB_METHOD), isA(CxDidDocumentPublisher.class));
    }
}
