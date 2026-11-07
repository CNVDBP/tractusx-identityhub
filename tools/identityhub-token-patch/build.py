# Copyright (c) 2026 Contributors to the Eclipse Foundation
# Copyright (c) 2026 VDBP
#
# See the NOTICE file(s) distributed with this work for additional information
# regarding copyright ownership.
# This program and the accompanying materials are made available under the
# terms of the Apache License, Version 2.0 which is available at
# https://www.apache.org/licenses/LICENSE-2.0.
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# SPDX-License-Identifier: Apache-2.0

"""Build the reviewed patch from a dedicated, pinned IdentityHub checkout (Python 3.11+)."""
import argparse
from pathlib import Path
import subprocess
import sys
import tomllib

BASE = "f3bbaf90468472e646f0929b72d42d33f7641fc6"
MODULES = [":core:lib:common-lib", ":core:common-core", ":core:identity-hub-core",
           ":protocols:dcp:dcp-issuer:dcp-issuer-core",
           ":protocols:dcp:dcp-identityhub:dcp-identityhub-core",
           ":protocols:dcp:dcp-identityhub:presentation-api"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="dedicated Eclipse IdentityHub RC5 checkout")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    source = args.source.resolve()
    directory = Path(__file__).resolve().parent
    root = directory.parent.parent
    patch = directory / "si-token.patch"

    def git(*arguments):
        return subprocess.check_output(["git", "-C", str(source), *arguments])

    if git("rev-parse", "HEAD").decode().strip() != BASE:
        sys.exit("The source checkout must be at pinned IdentityHub RC5 " + BASE)
    if not git("status", "--porcelain"):
        git("apply", "--check", str(patch))
        git("apply", str(patch))
        files = [line.split(" b/", 1)[1] for line in patch.read_text(encoding="utf-8").splitlines() if line.startswith("diff --git ")]
        git("add", "--intent-to-add", "--", *files)
    if git("ls-files", "--others", "--exclude-standard") or git("diff", "--binary", "HEAD").replace(b"\r\n", b"\n") != patch.read_bytes().replace(b"\r\n", b"\n"):
        sys.exit("Source differs from the reviewed patch; use a dedicated clean checkout. No reset was performed.")

    catalog = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))
    version = catalog["versions"]["identityhub-token-patch"]
    wrapper = [str(source / "gradlew.bat")] if sys.platform == "win32" else ["sh", str(source / "gradlew")]
    tasks = [module + ":publishAllPublicationsToCxTokenPatchRepository" for module in MODULES]
    command = wrapper + tasks + ["--no-daemon", "--console=plain", "--max-workers=2",
        "--init-script", str(directory / "publish.gradle.kts"), "-Dcx.patch.version=" + version,
        "-Dcx.patch.repository=" + str(root / ".gradle/identityhub-token-patch-repository")]
    if args.offline:
        command.append("--offline")
    subprocess.run(command, cwd=source, check=True)


if __name__ == "__main__":
    main()
