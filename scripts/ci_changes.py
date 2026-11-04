"""Classify complete PR/push diffs without skipping required workflows."""
import json
import os
from pathlib import Path
import subprocess


def classify(paths):
    result = dict(android=False, backend=False, web=False, publish=False)
    for path in paths:
        if path.startswith("android/") or path == "scripts/publish-update.ps1":
            result.update(android=True, publish=True)
        if (path.startswith("mcp/") and not path.endswith(".md")) or path.startswith("infra/") or path == ".python-version":
            result["backend"] = True
        if path.startswith("ultronapp/") or path in ("firebase.json", "firestore.rules"):
            result["web"] = True
        if path in (".github/workflows/checks.yml", "scripts/ci_changes.py") or path.startswith("tests/ci/"):
            result.update(android=True, backend=True, web=True)
        if path == ".github/workflows/publish-ota.yml" or path.startswith(".github/actions/"):
            result.update(android=True, backend=True, web=True, publish=True)
    return result


def plan(event_name, event, diff):
    if event_name == "pull_request":
        base, head, triple = event["pull_request"]["base"]["sha"], event["pull_request"]["head"]["sha"], True
    elif event_name == "push" and event.get("before", "").strip("0"):
        base, head, triple = event["before"], event["after"], False
    else:
        return dict.fromkeys(("android", "backend", "web", "publish"), True)
    try:
        return classify(diff(base, head, triple))
    except subprocess.CalledProcessError:
        return dict.fromkeys(("android", "backend", "web", "publish"), True)


def git_diff(base, head, triple):
    raw = subprocess.check_output(["git", "diff", "--no-renames", "--name-only", "-z", base + ("..." if triple else "..") + head])
    return [name.decode("utf-8", errors="surrogateescape") for name in raw.split(b"\0") if name]


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    result = plan(os.environ["GITHUB_EVENT_NAME"], event, git_diff)
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for name, enabled in result.items():
            output.write(f"{name}={str(enabled).lower()}\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
