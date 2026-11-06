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

import org.eclipse.edc.identityhub.publisher.did.local.LocalDidPublisher;
import org.eclipse.edc.identityhub.spi.did.events.DidDocumentObservable;
import org.eclipse.edc.identityhub.spi.did.store.DidResourceStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.util.List;

import static org.eclipse.edc.iam.did.spi.document.DidDocument.DID_CONTEXT;

class CxDidDocumentPublisher extends LocalDidPublisher {

    private static final List<String> CONTEXTS = List.of(
            DID_CONTEXT,
            "https://w3id.org/security/suites/jws-2020/v1",
            "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld",
            "https://w3id.org/dspace/2025/1/context.jsonld");

    private final DidResourceStore store;

    CxDidDocumentPublisher(DidDocumentObservable observable, DidResourceStore store, Monitor monitor) {
        super(observable, store, monitor);
        this.store = store;
    }

    @Override
    public Result<Void> publish(String did) {
        var resource = store.findById(did);
        if (resource == null) {
            return super.publish(did);
        }

        var document = resource.getDocument();
        for (var context : CONTEXTS) {
            if (!document.getContext().contains(context)) {
                document.getContext().add(context);
            }
        }
        // Repair only the ID emitted by the old initial-participant helper.
        var legacyServiceId = document.getId().replace("did:web:", "") + "#credential-service";
        document.getService().stream()
                .filter(service -> "CredentialService".equals(service.getType()) && legacyServiceId.equals(service.getId()))
                .forEach(service -> service.setId(document.getId() + "#credential-service"));

        // Persist before the upstream publisher reloads the document and emits its publication event.
        var result = store.update(resource);
        if (result.failed()) {
            return Result.failure(result.getFailureDetail());
        }
        return super.publish(did);
    }
}
