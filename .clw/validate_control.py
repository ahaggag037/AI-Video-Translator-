#!/usr/bin/env python3
"""CLW Safe Engineering Kernel v0 validator.

Dependency-free by design. It validates static reservations and, on agent branches,
checks changed paths against the granted write set. In CI it emits SHA-bound
control evidence for the exact checkout being tested.
"""

from __future__ import annotations

import fnmatch
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / ".clw" / "reservations.json"
OUT = ROOT / ".clw" / "out"

ACTIVE = {"RESERVED", "RUNNING", "VERIFYING", "REVIEW_REQUIRED", "APPROVED"}


def fail(message: str) -> None:
    print(f"CLW_CONTROL_FAIL: {message}", file=sys.stderr)
    raise SystemExit(2)


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=ROOT, text=True, capture_output=True, check=False
    )
    if result.returncode != 0:
        fail(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout.strip()


def normalize(pattern: str) -> str:
    p = pattern.replace("\\", "/").strip()
    while p.startswith("./"):
        p = p[2:]
    if not p or p.startswith("/") or ".." in Path(p).parts:
        fail(f"unsafe path pattern: {pattern!r}")
    return p


def static_prefix(pattern: str) -> str:
    pattern = normalize(pattern)
    wildcard_at = min(
        [i for i in (pattern.find("*"), pattern.find("?"), pattern.find("[")) if i >= 0]
        or [len(pattern)]
    )
    return pattern[:wildcard_at].rstrip("/")


def overlaps(a: str, b: str) -> bool:
    a = normalize(a)
    b = normalize(b)
    pa, pb = static_prefix(a), static_prefix(b)
    if not pa or not pb:
        return True
    if pa == pb or pa.startswith(pb + "/") or pb.startswith(pa + "/"):
        return True
    # Exact-pattern cross match for non-prefix cases.
    return fnmatch.fnmatch(a, b) or fnmatch.fnmatch(b, a)


def allowed(path: str, patterns: list[str]) -> bool:
    path = normalize(path)
    for raw in patterns:
        pattern = normalize(raw)
        if pattern.endswith("/**"):
            prefix = pattern[:-3].rstrip("/")
            if path == prefix or path.startswith(prefix + "/"):
                return True
        if fnmatch.fnmatch(path, pattern):
            return True
        if path == pattern:
            return True
    return False


def load_state() -> dict:
    try:
        data = json.loads(STATE.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot read {STATE.relative_to(ROOT)}: {exc}")
    if data.get("schema") != "clw-reservations/v0":
        fail("unsupported reservation schema")
    if not isinstance(data.get("reservations"), list):
        fail("reservations must be a list")
    return data


def validate_reservations(data: dict) -> None:
    seen_ids: set[str] = set()
    seen_branches: set[str] = set()
    active: list[dict] = []

    for item in data["reservations"]:
        rid = str(item.get("reservation_id", "")).strip()
        branch = str(item.get("branch", "")).strip()
        status = str(item.get("status", "")).strip()
        risk = str(item.get("risk", "")).strip()
        writes = item.get("write_set", [])
        reads = item.get("read_base_set", [])
        contracts = item.get("contract_set", [])

        if not rid or rid in seen_ids:
            fail(f"missing/duplicate reservation_id: {rid!r}")
        seen_ids.add(rid)
        if not branch.startswith("agent/"):
            fail(f"reservation {rid}: branch must start with agent/")
        if branch in seen_branches:
            fail(f"duplicate reservation branch: {branch}")
        seen_branches.add(branch)
        if risk not in {"R0", "R1", "R2", "R3", "R4"}:
            fail(f"reservation {rid}: invalid risk {risk!r}")
        if not isinstance(writes, list) or not writes:
            fail(f"reservation {rid}: write_set must be non-empty")
        if not isinstance(reads, list) or not isinstance(contracts, list):
            fail(f"reservation {rid}: read_base_set/contract_set must be lists")
        for p in [*writes, *reads]:
            normalize(str(p))
        if status in ACTIVE:
            active.append(item)

    for i, left in enumerate(active):
        for right in active[i + 1 :]:
            for a in left["write_set"]:
                for b in right["write_set"]:
                    if overlaps(str(a), str(b)):
                        fail(
                            "active write-set conflict: "
                            f"{left['reservation_id']}:{a} <-> {right['reservation_id']}:{b}"
                        )


def find_reservation(data: dict, branch: str) -> dict | None:
    for item in data["reservations"]:
        if item.get("branch") == branch and item.get("status") in ACTIVE:
            return item
    return None


def changed_files(base_ref: str | None) -> list[str]:
    if base_ref:
        # GitHub Actions fetch-depth=0 makes this available. Merge-base prevents
        # unrelated integration history from being attributed to the worker.
        merge_base = git("merge-base", f"origin/{base_ref}", "HEAD")
        out = git("diff", "--name-only", f"{merge_base}...HEAD")
    else:
        parent = git("rev-parse", "HEAD^") if git("rev-list", "--count", "HEAD") != "1" else ""
        out = git("diff", "--name-only", parent, "HEAD") if parent else ""
    return [line.strip() for line in out.splitlines() if line.strip()]


def validate_agent_branch(data: dict, branch: str, base_ref: str | None) -> tuple[dict, list[str]]:
    reservation = find_reservation(data, branch)
    if reservation is None:
        fail(f"agent branch has no active static reservation: {branch}")

    files = changed_files(base_ref)
    writes = [str(x) for x in reservation["write_set"]]
    protected = [str(x) for x in data.get("protected_prefixes", [])]
    control_change = bool(reservation.get("control_change", False))

    violations: list[str] = []
    for path in files:
        if not allowed(path, writes):
            violations.append(f"outside write_set: {path}")
        if any(path == p.rstrip("/") or path.startswith(p) for p in protected):
            if not (reservation.get("risk") == "R4" and control_change):
                violations.append(f"protected control path: {path}")
    if violations:
        fail("; ".join(violations))
    return reservation, files


def emit_evidence(data: dict, branch: str, base_ref: str | None, reservation: dict | None, files: list[str]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    evidence = {
        "schema": "clw-control-evidence/v0",
        "policy_version": 0,
        "integration_branch": data.get("integration_branch"),
        "checkout_sha": git("rev-parse", "HEAD"),
        "branch": branch,
        "base_ref": base_ref,
        "reservation_id": reservation.get("reservation_id") if reservation else None,
        "task_id": reservation.get("task_id") if reservation else None,
        "risk": reservation.get("risk") if reservation else None,
        "changed_files": files,
        "control_validation": "PASS",
    }
    (OUT / "control-evidence.json").write_text(
        json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(evidence, indent=2, sort_keys=True))


def main() -> None:
    data = load_state()
    validate_reservations(data)

    branch = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or git("branch", "--show-current")
    base_ref = os.environ.get("GITHUB_BASE_REF") or None
    reservation = None
    files: list[str] = []

    if branch.startswith("agent/"):
        reservation, files = validate_agent_branch(data, branch, base_ref)

    emit_evidence(data, branch, base_ref, reservation, files)
    print("CLW_CONTROL_PASS")


if __name__ == "__main__":
    main()
