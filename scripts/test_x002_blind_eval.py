import json
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("x002_blind_eval.py")


class X002BlindEvalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.ids = [f"c{i:02d}" for i in range(48)]
        self.corpus = self.root / "corpus.json"
        self.contexts = self.root / "contexts.json"
        self.paired = self.root / "paired.json"
        self.corpus.write_text(json.dumps({
            "schemaVersion": 2,
            "cases": [{"id": case_id, "source": f"Source {case_id}."} for case_id in self.ids],
        }), encoding="utf-8")
        self.contexts.write_text(json.dumps({
            "schemaVersion": 1,
            "cases": [{"id": self.ids[0], "before": ["Previous source."], "after": []}],
        }), encoding="utf-8")
        self.paired.write_text(json.dumps({
            "schemaVersion": 1,
            "sourceSha": "1" * 40,
            "providerEvidence": "fixture",
            "cases": [
                {
                    "id": case_id,
                    "legacyText": f"قديم {i}",
                    "semanticText": f"دلالي {i}",
                    "semanticMode": "BOUNDED_SOURCE_CONTEXT" if i == 0 else "UNIT_ONLY",
                }
                for i, case_id in enumerate(self.ids)
            ],
        }), encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, *args, check=True):
        return subprocess.run(["python3", str(SCRIPT), *map(str, args)], text=True, capture_output=True, check=check)

    def test_prepare_and_score_round_trip(self):
        blind = self.root / "blind.json"
        key = self.root / "key.json"
        report = self.root / "report.json"
        self.run_script(
            "prepare", "--corpus", self.corpus, "--contexts", self.contexts,
            "--paired", self.paired, "--seed-hex", "00112233445566778899aabbccddeeff",
            "--blind-out", blind, "--key-out", key,
        )
        blind_data = json.loads(blind.read_text(encoding="utf-8"))
        self.assertEqual(48, len(blind_data["cases"]))
        self.assertTrue(blind_data["pathLabelsHidden"])
        for case in blind_data["cases"]:
            for label in ("A", "B"):
                case["scores"][label].update(fidelity=4, naturalness=4, criticalError=False)
            case["preference"] = "TIE"
        scored = self.root / "scored.json"
        scored.write_text(json.dumps(blind_data, ensure_ascii=False), encoding="utf-8")
        self.run_script("score", "--scored", scored, "--key", key, "--out", report)
        result = json.loads(report.read_text(encoding="utf-8"))
        self.assertTrue(result["legacy"]["n25ThresholdMet"])
        self.assertTrue(result["semantic"]["n25ThresholdMet"])
        self.assertEqual(48, result["blindPreference"]["tie"])

    def test_prepare_rejects_missing_output_case(self):
        paired = json.loads(self.paired.read_text(encoding="utf-8"))
        paired["cases"].pop()
        broken = self.root / "broken.json"
        broken.write_text(json.dumps(paired), encoding="utf-8")
        result = self.run_script(
            "prepare", "--corpus", self.corpus, "--contexts", self.contexts,
            "--paired", broken, "--seed-hex", "00112233445566778899aabbccddeeff",
            "--blind-out", self.root / "blind.json", "--key-out", self.root / "key.json",
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("must match corpus exactly", result.stderr)

    def test_template_is_complete_and_marks_context_mode(self):
        out = self.root / "template.json"
        self.run_script(
            "template", "--corpus", self.corpus, "--contexts", self.contexts,
            "--source-sha", "2" * 40, "--out", out,
        )
        value = json.loads(out.read_text(encoding="utf-8"))
        self.assertEqual(48, len(value["cases"]))
        self.assertEqual("BOUNDED_SOURCE_CONTEXT", value["cases"][0]["semanticMode"])
        self.assertIsNone(value["cases"][0]["legacyText"])
        self.assertIsNone(value["cases"][0]["semanticText"])


if __name__ == "__main__":
    unittest.main()
