# Wallet issuance acceptance — 2026-11-08

This records a real DCP 1.0 exchange between the existing SQL Holder and test
Issuer. It is not external CSP interoperability or a CX-0149 conformance result.
The tested Holder revision was `f1d896007aa7025285f07f13396eac3d55c4dbc7`;
the Issuer revision was `76fdd27d16fcf091fb8dddc940e5d29910d7d3d6`.
Both are ancestors of this fork's repair branch. No application was rebuilt or
restarted for this acceptance.

One dedicated Membership credential definition reused the existing test Holder,
Issuer, keys and attestation. Its distinct credential-object ID prevented
supersession of existing credentials. The Holder requested `vc11-sl2021/jwt`
through its management API; the runtimes performed issuance and Storage delivery
over the public HTTPS endpoints discovered from their DID documents. There was
no management-API credential import.

| Check | Observed result |
| --- | --- |
| Holder request and asynchronous delivery | HTTP 201; CREATED → REQUESTED → ISSUED; one stored credential |
| Credential and presentation signatures | Verified with the published DID keys; credential subject matched the Holder |
| Presentation by canonical JWT credential ID | HTTP 200 and exactly the new signed credential |
| Duplicate DCP request with the same holderPid and a fresh SI token | HTTP 409 and the same issuer process; no additional issuance |
| Requested profile | **Failed:** `vc11-sl2021/jwt` produced `BitstringStatusListEntry` and `BitstringStatusListCredential` |
| VC-JWT identifier mapping | **Failed:** JWT `jti` differed from the embedded `vc.id`; the Holder normalized the ID from `jti` |
| Duplicate response Location | **Failed:** HTTP URL without the public issuance prefix; the corresponding HTTPS path returned 404 |
| Issuer suspension and resumption | Both returned 501; not implemented in this Issuer |
| Revocation with the actual Bitstring list | HTTP 204; signed public status bit changed from 0 to 1; presentation stopped returning the credential after natural cache refresh |

The deployed revocation-cache validity was 900,000 ms. The revoked credential
stopped appearing approximately ten minutes after revocation in this run; the
cache was not cleared and the Holder was not restarted. This confirms the existing
cache behavior, not immediate revocation propagation.

The new Holder credential and definition were removed after the checks. The
request, issuance process, revoked Issuer record and allocated status bit remain
as test audit records. Existing identities, keys, six original credentials and
unrelated workloads were preserved. The status-list manager also extended the
old 2,048-byte list to 16,384 bytes: the extension contains only zero padding and
the only changed existing bit is the dedicated test credential's revocation bit.

## Remaining implementation boundaries

The RC5 `JwtCredentialGenerator` independently generates the credential ID and
JWT ID. [VC Data Model 1.1 JWT encoding](https://www.w3.org/TR/vc-data-model-1.1/#json-web-token)
requires `jti` to represent the credential ID. The Holder's use of `jti` on decode
follows that mapping; using the inconsistent embedded ID as a scope returns no
credential.

The RC5 Issuer's status-list manager emits Bitstring Status List entries regardless
of the VC 1.1 profile label. Metadata advertising the profile is insufficient
evidence of a StatusList2021 credential. The observed Bitstring behavior does not
validate the Holder's StatusList2021 revocation or suspension path.

The request controller supplies a root-relative Location without the deployed
public prefix; the observed response also used HTTP. The Holder completed this
exchange by extracting the process ID and using the discovered Issuer endpoint.
Clients that follow the returned Location directly cannot do so successfully.

These observations require follow-up before treating this test Issuer as a
profile-correct counterpart. External Issuer/CSP testing remains necessary.
