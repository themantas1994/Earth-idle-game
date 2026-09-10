#!/usr/bin/env python3
"""Check that every relative Markdown link in the repository resolves.

Deliberately dependency-free: Python 3 and nothing else, so it runs in CI
without an install step and on a contributor's machine without a toolchain.

Checks relative file targets and `#anchor` fragments against the headings of the
file they point at. External http(s) links are skipped — a link checker that
fails because someone else's site is down is a link checker people disable.

    python3 scripts/check-docs-links.py
"""

from __future__ import annotations

import os
import re
import sys

SKIP_DIRS = {".git", "node_modules", "build", ".gradle", ".kotlin", ".idea"}
LINK = re.compile(r'\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)')
HEADING = re.compile(r"^#{1,6}\s+(.*)")


def markdown_files(root: str) -> list[str]:
    found = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        found += [os.path.join(dirpath, f) for f in filenames if f.endswith(".md")]
    return sorted(found)


def heading_slugs(path: str) -> set[str]:
    """GitHub's heading-to-anchor rules, near enough: strip markup, lowercase,
    drop punctuation, spaces to hyphens."""
    slugs = set()
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = HEADING.match(line)
            if not match:
                continue
            text = match.group(1).strip().replace("`", "")
            text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
            slug = re.sub(r"[^\w\s-]", "", text.lower()).strip().replace(" ", "-")
            slugs.add(slug)
    return slugs


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    files = markdown_files(root)
    anchors: dict[str, set[str]] = {}
    problems: list[str] = []

    for path in files:
        rel = os.path.relpath(path, root)
        for target in LINK.findall(open(path, encoding="utf-8").read()):
            if target.startswith(("http://", "https://", "mailto:")):
                continue

            fragment = None
            if "#" in target:
                target, fragment = target.split("#", 1)

            resolved = path if target == "" else os.path.normpath(
                os.path.join(os.path.dirname(path), target)
            )

            if not os.path.exists(resolved):
                problems.append(f"missing file:   {rel} -> {target}")
                continue

            if fragment and resolved.endswith(".md"):
                if resolved not in anchors:
                    anchors[resolved] = heading_slugs(resolved)
                if fragment.lower() not in anchors[resolved]:
                    problems.append(f"missing anchor: {rel} -> {target}#{fragment}")

    print(f"checked {len(files)} markdown files")
    for problem in problems:
        print(f"  {problem}")
    if problems:
        print(f"\n{len(problems)} broken link(s)")
        return 1
    print("all internal links resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
