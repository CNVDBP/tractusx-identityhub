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

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.document.VerificationMethod;
import org.eclipse.edc.identityhub.spi.did.events.DidDocumentObservable;
import org.eclipse.edc.identityhub.spi.did.model.DidResource;
import org.eclipse.edc.identityhub.spi.did.model.DidState;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CxDidDocumentPublisherTest {

    private static final String DID = "did:web:wallet.example:holder";
    private final DidResourceStore store = mock();
    private final DidDocumentObservable observable = mock();
    private final CxDidDocumentPublisher publisher = new CxDidDocumentPublisher(observable, store, mock(Monitor.class));

    @ParameterizedTest
    @ValueSource(strings = {"wallet.example:holder#credential-service", DID + "#credential-service", DID + "#custom-service"})
    void publish_preservesIdentityAndAddsCxContexts(String serviceId) throws Exception {
        var mapper = new ObjectMapper();
        var key = VerificationMethod.Builder.newInstance()
                .id(DID + "#key-1").controller(DID).type("JsonWebKey2020")
                .publicKeyJwk(Map.of("kty", "EC", "crv", "P-256", "x", "test-x", "y", "test-y")).build();
        var document = DidDocument.Builder.newInstance().id(DID)
                .context(List.of(DidDocument.DID_CONTEXT, "https://example.org/custom-context"))
                .service(List.of(new Service(serviceId, "CredentialService", "https://wallet.example/credentials"),
                        new Service(DID + "#data-service", "DataService", "https://connector.example")))
                .verificationMethod(List.of(key)).authentication(List.of(key.getId())).capabilityInvocation(List.of(key.getId()))
                .build();
        var resource = DidResource.Builder.newInstance().did(DID).document(document).participantContextId("holder").build();
        var stored = new AtomicReference<>(resource);
        // SQL reloads a separate object; mutating the first read must not be enough to publish the fix.
        when(store.findById(DID)).thenAnswer(invocation -> mapper.readValue(mapper.writeValueAsString(stored.get()), DidResource.class));
        when(store.update(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return StoreResult.success();
        });

        assertThat(publisher.publish(DID)).isSucceeded();
        assertThat(publisher.publish(DID)).isSucceeded();

        resource = stored.get();
        document = resource.getDocument();
        assertThat(document.getContext()).containsExactly(DidDocument.DID_CONTEXT, "https://example.org/custom-context",
                "https://w3id.org/security/suites/jws-2020/v1", "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld",
                "https://w3id.org/dspace/2025/1/context.jsonld");
        assertThat(resource.getDid()).isEqualTo(DID);
        assertThat(resource.getParticipantContextId()).isEqualTo("holder");
        assertThat(resource.getState()).isEqualTo(DidState.PUBLISHED.code());
        assertThat(document.getId()).isEqualTo(DID);
        assertThat(document.getVerificationMethod()).usingRecursiveComparison().isEqualTo(List.of(key));
        assertThat(document.getAuthentication()).containsExactly(key.getId());
        assertThat(document.getCapabilityInvocation()).containsExactly(key.getId());
        assertThat(document.getService().get(0).getId()).isEqualTo(serviceId.startsWith("did:") ? serviceId : DID + "#credential-service");
        assertThat(document.getService().get(0).getServiceEndpoint()).isEqualTo("https://wallet.example/credentials");
        assertThat(document.getService().get(1).getId()).isEqualTo(DID + "#data-service");
        // CX-0049 v2.3.0, section 2.1, catenax-eV/catenax-ev.github.io@1cdbdd192d4f0e366459b12fe1d934d8da622a22.
        try (var schemaFile = getClass().getResourceAsStream("/cx-0049-did-document-schema.json")) {
            var schema = SchemaRegistry.withDialect(Dialects.getDraft202012()).getSchema(schemaFile);
            assertThat(schema.validate(mapper.valueToTree(document), context -> context.executionConfig(config -> config.formatAssertionsEnabled(true))))
                    .isEmpty();
        }
        verify(observable, times(2)).invokeForEach(any());
    }

    @Test
    void publish_doesNotPublishWhenDocumentUpdateFails() {
        var resource = DidResource.Builder.newInstance().did(DID).participantContextId("holder")
                .document(DidDocument.Builder.newInstance().id(DID).build()).build();
        when(store.findById(DID)).thenReturn(resource);
        when(store.update(any())).thenReturn(StoreResult.generalError("update failed"));

        assertThat(publisher.publish(DID)).isFailed().detail().contains("update failed");

        assertThat(resource.getState()).isNotEqualTo(DidState.PUBLISHED.code());
        verify(store).findById(DID);
        verifyNoInteractions(observable);
    }
}
