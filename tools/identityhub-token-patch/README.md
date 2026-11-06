# RC5 SI token patch

This fork carries a source patch against Eclipse IdentityHub commit
`f3bbaf90468472e646f0929b72d42d33f7641fc6` (RC5). It fixes the shared SI token
precheck and the presentation, issuance-request and credential-delivery
verification paths. It checks the resolved DID identity and
`capabilityInvocation`, accepts an omitted `kid` only with exactly one authorized
verification method, and requires a non-blank `jti`.

The existing atomic JTI store is reused. Replay entries are retained through the
validators' existing five-second clock allowance. Local access-token validation,
registered-holder checks and pending-delivery ownership checks still run before
external DID resolution. Other public-key uses, including VC and status-list
signature verification, retain their existing resolver.

## Build

Use Python 3.11+, Git and the normal project JDK. Prepare a dedicated checkout;
the script never resets a checkout containing other changes:

```sh
git clone https://github.com/eclipse-edc/IdentityHub.git ../identityhub-token-patch-source
git -C ../identityhub-token-patch-source checkout --detach f3bbaf90468472e646f0929b72d42d33f7641fc6
python3 tools/identityhub-token-patch/build.py ../identityhub-token-patch-source
./gradlew :runtimes:identityhub:shadowJar :runtimes:issuerservice:shadowJar
```

On Windows, use `python` and `gradlew.bat`. With dependencies already cached,
pass `--offline` to the Python script and Gradle. The script accepts either the
clean pinned source or exactly this already-applied patch; unexpected source
changes stop the build.

Only `common-lib`, `common-core`, `identity-hub-core`, `dcp-issuer-core` and
`dcp-identityhub-core` receive the distinct version pinned in
`gradle/libs.versions.toml`. They are published into the ignored project-local
`.gradle/identityhub-token-patch-repository`. Original RC5 artifacts and the
user's Maven local repository are not overwritten. The five modules are selected
together; do not mix the relaxed precheck with the original key resolver.
Their bytecode and published JVM requirement remain Java 17, matching RC5,
even when the build runs on a newer JDK.

The existing CI setup builds the same pinned source patch. Its affected unit,
signature, Storage API and Checkstyle tests run in the existing verification
jobs. This patch is maintained in this fork and is not an upstream release.

## Deployment compatibility

Runtime, Docker example and chart defaults enable
`edc.iam.accesstoken.jti.validation=true`. Existing external configuration can
override defaults: verify the effective value when upgrading. SQL deployments
must retain the existing JTI table and datasource across replicas and restarts.
Memory runtimes only retain replay state for that process lifetime.

Each SI token and presentation access token must contain its own unique `jti`;
clients must acquire fresh tokens for retries instead of replaying a used token.
An absent `kid` with two verification methods is rejected even when only one
method is authorized. Relative `#key` references are supported. No DID, key,
credential or database schema migration is performed by this patch.

Keep the previous application images and configuration for rollback. Reverting
to the original RC5 verification code also reverts these authentication fixes;
preserve the SQL JTI records when rolling application versions.

## NOTICE

This work is licensed under [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/legalcode).

- SPDX-License-Identifier: CC-BY-4.0
- SPDX-FileCopyrightText: 2026 Contributors to the Eclipse Foundation
- SPDX-FileCopyrightText: 2026 VDBP
- Source URL: <https://github.com/CNVDBP/tractusx-identityhub/tree/fix/wallet-cx0149-dcp-did/tools/identityhub-token-patch>
