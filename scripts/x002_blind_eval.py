#!/usr/bin/env python3
"""Prepare and score a deterministic blind A/B Arabic evaluation for X002.

This tool never calls a provider and never invents outputs or human scores.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import statistics
from pathlib import Path
from typing import Any

RUBRIC_VERSION = "x002-arabic-blind-v1"
MIN_CASES = 48


def load_json(path: str) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"{path}: root must be an object")
    return value


def write_json(path: str, value: dict[str, Any]) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=False)
        handle.write("\n")


def require_unique(items: list[dict[str, Any]], field: str, label: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for item in items:
        key = item.get(field)
        if not isinstance(key, str) or not key.strip():
            raise ValueError(f"{label}: missing non-empty {field}")
        if key in result:
            raise ValueError(f"{label}: duplicate {field}={key}")
        result[key] = item
    return result


def load_corpus(path: str) -> tuple[dict[str, dict[str, Any]], list[str]]:
    root = load_json(path)
    cases = root.get("cases")
    if root.get("schemaVersion") != 2 or not isinstance(cases, list):
        raise ValueError("quality corpus must be schemaVersion 2")
    indexed = require_unique(cases, "id", "corpus")
    if len(indexed) != MIN_CASES:
        raise ValueError(f"quality corpus must contain exactly {MIN_CASES} cases")
    for case_id, case in indexed.items():
        source = case.get("source")
        if not isinstance(source, str) or not source.strip():
            raise ValueError(f"corpus {case_id}: source is blank")
        forbidden = {"target", "referenceArabic", "score"}.intersection(case)
        if forbidden:
            raise ValueError(f"corpus {case_id}: forbidden answer fields {sorted(forbidden)}")
    return indexed, list(indexed)


def load_contexts(path: str | None, corpus_ids: set[str]) -> dict[str, dict[str, Any]]:
    if path is None:
        return {}
    root = load_json(path)
    cases = root.get("cases")
    if root.get("schemaVersion") != 1 or not isinstance(cases, list):
        raise ValueError("context windows must be schemaVersion 1")
    indexed = require_unique(cases, "id", "contexts")
    extra = set(indexed) - corpus_ids
    if extra:
        raise ValueError(f"contexts reference unknown corpus IDs: {sorted(extra)}")
    for case_id, case in indexed.items():
        for side in ("before", "after"):
            values = case.get(side, [])
            if not isinstance(values, list) or len(values) > 1:
                raise ValueError(f"context {case_id}: {side} must contain at most one SOURCE unit")
            if any(not isinstance(value, str) or not value.strip() for value in values):
                raise ValueError(f"context {case_id}: {side} contains blank source text")
        forbidden = {"target", "referenceArabic", "score", "legacyText", "semanticText"}.intersection(case)
        if forbidden:
            raise ValueError(f"context {case_id}: forbidden output fields {sorted(forbidden)}")
    return indexed


def validate_paired(
    root: dict[str, Any],
    corpus_order: list[str],
    contextual_ids: set[str],
) -> dict[str, dict[str, Any]]:
    if root.get("schemaVersion") != 1:
        raise ValueError("paired outputs must be schemaVersion 1")
    source_sha = root.get("sourceSha")
    if not isinstance(source_sha, str) or len(source_sha) != 40 or any(ch not in "0123456789abcdef" for ch in source_sha.lower()):
        raise ValueError("paired outputs require an exact 40-hex sourceSha")
    provider_evidence = root.get("providerEvidence")
    if not isinstance(provider_evidence, str) or not provider_evidence.strip():
        raise ValueError("paired outputs require a non-secret providerEvidence reference")
    cases = root.get("cases")
    if not isinstance(cases, list):
        raise ValueError("paired outputs cases must be an array")
    indexed = require_unique(cases, "id", "paired outputs")
    if set(indexed) != set(corpus_order):
        missing = sorted(set(corpus_order) - set(indexed))
        extra = sorted(set(indexed) - set(corpus_order))
        raise ValueError(f"paired outputs must match corpus exactly; missing={missing} extra={extra}")
    for case_id, case in indexed.items():
        for field in ("legacyText", "semanticText"):
            text = case.get(field)
            if not isinstance(text, str) or not text.strip():
                raise ValueError(f"paired output {case_id}: {field} is blank")
        expected_mode = "BOUNDED_SOURCE_CONTEXT" if case_id in contextual_ids else "UNIT_ONLY"
        if case.get("semanticMode") != expected_mode:
            raise ValueError(f"paired output {case_id}: semanticMode must be {expected_mode}")
        forbidden = {"score", "humanScore", "referenceArabic"}.intersection(case)
        if forbidden:
            raise ValueError(f"paired output {case_id}: human/reference fields are not allowed")
    return indexed



def template(args: argparse.Namespace) -> None:
    corpus, order = load_corpus(args.corpus)
    contexts = load_contexts(args.contexts, set(order))
    source_sha = args.source_sha.lower()
    if len(source_sha) != 40 or any(ch not in "0123456789abcdef" for ch in source_sha):
        raise ValueError("--source-sha must be an exact 40-hex commit SHA")
    value = {
        "schemaVersion": 1,
        "sourceSha": source_sha,
        "providerEvidence": None,
        "cases": [
            {
                "id": case_id,
                "legacyText": None,
                "semanticText": None,
                "semanticMode": "BOUNDED_SOURCE_CONTEXT" if case_id in contexts else "UNIT_ONLY",
            }
            for case_id in order
        ],
    }
    write_json(args.out, value)


def orientation(seed: bytes, case_id: str) -> bool:
    digest = hmac.new(seed, case_id.encode("utf-8"), hashlib.sha256).digest()
    return bool(digest[0] & 1)


def blank_score() -> dict[str, Any]:
    return {
        "fidelity": None,
        "naturalness": None,
        "criticalError": None,
        "errorTypes": [],
        "notes": "",
    }


def prepare(args: argparse.Namespace) -> None:
    corpus, order = load_corpus(args.corpus)
    contexts = load_contexts(args.contexts, set(order))
    paired_root = load_json(args.paired)
    paired = validate_paired(paired_root, order, set(contexts))
    try:
        seed = bytes.fromhex(args.seed_hex)
    except ValueError as exc:
        raise ValueError("--seed-hex must be valid hexadecimal") from exc
    if len(seed) < 16:
        raise ValueError("--seed-hex must contain at least 16 bytes")

    blind_cases: list[dict[str, Any]] = []
    key_cases: list[dict[str, str]] = []
    for case_id in order:
        pair = paired[case_id]
        semantic_is_a = orientation(seed, case_id)
        output_a = pair["semanticText"] if semantic_is_a else pair["legacyText"]
        output_b = pair["legacyText"] if semantic_is_a else pair["semanticText"]
        context = contexts.get(case_id, {})
        blind_cases.append({
            "caseId": case_id,
            "source": corpus[case_id]["source"],
            "sourceContext": {
                "before": context.get("before", []),
                "after": context.get("after", []),
            },
            "outputA": output_a,
            "outputB": output_b,
            "scores": {"A": blank_score(), "B": blank_score()},
            "preference": None,
        })
        key_cases.append({
            "caseId": case_id,
            "A": "semantic" if semantic_is_a else "legacy",
            "B": "legacy" if semantic_is_a else "semantic",
        })

    seed_fingerprint = hashlib.sha256(seed).hexdigest()
    blind = {
        "schemaVersion": 1,
        "rubricVersion": RUBRIC_VERSION,
        "sourceSha": paired_root["sourceSha"],
        "caseCount": len(blind_cases),
        "pathLabelsHidden": True,
        "cases": blind_cases,
    }
    key = {
        "schemaVersion": 1,
        "rubricVersion": RUBRIC_VERSION,
        "sourceSha": paired_root["sourceSha"],
        "seedFingerprint": seed_fingerprint,
        "cases": key_cases,
    }
    write_json(args.blind_out, blind)
    write_json(args.key_out, key)


def checked_score(case_id: str, label: str, value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{case_id} {label}: score object is missing")
    fidelity = value.get("fidelity")
    naturalness = value.get("naturalness")
    critical = value.get("criticalError")
    if not isinstance(fidelity, int) or isinstance(fidelity, bool) or not 1 <= fidelity <= 5:
        raise ValueError(f"{case_id} {label}: fidelity must be integer 1..5")
    if not isinstance(naturalness, int) or isinstance(naturalness, bool) or not 1 <= naturalness <= 5:
        raise ValueError(f"{case_id} {label}: naturalness must be integer 1..5")
    if not isinstance(critical, bool):
        raise ValueError(f"{case_id} {label}: criticalError must be true/false")
    error_types = value.get("errorTypes", [])
    if not isinstance(error_types, list) or any(not isinstance(item, str) for item in error_types):
        raise ValueError(f"{case_id} {label}: errorTypes must be an array of strings")
    return {"fidelity": fidelity, "naturalness": naturalness, "criticalError": critical, "errorTypes": error_types}


def path_summary(rows: list[dict[str, Any]]) -> dict[str, Any]:
    passing = [row for row in rows if row["fidelity"] >= 4 and row["naturalness"] >= 4]
    critical = [row for row in rows if row["criticalError"]]
    pass_rate = len(passing) / len(rows) if rows else 0.0
    return {
        "reviewed": len(rows),
        "fidelityMean": round(statistics.fmean(row["fidelity"] for row in rows), 4),
        "naturalnessMean": round(statistics.fmean(row["naturalness"] for row in rows), 4),
        "ratedAtLeast4OnBoth": len(passing),
        "ratedAtLeast4OnBothRate": round(pass_rate, 6),
        "criticalErrors": len(critical),
        "n25ThresholdMet": pass_rate >= 0.90 and len(critical) == 0,
    }


def score(args: argparse.Namespace) -> None:
    blind = load_json(args.scored)
    key = load_json(args.key)
    if blind.get("rubricVersion") != RUBRIC_VERSION or key.get("rubricVersion") != RUBRIC_VERSION:
        raise ValueError("rubric version mismatch")
    if blind.get("sourceSha") != key.get("sourceSha"):
        raise ValueError("sourceSha mismatch between scored package and key")
    blind_cases = blind.get("cases")
    key_cases = key.get("cases")
    if not isinstance(blind_cases, list) or not isinstance(key_cases, list):
        raise ValueError("cases arrays are required")
    blind_by_id = require_unique(blind_cases, "caseId", "scored package")
    key_by_id = require_unique(key_cases, "caseId", "blind key")
    if set(blind_by_id) != set(key_by_id) or len(blind_by_id) != MIN_CASES:
        raise ValueError("scored package/key must contain the same complete 48-case set")

    rows: dict[str, list[dict[str, Any]]] = {"legacy": [], "semantic": []}
    preference = {"legacy": 0, "semantic": 0, "tie": 0}
    case_results: list[dict[str, Any]] = []
    for case_id, case in blind_by_id.items():
        mapping = key_by_id[case_id]
        if {mapping.get("A"), mapping.get("B")} != {"legacy", "semantic"}:
            raise ValueError(f"{case_id}: invalid key mapping")
        scores = case.get("scores")
        if not isinstance(scores, dict):
            raise ValueError(f"{case_id}: scores missing")
        a = checked_score(case_id, "A", scores.get("A"))
        b = checked_score(case_id, "B", scores.get("B"))
        rows[mapping["A"]].append(a)
        rows[mapping["B"]].append(b)
        pref = case.get("preference")
        if pref not in ("A", "B", "TIE"):
            raise ValueError(f"{case_id}: preference must be A, B, or TIE")
        if pref == "TIE":
            preference["tie"] += 1
        else:
            preference[mapping[pref]] += 1
        case_results.append({
            "caseId": case_id,
            "legacy": a if mapping["A"] == "legacy" else b,
            "semantic": a if mapping["A"] == "semantic" else b,
            "preference": "tie" if pref == "TIE" else mapping[pref],
        })

    legacy = path_summary(rows["legacy"])
    semantic = path_summary(rows["semantic"])
    if not semantic["n25ThresholdMet"]:
        status = "SEMANTIC_THRESHOLD_NOT_MET"
    elif semantic["n25ThresholdMet"] and not legacy["n25ThresholdMet"]:
        status = "SEMANTIC_THRESHOLD_MET_LEGACY_NOT_MET__INTEGRATOR_REVIEW_REQUIRED"
    else:
        status = "BOTH_THRESHOLD_RESULTS_AVAILABLE__COMPARATIVE_INTEGRATOR_JUDGMENT_REQUIRED"
    report = {
        "schemaVersion": 1,
        "rubricVersion": RUBRIC_VERSION,
        "sourceSha": blind["sourceSha"],
        "legacy": legacy,
        "semantic": semantic,
        "blindPreference": preference,
        "replacementEvidenceStatus": status,
        "caseResults": case_results,
    }
    write_json(args.out, report)


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    sub = root.add_subparsers(dest="command", required=True)
    template_cmd = sub.add_parser("template", help="create a complete 48-case paired-output collection skeleton")
    template_cmd.add_argument("--corpus", required=True)
    template_cmd.add_argument("--contexts")
    template_cmd.add_argument("--source-sha", required=True)
    template_cmd.add_argument("--out", required=True)
    template_cmd.set_defaults(func=template)
    prepare_cmd = sub.add_parser("prepare", help="create blind A/B package and separate mapping key")
    prepare_cmd.add_argument("--corpus", required=True)
    prepare_cmd.add_argument("--contexts")
    prepare_cmd.add_argument("--paired", required=True)
    prepare_cmd.add_argument("--seed-hex", required=True)
    prepare_cmd.add_argument("--blind-out", required=True)
    prepare_cmd.add_argument("--key-out", required=True)
    prepare_cmd.set_defaults(func=prepare)
    score_cmd = sub.add_parser("score", help="unblind completed human scoring and compute deterministic metrics")
    score_cmd.add_argument("--scored", required=True)
    score_cmd.add_argument("--key", required=True)
    score_cmd.add_argument("--out", required=True)
    score_cmd.set_defaults(func=score)
    return root


def main() -> None:
    args = parser().parse_args()
    try:
        args.func(args)
    except ValueError as exc:
        raise SystemExit(f"X002 evaluation error: {exc}") from exc


if __name__ == "__main__":
    main()
