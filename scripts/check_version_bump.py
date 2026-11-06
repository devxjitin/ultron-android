"""Fail a pull request that changes the app without giving it a new version.

The first "## x.y.z" heading in android/app/src/main/assets/changelog.md is the app's version (the build, the update
notes and the About page all read it). Merging to main publishes an update, so every change that reaches the app must
add a newer version on top of that file, written in the pull request itself.
"""
import re
import subprocess
import sys

CHANGELOG = "android/app/src/main/assets/changelog.md"
HEADING = re.compile(r"^##\s+v?(\d+)\.(\d+)\.(\d+)", re.MULTILINE)


def top_version(text):
    """The newest version in a changelog as a tuple, or None when it has no version heading."""
    match = HEADING.search(text)
    return tuple(int(part) for part in match.groups()) if match else None


def check(base_text, head_text):
    """Returns an error message, or None when the head has a newer version than the base."""
    head = top_version(head_text)
    base = top_version(base_text)
    if head is None:
        return f"{CHANGELOG} has no '## x.y.z' version heading."
    if base is not None and head <= base:
        return (
            f"This change reaches the app but the version is still {'.'.join(map(str, head))}. "
            f"Add a new '## x.y.z' entry above it in {CHANGELOG} that is newer than {'.'.join(map(str, base))}."
        )
    return None


def main(base_sha):
    base_text = subprocess.run(["git", "show", f"{base_sha}:{CHANGELOG}"], capture_output=True, text=True).stdout
    with open(CHANGELOG, encoding="utf-8") as handle:
        error = check(base_text, handle.read())
    if error:
        sys.exit(error)
    print("Version bumped.")


if __name__ == "__main__":
    main(sys.argv[1])
