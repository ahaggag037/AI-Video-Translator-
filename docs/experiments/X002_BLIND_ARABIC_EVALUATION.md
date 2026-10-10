# X002 — Blind Arabic Evaluation Package

Status: **PACKAGE READY / PROVIDER OUTPUTS PENDING / HUMAN SCORING PENDING**.

This package prepares the evidence required to compare the frozen legacy comparator with the X002 semantic shadow path. It does **not** make semantic translation production truth, does not call any provider, and does not create human scores.

## Canonical inputs

- Source corpus: `app/src/test/resources/translation_v1/quality_corpus.json` (48 synthetic source-only cases).
- Bounded-context fixture: `app/src/test/resources/translation_v1/x002_context_windows.json` (12 source-only cases; at most one neighboring source unit on each side).
- Collection/scoring tool: `scripts/x002_blind_eval.py`.
- Tool regression tests: `scripts/test_x002_blind_eval.py`.

The provider collector must record the exact execution commit SHA and must obtain one legacy output and one semantic output for every corpus case. The collector must not place API keys, authorization headers, private provider payloads, or user transcript data in the repository.

## 1. Create the paired-output collection skeleton

```bash
python3 scripts/x002_blind_eval.py template \
  --corpus app/src/test/resources/translation_v1/quality_corpus.json \
  --contexts app/src/test/resources/translation_v1/x002_context_windows.json \
  --source-sha <EXACT_EXECUTION_SHA> \
  --out /secure/path/x002_paired_outputs.json
```

Fill only `legacyText`, `semanticText`, and a non-secret `providerEvidence` reference. Do not add reference Arabic answers or human scores. The legacy output must come from the frozen legacy comparator. The semantic output must come from the shadow request variant named by `semanticMode`. A case marked `BOUNDED_SOURCE_CONTEXT` must use only the source context in the canonical fixture; no previous-target chain or rolling translated context is allowed.

## 2. Prepare the blind A/B package

Use a secret random seed of at least 16 bytes. Keep the mapping key away from the evaluator.

```bash
python3 scripts/x002_blind_eval.py prepare \
  --corpus app/src/test/resources/translation_v1/quality_corpus.json \
  --contexts app/src/test/resources/translation_v1/x002_context_windows.json \
  --paired /secure/path/x002_paired_outputs.json \
  --seed-hex <SECRET_RANDOM_HEX> \
  --blind-out /secure/path/x002_blind_arabic.json \
  --key-out /secure/path/x002_blind_key.json
```

The evaluator receives `x002_blind_arabic.json` only. Each case contains the stable case ID, source text, any canonical source-only context, Output A, Output B, blank scoring fields, and a blank A/B/TIE preference. The evaluator must not receive `x002_blind_key.json` until scoring is locked.

## 3. Arabic scoring rubric

Score **each output independently before choosing a preference**.

### Fidelity / دقة المعنى — 1 to 5

- **5:** preserves the full source meaning, relations, polarity, references, entities, numbers, and required details.
- **4:** meaning is materially correct; only a minor wording/detail issue that does not change the intended message.
- **3:** understandable but has a noticeable omission, addition, ambiguity, or meaning shift.
- **2:** major meaning loss or distortion; important relations, references, or facts are wrong.
- **1:** substantially wrong, non-translation, or contradicts the source.

### Naturalness / طبيعية العربية — 1 to 5

- **5:** fluent, idiomatic Arabic appropriate to the source register.
- **4:** natural and clear with only a minor awkward phrase.
- **3:** understandable but noticeably literal, awkward, or inconsistent in register.
- **2:** difficult or unnatural Arabic that interferes with comprehension.
- **1:** unusable Arabic, wrong language, or incoherent output.

### Critical error / خطأ حرج

Set `criticalError=true` for any error that makes the output unsafe as production translation evidence, including: polarity/negation reversal; material number/date/currency/unit/identifier corruption; wrong named-entity reference; omission or invention of essential meaning; translating the wrong target because context leaked into the answer; obeying instruction-like source text instead of translating it; or wrong-language/non-translation output.

`errorTypes` may contain concise labels such as `NEGATION`, `NUMBER`, `ENTITY`, `OMISSION`, `ADDITION`, `REFERENCE`, `CONTEXT_LEAK`, `PROMPT_INJECTION`, `WRONG_LANGUAGE`, or `OTHER`. Notes should explain only observable translation problems and must not speculate about which path produced the output.

After scoring both outputs, set `preference` to `A`, `B`, or `TIE` based on overall translation quality. Do not use knowledge of implementation identity.

## 4. Lock scoring, unblind, and compute deterministic metrics

```bash
python3 scripts/x002_blind_eval.py score \
  --scored /secure/path/x002_blind_arabic.scored.json \
  --key /secure/path/x002_blind_key.json \
  --out /secure/path/x002_unblinded_report.json
```

The scorer fails closed if a score, preference, case, or mapping is missing. It reports fidelity mean, naturalness mean, count/rate of cases rated at least 4 on both dimensions, critical-error count, and blind preference counts for each path.

## Decision rule

For the N25 acceptance threshold, a path passes only when **at least 90% of the complete 48-case set receives both fidelity >= 4 and naturalness >= 4, with zero critical errors**. If semantic fails this threshold, it cannot replace legacy. If semantic passes while legacy does not, the report marks semantic as a candidate for integrator review, not production truth. If both pass, the tool reports both results and blind preferences; the acceptance contract does not define an automatic superiority margin, so the Integrator must make the comparative gate judgment from the completed human evidence.

Automated tests, this scorer, or green CI never substitute for actual blind Arabic scores. Until paired provider outputs and completed human scoring exist, X002 remains **PENDING** for semantic replacement.
