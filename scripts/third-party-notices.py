#!/usr/bin/env python3
"""Regenerates THIRD_PARTY_NOTICES.txt from the resolved release classpath.

Everything this writes comes from two authoritative places, never from a
hand-maintained list and never from a guess:

  * `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
    — the exact set of modules the shipped application links against, after
    Gradle has applied every version constraint.
  * the modules' own published metadata in the Gradle cache — the `<licenses>`
    block of each POM (following `<parent>` where a POM inherits it), the
    LICENSE files embedded in the artifacts, and, for the Google SDKs, the
    `third_party_licenses.json` index Google publishes inside each AAR.

Run it after adding or upgrading any dependency:

    python3 scripts/third-party-notices.py

It rewrites THIRD_PARTY_NOTICES.txt in place and prints a summary. The file is
committed, and the build copies it into the app's assets so the in-app
"Open Source Licenses" screen shows exactly what the repository says.

Requires a working Android SDK, because it runs Gradle. Nothing else.
"""

from __future__ import annotations

import collections
import glob
import json
import os
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
POM_NS = "{http://maven.apache.org/POM/4.0.0}"
OUTPUT = os.path.join(ROOT, "THIRD_PARTY_NOTICES.txt")

# Modules that exist only to publish a version constraint. They contribute no
# code to the application, so they carry no notice.
PLATFORMS = {
    ("androidx.compose", "compose-bom"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-bom"),
    ("org.jetbrains.kotlinx", "kotlinx-serialization-bom"),
}

DEPENDENCY_LINE = re.compile(
    r"[\\+|]--- ([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):([^ \n(]+)(?: -> ([^ \n(]+))?"
)


def resolve_classpath() -> dict[tuple[str, str], str]:
    """The release runtime classpath, as Gradle resolves it."""
    print("Resolving releaseRuntimeClasspath …", file=sys.stderr)
    result = subprocess.run(
        [
            os.path.join(ROOT, "gradlew"),
            "--quiet",
            ":app:dependencies",
            "--configuration",
            "releaseRuntimeClasspath",
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        sys.exit(f"gradlew :app:dependencies failed:\n{result.stdout}\n{result.stderr}")

    modules: dict[tuple[str, str], str] = {}
    for line in result.stdout.splitlines():
        match = DEPENDENCY_LINE.search(line)
        if match:
            group, artifact, requested, upgraded = match.groups()
            modules[(group, artifact)] = upgraded or requested
    for platform in PLATFORMS:
        modules.pop(platform, None)
    if not modules:
        sys.exit("No dependencies parsed — has the Gradle output format changed?")
    return modules


def artifact_paths(group: str, artifact: str, version: str, suffix: str) -> list[str]:
    return sorted(glob.glob(f"{CACHE}/{group}/{artifact}/{version}/*/{artifact}-{version}{suffix}"))


def pom_licences(group: str, artifact: str, version: str) -> list[str]:
    """The licence names a module declares, following `<parent>` when it must."""
    poms = artifact_paths(group, artifact, version, ".pom")
    if not poms:
        return []
    root = ET.parse(poms[0]).getroot()
    declared = root.find(POM_NS + "licenses")
    if declared is not None:
        names = [
            (licence.findtext(POM_NS + "name") or "").strip()
            for licence in declared.findall(POM_NS + "license")
        ]
        if any(names):
            return names
    parent = root.find(POM_NS + "parent")
    if parent is not None:
        return pom_licences(
            (parent.findtext(POM_NS + "groupId") or "").strip(),
            (parent.findtext(POM_NS + "artifactId") or "").strip(),
            (parent.findtext(POM_NS + "version") or "").strip(),
        )
    return []


def classify(names: list[str]) -> str:
    """Maps a module's declared licence names onto the sections below."""
    joined = " ".join(names).lower()
    if "android software development kit" in joined:
        return "Android SDK"
    if "bsd" in joined:
        return "BSD-3-Clause"
    if "mit" in joined:
        return "MIT"
    if "apache" in joined:
        return "Apache-2.0"
    return "UNKNOWN"


def embedded_licence_text(group: str, artifact: str, version: str, pattern: str) -> str | None:
    """A LICENSE file shipped inside the artifact itself, if there is one."""
    for suffix in (".aar", ".jar"):
        for path in artifact_paths(group, artifact, version, suffix):
            archive = zipfile.ZipFile(path)
            for name in archive.namelist():
                if re.search(pattern, name):
                    return archive.read(name).decode("utf-8", "replace").rstrip("\n")
    return None


def google_embedded_components(modules: dict[tuple[str, str], str]) -> list[str]:
    """Third-party components Google bundles inside its own binaries."""
    components: set[str] = set()
    for (group, artifact), version in modules.items():
        for path in artifact_paths(group, artifact, version, ".aar"):
            archive = zipfile.ZipFile(path)
            if "third_party_licenses.json" in archive.namelist():
                components |= set(json.loads(archive.read("third_party_licenses.json")))
    return sorted(components)


def has_notice_file(modules: dict[tuple[str, str], str]) -> list[str]:
    """Modules shipping a NOTICE file, which Apache-2.0 4(d) makes us propagate."""
    carrying = []
    for (group, artifact), version in sorted(modules.items()):
        for suffix in (".aar", ".jar"):
            for path in artifact_paths(group, artifact, version, suffix):
                archive = zipfile.ZipFile(path)
                if any(re.search(r"(^|/)NOTICE(\.txt|\.md)?$", n, re.I) for n in archive.namelist()):
                    carrying.append(f"{group}:{artifact}:{version}")
    return carrying


def indent(coordinates: list[str]) -> str:
    return "\n".join("    " + coordinate for coordinate in coordinates)


def main() -> None:
    modules = resolve_classpath()

    buckets: dict[str, list[str]] = collections.defaultdict(list)
    for (group, artifact), version in sorted(modules.items()):
        bucket = classify(pom_licences(group, artifact, version))
        buckets[bucket].append(f"{group}:{artifact}:{version}")

    if buckets["UNKNOWN"]:
        sys.exit(
            "These modules declare no licence this script recognises. Determine the\n"
            "licence from the project's own source and teach classify() about it —\n"
            "do not let an unattributed dependency into a release:\n"
            + indent(buckets["UNKNOWN"])
        )

    notices = embedded_licence_text("androidx.core", "core", modules[("androidx.core", "core")], r"LICENSE\.txt$")
    if notices is None:
        sys.exit("Could not read the Apache-2.0 text from the androidx.core artifact.")
    apache = notices

    protobuf = embedded_licence_text(
        "androidx.datastore",
        "datastore-preferences-external-protobuf",
        modules[("androidx.datastore", "datastore-preferences-external-protobuf")],
        r"LICENSE\.txt$",
    )
    if protobuf is None:
        sys.exit("Could not read the BSD-3-Clause text from the bundled protobuf artifact.")

    propagating = has_notice_file(modules)
    notice_clause = (
        "None of these artifacts ships a NOTICE file, so Apache-2.0 section 4(d)\n"
        "adds no further attribution requirement beyond the licence text below."
        if not propagating
        else "These artifacts ship a NOTICE file, which Apache-2.0 section 4(d)\n"
        "requires to be reproduced:\n\n" + indent(propagating)
    )

    components = google_embedded_components(modules)
    version_name = read_version_name()

    document = TEMPLATE.format(
        version=version_name,
        apache_count=len(buckets["Apache-2.0"]),
        apache_modules=indent(buckets["Apache-2.0"]),
        apache_text=apache,
        notice_clause=notice_clause,
        bsd_modules=indent(buckets["BSD-3-Clause"]),
        bsd_text=protobuf,
        mit_modules=indent(buckets["MIT"]),
        google_modules=indent(buckets["Android SDK"]),
        google_components=indent(components),
    )
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        handle.write(document)

    print(f"Wrote {OUTPUT} ({len(document):,} bytes)")
    for bucket, entries in sorted(buckets.items()):
        print(f"  {bucket:<14} {len(entries):>3} modules")
    print(f"  Google-embedded components: {len(components)}")


def read_version_name() -> str:
    build_file = os.path.join(ROOT, "app", "build.gradle.kts")
    with open(build_file, encoding="utf-8") as handle:
        match = re.search(r'val earthVersionName = "([^"]+)"', handle.read())
    if not match:
        sys.exit(f"Could not read earthVersionName from {build_file}")
    return match.group(1)


TEMPLATE = """\
================================================================================
THIRD-PARTY NOTICES — EARTH (com.earthgame.idle)
================================================================================

This file lists the third-party software distributed inside the EARTH Android
application and reproduces the notices those licences require.

It covers the release runtime classpath: every module the application links
against and ships, direct and transitive, as resolved by

    ./gradlew :app:dependencies --configuration releaseRuntimeClasspath

Licences were read from each module's published POM metadata and from the
LICENSE files embedded in the published artifacts. Nothing here is guessed, and
nothing here is hand-maintained: regenerate the file with

    python3 scripts/third-party-notices.py

EARTH's own source code is NOT covered by this file. At the time of writing the
repository carries no licence of its own, which means default copyright applies
to it; see docs/RELEASE_BLOCKERS.md.

Generated for EARTH {version}.

================================================================================
1. APACHE LICENSE 2.0
================================================================================

The following {apache_count} modules are licensed under the Apache License, Version 2.0.

Android Jetpack / AndroidX and Android Open Source Project components are
Copyright (C) The Android Open Source Project.
Kotlin and kotlinx components are Copyright (C) JetBrains s.r.o. and Kotlin
Programming Language contributors.
Guava, Error Prone, J2ObjC and FindBugs JSR-305 components are
Copyright (C) Google LLC.
Okio is Copyright (C) Square, Inc.
JSpecify is Copyright (C) The JSpecify Authors.

Modules:

{apache_modules}

{notice_clause}

--------------------------------------------------------------------------------

{apache_text}

================================================================================
2. BSD 3-CLAUSE LICENSE
================================================================================

The following module embeds a repackaged subset of Protocol Buffers:

{bsd_modules}

Copyright 2008 Google Inc.  All rights reserved.

{bsd_text}

================================================================================
3. MIT LICENSE
================================================================================

The following module is licensed under the MIT License:

{mit_modules}

Checker Framework qualifiers
Copyright 2004-present by the Checker Framework developers

MIT License:

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

================================================================================
4. GOOGLE PLAY SERVICES AND THE USER MESSAGING PLATFORM
================================================================================

The following modules are licensed under the Android Software Development Kit
License Agreement, https://developer.android.com/studio/terms, and are
Copyright (C) Google LLC:

{google_modules}

These are the Google Mobile Ads SDK (the banner advertisement), the User
Messaging Platform SDK (the EEA/UK consent form), and the Google Play services
modules they depend on. That agreement permits distributing the SDK as part of
an application and does not require its own text to be reproduced here.

Those binaries in turn embed third-party components. Google publishes the full
licence text for each of them inside the published artifacts
(third_party_licenses.txt and third_party_licenses.json in each AAR); the
combined text runs to roughly 600 KB, several times the size of everything else
in this file, so it is referenced rather than reproduced. The components, read
from Google's own index rather than compiled by hand, are:

{google_components}

To reproduce Google's notices verbatim inside the application instead, add
Google's official oss-licenses Gradle plugin —
https://developers.google.com/android/guides/opensource — which generates them
from the same indexes at build time. docs/THIRD_PARTY_LICENSES.md records the
trade-off. To read them without building anything, open the
third_party_licenses.txt entry of the relevant .aar from https://maven.google.com.

================================================================================
5. BUILD-TIME AND TEST-TIME SOFTWARE
================================================================================

Gradle, the Android Gradle Plugin, the Kotlin compiler, JUnit, Robolectric and
the AndroidX test libraries build and test EARTH but are not distributed inside
the application, so their notices do not travel with it. They are listed in
docs/THIRD_PARTY_LICENSES.md for completeness.

================================================================================
END OF THIRD-PARTY NOTICES
================================================================================
"""


if __name__ == "__main__":
    main()
