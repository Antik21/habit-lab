#!/usr/bin/env python3
"""Hermetic black-box contracts for AutoDev progressive memory (Python 3.9+)."""

from __future__ import print_function

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
SKILL_RELATIVE = Path(".agents/skills/habit-lab-autodev")
MEMORY_SOURCE = REPOSITORY_ROOT / SKILL_RELATIVE
MEMORY = MEMORY_SOURCE / "scripts/autodev_memory.py"
GATE = MEMORY_SOURCE / "scripts/autodev_gate.py"
STAMP = "2026-09-06T12:00:00Z"


def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()


def canonical_digest(value):
    return sha_bytes(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                 ensure_ascii=True).encode("utf-8"))


class MemoryHarness(object):
    """A disposable Git checkout containing only the helper's declared inputs."""

    def __init__(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="habitlab-den20-memory-")
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.email", "qa@example.invalid")
        self.git("config", "user.name", "Memory QA")
        (self.root / ".gitignore").write_text(".autodev/\n", encoding="utf-8")
        (self.root / "source.txt").write_text("initial\n", encoding="utf-8")
        skill = self.root / SKILL_RELATIVE
        (skill / "scripts").mkdir(parents=True)
        shutil.copyfile(str(MEMORY), str(skill / "scripts/autodev_memory.py"))
        shutil.copyfile(str(GATE), str(skill / "scripts/autodev_gate.py"))
        shutil.copytree(str(MEMORY_SOURCE / "memory"), str(skill / "memory"))
        navigation = self.root / "shared/src/commonMain/kotlin/com/denis/habitlab/shared/app"
        navigation.mkdir(parents=True)
        shutil.copyfile(
            str(REPOSITORY_ROOT / "shared/src/commonMain/kotlin/com/denis/habitlab/shared/app/Navigation3AppHost.kt"),
            str(navigation / "Navigation3AppHost.kt"),
        )
        self.git("add", ".")
        self.git("commit", "-qm", "fixture base")

    def close(self):
        self.temporary.cleanup()

    def git(self, *args):
        return subprocess.run(["git"] + list(args), cwd=str(self.root), check=True,
                              text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()

    def head(self):
        return self.git("rev-parse", "HEAD")

    def commit_source(self, value):
        (self.root / "source.txt").write_text(value + "\n", encoding="utf-8")
        self.git("add", "source.txt")
        self.git("commit", "-qm", value)
        return self.head()

    def commit_memory_change(self, value):
        path = self.root / SKILL_RELATIVE / "memory/lessons.md"
        path.write_text(path.read_text(encoding="utf-8") + "\n%s\n" % value, encoding="utf-8")
        self.git("add", str(SKILL_RELATIVE / "memory/lessons.md"))
        self.git("commit", "-qm", "later memory change")
        return self.head()

    def memory_path(self, run_id, name="memory-ledger.json"):
        return self.root / ".autodev/artifacts" / run_id / name

    def call_raw(self, *args):
        return subprocess.run([sys.executable, str(self.root / SKILL_RELATIVE / "scripts/autodev_memory.py")] + list(args),
                              cwd=str(self.root), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              check=False)

    def call(self, *args, **kwargs):
        expected = kwargs.pop("expected", 0)
        if kwargs:
            raise AssertionError("unexpected helper call kwargs: %r" % kwargs)
        result = self.call_raw(*args)
        if result.returncode != expected:
            raise AssertionError("memory helper exit %s, expected %s\nstdout=%s\nstderr=%s" % (
                result.returncode, expected, result.stdout, result.stderr))
        if "Traceback" in result.stdout or "Traceback" in result.stderr:
            raise AssertionError("memory helper exposed a traceback")
        lines = [line for line in result.stdout.splitlines() if line]
        if len(lines) != 1:
            raise AssertionError("memory helper did not emit one JSON object: %r" % result.stdout)
        try:
            payload = json.loads(lines[0])
        except ValueError as exc:
            raise AssertionError("memory helper output is not JSON: %r" % result.stdout) from exc
        if not isinstance(payload, dict):
            raise AssertionError("memory helper output is not an object: %r" % payload)
        if result.returncode:
            if payload.get("ok") is not False:
                raise AssertionError("memory error is not fail-closed: %r" % payload)
        else:
            if payload.get("ok") is not True:
                raise AssertionError("memory success is not affirmative: %r" % payload)
        return payload

    def denied(self, *args):
        result = self.call_raw(*args)
        if result.returncode == 0:
            raise AssertionError("expected memory command rejection: %r" % (args,))
        if "Traceback" in result.stdout or "Traceback" in result.stderr:
            raise AssertionError("rejection exposed traceback")
        lines = [line for line in result.stdout.splitlines() if line]
        if len(lines) != 1:
            raise AssertionError("rejection was not a single JSON error: %r" % result.stdout)
        payload = json.loads(lines[0])
        if payload.get("ok") is not False or not isinstance(payload.get("error"), str):
            raise AssertionError("rejection is not fail closed: %r" % payload)
        return payload

    def start(self, run_id):
        return self.call("start", run_id)

    def finalize(self, run_id, outcome="success", **kwargs):
        command = ["finalize", run_id, "--outcome", outcome,
                   "--duration-seconds", str(kwargs.pop("duration", 3)),
                   "--iterations", str(kwargs.pop("iterations", 1)),
                   "--attempts", str(kwargs.pop("attempts", 1))]
        for name, flag in (("builds", "--build"), ("platforms", "--platform"),
                           ("flaky", "--flaky-step"), ("reads", "--read"), ("writes", "--write")):
            for value in kwargs.pop(name, []):
                command.extend([flag, value])
        gate_run = kwargs.pop("gate_run", None)
        if gate_run is not None:
            command.extend(["--gate-run", gate_run])
        if kwargs:
            raise AssertionError("unexpected finalize kwargs: %r" % kwargs)
        return self.call(*command)

    def ledger(self, run_id):
        return json.loads(self.memory_path(run_id).read_text(encoding="utf-8"))

    def gate_call(self, *args, **kwargs):
        expected = kwargs.pop("expected", 0)
        if kwargs:
            raise AssertionError("unexpected gate call kwargs: %r" % kwargs)
        result = subprocess.run([sys.executable, str(self.root / SKILL_RELATIVE / "scripts/autodev_gate.py")] + list(args),
                                cwd=str(self.root), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                check=False)
        if result.returncode != expected:
            raise AssertionError("gate exit %s, expected %s\nstdout=%s\nstderr=%s" % (
                result.returncode, expected, result.stdout, result.stderr))
        stream = result.stdout if result.returncode == 0 else result.stderr
        lines = [line for line in stream.splitlines() if line]
        if "Traceback" in result.stdout or "Traceback" in result.stderr or len(lines) != 1:
            raise AssertionError("gate emitted a non-JSON traceback/noise: %r / %r" % (result.stdout, result.stderr))
        payload = json.loads(lines[0])
        if bool(payload.get("ok")) != (result.returncode == 0):
            raise AssertionError("gate response is not fail closed: %r" % payload)
        return payload

    def write_gate_receipt(self, run_id, name, value):
        path = self.root / ".autodev/artifacts" / run_id / name
        path.write_text(json.dumps(value, sort_keys=True), encoding="utf-8")
        return ".autodev/artifacts/%s/%s" % (run_id, name)

    def seal_gate_success(self, run_id):
        """Create a real sealed success so correction/self-patch tests stay black-box."""
        revision = self.head()
        self.gate_call("init", run_id, "--task-id", "DEN-20", "--task-type", "feature",
                       "--source-revision", revision, "--blast-radius", "memory", "--platform", "android")
        self.gate_call("add", run_id, "--criterion-id", "main", "--text", "memory contract",
                       "--kind", "main", "--evidence-type", "command", "--platform", "android")
        self.gate_call("freeze", run_id)
        evidence = self.write_gate_receipt(run_id, "proof.json", {
            "schemaVersion": 1, "kind": "command-evidence", "sourceRevision": revision,
            "timestamp": STAMP, "platform": "android", "criterionId": "main", "result": "pass",
            "exitCode": 0, "command": "./gradlew check",
        })
        self.gate_call("pass", run_id, "--criterion", "main", "--platform", "android",
                       "--evidence", evidence, "--source-revision", revision)
        self.start(run_id)
        self.finalize(run_id, builds=["check:pass"], platforms=["android"])
        memory = self.call("receipt", run_id, "--source-revision", revision)["receipt"]
        build = self.write_gate_receipt(run_id, "build.json", {
            "schemaVersion": 1, "kind": "build", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew build", "exitCode": 0, "platforms": ["android"],
        })
        test = self.write_gate_receipt(run_id, "test.json", {
            "schemaVersion": 1, "kind": "test", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew test", "exitCode": 0, "platforms": ["android"],
        })
        review = self.write_gate_receipt(run_id, "review.json", {
            "schemaVersion": 1, "kind": "review", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "independent": True, "reviewedRevision": revision,
            "unresolvedJustifiedFindings": 0, "lateRegressionDigest": canonical_digest([]),
        })
        cleanup = self.write_gate_receipt(run_id, "cleanup.json", {
            "schemaVersion": 1, "kind": "cleanup", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "residualScratch": [], "forbiddenHooks": [],
            "secretScan": {"status": "pass", "findings": 0, "checkedPaths": [],
                           "allowlistedPaths": [], "coverage": "fixture bounded scan"},
        })
        finished = self.gate_call("finish", run_id, "--outcome", "success", "--source-revision", revision,
                                  "--build-receipt", build, "--test-receipt", test,
                                  "--memory-receipt", memory, "--review-receipt", review,
                                  "--cleanup-receipt", cleanup)
        if finished.get("draftPr") != "eligible":
            raise AssertionError("fixture gate did not seal success: %r" % finished)
        return revision

    def instruction_record(self, run_id, name, structure=False, evaluation=None):
        relative = ".agents/skills/habit-lab-autodev/memory/instructions/%s.json" % name
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        value = {"schemaVersion": 1, "kind": "autodev-instruction-record", "runId": run_id,
                 "instructionId": name, "instruction": "Use the reviewed correction.",
                 "structureChange": structure, "evalReceipt": evaluation}
        path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")
        return relative, value


class AutoDevMemoryTest(unittest.TestCase):
    def setUp(self):
        self.memory = MemoryHarness()

    def tearDown(self):
        self.memory.close()

    def test_json_only_start_load_plan_and_local_history_classification(self):
        initial = self.memory.start("first")
        self.assertEqual(["memory.screen-navigation", "memory.lessons"],
                         [item["entryId"] for item in initial["loaded"]])
        self.assertTrue(all("content" in item and len(item["sha256"]) == 64 for item in initial["loaded"]))
        status = self.memory.call("status", "first")
        self.assertEqual("loaded", status["classification"]["memory.screen-navigation"])
        self.assertEqual("unknown", status["classification"]["nav.settings.logic"])
        plan = self.memory.call("plan", "first", "--tag", "experiment", "--tag", "navigation")
        self.assertLessEqual(len(plan["selected"]), 3)
        self.assertEqual(3, len(plan["selected"]))
        first_entry = plan["selected"][0]
        loaded = self.memory.call("load", "first", first_entry["id"])
        self.assertEqual(first_entry["path"], loaded["path"])
        self.assertEqual(sha_bytes(loaded["content"].encode("utf-8")), loaded["sha256"])
        self.assertNotIn(plan["selected"][1]["id"], [item["entryId"] for item in self.memory.ledger("first")["loadedEntries"]])
        self.memory.finalize("first")

        self.memory.start("second")
        second = self.memory.call("plan", "second", "--tags", "experiment,navigation")
        self.assertEqual("stale", second["classification"][first_entry["id"]])
        self.memory.call("load", "second", first_entry["id"])
        self.memory.finalize("second")

        self.memory.start("third")
        third = self.memory.call("plan", "third", "--tag", "experiment")
        self.assertEqual("hot", third["classification"][first_entry["id"]])
        self.assertEqual("never", third["classification"]["nav.settings.logic"])

    def test_plan_freezes_after_a_noninitial_load_and_read_write_duplicates_are_scoped(self):
        self.memory.start("planned")
        plan = self.memory.call("plan", "planned", "--tag", "navigation")
        self.memory.call("load", "planned", plan["selected"][0]["id"])
        before = self.memory.ledger("planned")
        self.memory.denied("plan", "planned", "--tag", "experiment")
        self.assertEqual(before, self.memory.ledger("planned"))

        self.memory.start("cross-list")
        self.memory.finalize("cross-list", reads=["source.txt"], writes=["source.txt"])
        ledger = self.memory.ledger("cross-list")
        self.assertIn("source.txt", [item["path"] for item in ledger["reads"]])
        self.assertEqual(["source.txt"], [item["path"] for item in ledger["writes"]])

        for field in ("read", "write"):
            run_id = "duplicate-" + field
            self.memory.start(run_id)
            self.memory.denied("finalize", run_id, "--outcome", "success",
                               "--" + field, "source.txt", "--" + field, "source.txt")
            self.assertIsNone(self.memory.ledger(run_id)["outcome"])

    def test_initial_catalog_paths_cannot_redirect_the_mandatory_records(self):
        expected = {
            "memory.screen-navigation": "memory/screen-navigation.md",
            "memory.lessons": "memory/lessons.md",
        }
        for index, (entry_id, source_path) in enumerate(sorted(expected.items())):
            if index:
                self.memory.close(); self.memory = MemoryHarness()
            catalog_path = self.memory.root / SKILL_RELATIVE / "memory/catalog.json"
            catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
            entry = next(item for item in catalog["entries"] if item["id"] == entry_id)
            entry["path"] = "memory/lessons.md" if source_path.endswith("screen-navigation.md") else "memory/screen-navigation.md"
            catalog_path.write_text(json.dumps(catalog), encoding="utf-8")
            self.memory.denied("start", "redirect")

    def test_non_success_gate_lifecycle_retains_evidence_without_success_receipts(self):
        revision = self.memory.head()
        self.memory.gate_call("init", "blocked", "--task-id", "DEN-20", "--task-type", "feature",
                              "--source-revision", revision, "--blast-radius", "memory", "--platform", "android")
        proof = self.memory.write_gate_receipt("blocked", "retained.json", {
            "schemaVersion": 1, "kind": "command-evidence", "sourceRevision": revision,
            "timestamp": STAMP, "platform": "android", "criterionId": "main", "result": "pass",
            "exitCode": 0, "command": "./gradlew check",
        })
        finished = self.memory.gate_call("finish", "blocked", "--outcome", "blocked",
                                        "--source-revision", revision, "--reason", "fixture blocked")
        self.assertEqual("blocked", finished["outcome"])
        self.assertTrue((self.memory.root / proof).is_file())
        report = self.memory.memory_path("blocked", "report.md").read_text(encoding="utf-8")
        self.assertIn("Outcome: `blocked`", report)
        self.assertIn("Draft PR: not eligible", report)

    def test_structural_receipt_is_derived_and_accepted_by_helper_and_gate(self):
        base = self.memory.head()
        run_id = "structural"
        self.memory.gate_call("init", run_id, "--task-id", "DEN-20", "--task-type", "feature",
                              "--source-revision", base, "--blast-radius", "memory", "--platform", "android")
        self.memory.gate_call("add", run_id, "--criterion-id", "main", "--text", "memory contract",
                              "--kind", "main", "--evidence-type", "command", "--platform", "android")
        self.memory.gate_call("freeze", run_id)
        self.memory.start(run_id)
        self.memory.finalize(run_id, builds=["check:pass"], platforms=["android"])

        relative, _ = self.memory.instruction_record(
            run_id, "derived", structure=True, evaluation=".autodev/artifacts/structural/eval.json")
        self.memory.git("add", relative)
        self.memory.git("commit", "-qm", "add structural instruction record")
        checked = self.memory.head()
        record_digest = sha_bytes((self.memory.root / relative).read_bytes())
        checked_change_digest = canonical_digest({
            "sourceRevision": checked,
            "paths": [{"path": relative, "sha256": record_digest}],
        })
        evaluation = self.memory.write_gate_receipt(run_id, "eval.json", {
            "schemaVersion": 1, "kind": "autodev-memory-eval", "runId": run_id,
            "sourceRevision": checked, "status": "pass", "command": "python3 focused_test.py",
            "exitCode": 0, "checkedChangeDigest": checked_change_digest, "regressionResult": "pass",
        })
        memory = self.memory.call("receipt", run_id, "--source-revision", checked,
                                  "--eval-receipt", evaluation)["receipt"]
        receipt_value = json.loads((self.memory.root / memory).read_text(encoding="utf-8"))
        self.assertTrue(receipt_value["structureChanged"])
        self.assertEqual(1, receipt_value["instructionPatchCount"])
        self.assertEqual(checked_change_digest,
                         json.loads((self.memory.root / evaluation).read_text(encoding="utf-8"))["checkedChangeDigest"])

        # These flags are intentionally absent from the receipt CLI: their
        # values are derived from the sealed Git delta, not operator input.
        self.memory.denied("receipt", run_id, "--source-revision", checked, "--structure-changed")
        self.memory.denied("receipt", run_id, "--source-revision", checked, "--instruction-patch-count", "0")

        proof = self.memory.write_gate_receipt(run_id, "proof.json", {
            "schemaVersion": 1, "kind": "command-evidence", "sourceRevision": checked,
            "timestamp": STAMP, "platform": "android", "criterionId": "main", "result": "pass",
            "exitCode": 0, "command": "./gradlew check",
        })
        self.memory.gate_call("pass", run_id, "--criterion", "main", "--platform", "android",
                              "--evidence", proof, "--source-revision", checked)
        build = self.memory.write_gate_receipt(run_id, "build.json", {
            "schemaVersion": 1, "kind": "build", "sourceRevision": checked, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew build", "exitCode": 0, "platforms": ["android"],
        })
        test = self.memory.write_gate_receipt(run_id, "test.json", {
            "schemaVersion": 1, "kind": "test", "sourceRevision": checked, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew test", "exitCode": 0, "platforms": ["android"],
        })
        review = self.memory.write_gate_receipt(run_id, "review.json", {
            "schemaVersion": 1, "kind": "review", "sourceRevision": checked, "timestamp": STAMP,
            "status": "pass", "independent": True, "reviewedRevision": checked,
            "unresolvedJustifiedFindings": 0, "lateRegressionDigest": canonical_digest([]),
        })
        cleanup = self.memory.write_gate_receipt(run_id, "cleanup.json", {
            "schemaVersion": 1, "kind": "cleanup", "sourceRevision": checked, "timestamp": STAMP,
            "status": "pass", "residualScratch": [], "forbiddenHooks": [],
            "secretScan": {"status": "pass", "findings": 0, "checkedPaths": [],
                            "allowlistedPaths": [], "coverage": "fixture bounded scan"},
        })
        finished = self.memory.gate_call(
            "finish", run_id, "--outcome", "success", "--source-revision", checked,
            "--build-receipt", build, "--test-receipt", test, "--memory-receipt", memory,
            "--review-receipt", review, "--cleanup-receipt", cleanup)
        self.assertEqual("eligible", finished["draftPr"])

    def test_locked_directory_propagates_a_body_oserror_without_reclassification(self):
        script = r'''
import importlib.util
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
root = Path(sys.argv[2])
spec = importlib.util.spec_from_file_location("autodev_memory_lock_probe", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
try:
    with module.locked_directory(root):
        raise OSError(5, "body fixture")
except OSError as exc:
    print(json.dumps({"classification": "body-oserror", "errno": exc.errno}, sort_keys=True))
except module.MemoryError as exc:
    print(json.dumps({"classification": "memory", "exitCode": exc.code}, sort_keys=True))
'''
        # macOS often resolves /var through a symlink; use a disposable directory
        # below the physical workspace so the probe reaches the lock body.
        with tempfile.TemporaryDirectory(prefix=".qa-autodev-lock-", dir=str(REPOSITORY_ROOT)) as temporary:
            result = subprocess.run([sys.executable, "-c", script,
                                     str(self.memory.root / SKILL_RELATIVE / "scripts/autodev_memory.py"),
                                     temporary], cwd=str(self.memory.root), text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual({"classification": "body-oserror", "errno": 5}, json.loads(result.stdout))

    def test_self_patch_reservation_recovers_after_git_add_or_commit_failure(self):
        real_git = shutil.which("git")
        self.assertIsNotNone(real_git)
        for index, failing_command in enumerate(("add", "commit")):
            if index:
                self.memory.close(); self.memory = MemoryHarness()
            self.memory.seal_gate_success("recover")
            record, _ = self.memory.instruction_record("recover", "retry-" + failing_command)
            with tempfile.TemporaryDirectory(prefix="habitlab-den20-fake-git-") as temporary:
                fake_bin = Path(temporary)
                fake_git = fake_bin / "git"
                fake_git.write_text("#!/bin/sh\nif [ \"$1\" = \"%s\" ]; then exit 91; fi\nexec \"%s\" \"$@\"\n" %
                                    (failing_command, real_git), encoding="utf-8")
                fake_git.chmod(0o700)
                original_path = os.environ.get("PATH", "")
                os.environ["PATH"] = str(fake_bin) + os.pathsep + original_path
                try:
                    self.memory.denied("self-patch-commit", "recover", "--record", record,
                                       "--message", "DEN-20 retry", "--confirm-commit")
                finally:
                    os.environ["PATH"] = original_path
            self.assertTrue(self.memory.call("self-patch-validate", "recover", "--record", record)["eligible"])

    def test_gate_initialized_run_accepts_required_memory_start(self):
        # The gate-owned artifact directory must attach to this run and retain
        # the helper's exact two initial loads.
        revision = self.memory.head()
        self.memory.gate_call("init", "integrated", "--task-id", "DEN-20", "--task-type", "feature",
                              "--source-revision", revision, "--blast-radius", "memory", "--platform", "android")
        started = self.memory.start("integrated")
        self.assertEqual(2, len(started["loaded"]))

    def test_active_matching_gate_run_can_bind_memory_before_terminal_finish(self):
        revision = self.memory.head()
        run_id = "active-bound"
        self.memory.gate_call("init", run_id, "--task-id", "DEN-20", "--task-type", "feature",
                              "--source-revision", revision, "--blast-radius", "memory", "--platform", "android")
        self.memory.gate_call("add", run_id, "--criterion-id", "main", "--text", "memory contract",
                              "--kind", "main", "--evidence-type", "command", "--platform", "android")
        self.memory.gate_call("freeze", run_id)
        proof = self.memory.write_gate_receipt(run_id, "proof.json", {
            "schemaVersion": 1, "kind": "command-evidence", "sourceRevision": revision,
            "timestamp": STAMP, "platform": "android", "criterionId": "main", "result": "pass",
            "exitCode": 0, "command": "./gradlew check",
        })
        self.memory.gate_call("pass", run_id, "--criterion", "main", "--platform", "android",
                              "--evidence", proof, "--source-revision", revision)

        self.memory.start(run_id)
        finalized = self.memory.finalize(run_id, builds=["check:pass"], platforms=["android"],
                                        gate_run=run_id)
        self.assertEqual("success", finalized["outcome"])
        ledger = self.memory.ledger(run_id)
        self.assertEqual(run_id, ledger["gateRun"])
        self.assertIsNone(ledger["gateStatusSha256"])
        self.assertIsNone(self.memory.gate_call("status", run_id).get("terminal"))

        proposal = json.dumps({"schemaVersion": 1, "proposalId": "unsealed-bound", "kind": "code-correction",
                               "claim": "Terminal gate status is mandatory.", "trigger": "finalization",
                               "nextTime": "seal gate first", "invalidation": "contract changes"})
        self.memory.denied("observe-correction", run_id, "--proposal-json", proposal)
        self.memory.denied("self-patch-validate", run_id,
                           "--record", ".agents/skills/habit-lab-autodev/memory/instructions/unsealed.json")

        memory = self.memory.call("receipt", run_id, "--source-revision", revision)["receipt"]
        build = self.memory.write_gate_receipt(run_id, "build.json", {
            "schemaVersion": 1, "kind": "build", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew build", "exitCode": 0, "platforms": ["android"],
        })
        test = self.memory.write_gate_receipt(run_id, "test.json", {
            "schemaVersion": 1, "kind": "test", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "command": "./gradlew test", "exitCode": 0, "platforms": ["android"],
        })
        review = self.memory.write_gate_receipt(run_id, "review.json", {
            "schemaVersion": 1, "kind": "review", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "independent": True, "reviewedRevision": revision,
            "unresolvedJustifiedFindings": 0, "lateRegressionDigest": canonical_digest([]),
        })
        cleanup = self.memory.write_gate_receipt(run_id, "cleanup.json", {
            "schemaVersion": 1, "kind": "cleanup", "sourceRevision": revision, "timestamp": STAMP,
            "status": "pass", "residualScratch": [], "forbiddenHooks": [],
            "secretScan": {"status": "pass", "findings": 0, "checkedPaths": [],
                            "allowlistedPaths": [], "coverage": "fixture bounded scan"},
        })
        finished = self.memory.gate_call(
            "finish", run_id, "--outcome", "success", "--source-revision", revision,
            "--build-receipt", build, "--test-receipt", test, "--memory-receipt", memory,
            "--review-receipt", review, "--cleanup-receipt", cleanup)
        self.assertEqual("eligible", finished["draftPr"])
        self.assertEqual("candidate", self.memory.call(
            "observe-correction", run_id, "--proposal-json", proposal)["lifecycle"])

    def test_self_patch_lifecycle_serializes_reservation_owners_and_recovers(self):
        run_id = "reservation-race"
        self.memory.seal_gate_success(run_id)
        record, _ = self.memory.instruction_record(run_id, "reservation-race")
        real_git = shutil.which("git")
        self.assertIsNotNone(real_git)

        with tempfile.TemporaryDirectory(prefix="habitlab-den20-reservation-") as temporary:
            temporary_root = Path(temporary)
            ready = temporary_root / "ready"
            release = temporary_root / "release"
            fake_bin = temporary_root / "bin"
            fake_bin.mkdir()
            fake_git = fake_bin / "git"
            fake_git.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"add\" ]; then\n"
                "  : > \"$QA_RESERVATION_READY\"\n"
                "  while [ ! -e \"$QA_RESERVATION_RELEASE\" ]; do sleep 0.05; done\n"
                "  exit 91\n"
                "fi\n"
                "exec \"%s\" \"$@\"\n" % real_git,
                encoding="utf-8")
            fake_git.chmod(0o700)
            first_environment = dict(os.environ)
            first_environment["PATH"] = str(fake_bin) + os.pathsep + first_environment.get("PATH", "")
            first_environment["QA_RESERVATION_READY"] = str(ready)
            first_environment["QA_RESERVATION_RELEASE"] = str(release)
            first = subprocess.Popen(
                [sys.executable, str(self.memory.root / SKILL_RELATIVE / "scripts/autodev_memory.py"),
                 "self-patch-commit", run_id, "--record", record, "--message", "DEN-20 first",
                 "--confirm-commit"],
                cwd=str(self.memory.root), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                env=first_environment)
            second_started = temporary_root / "second-started"
            second_add = temporary_root / "second-add"
            second_bin = temporary_root / "second-bin"
            second_bin.mkdir()
            second_git = second_bin / "git"
            second_git.write_text(
                "#!/bin/sh\n"
                "if [ \"$1\" = \"add\" ]; then : > \"$QA_SECOND_ADD\"; fi\n"
                "exec \"%s\" \"$@\"\n" % real_git,
                encoding="utf-8")
            second_git.chmod(0o700)
            second_python = temporary_root / "python"
            second_python.write_text(
                "#!/bin/sh\n"
                ": > \"$QA_SECOND_STARTED\"\n"
                "exec \"$QA_REAL_PYTHON\" \"$@\"\n",
                encoding="utf-8")
            second_python.chmod(0o700)
            second_environment = dict(os.environ)
            second_environment["PATH"] = str(second_bin) + os.pathsep + second_environment.get("PATH", "")
            second_environment["QA_SECOND_STARTED"] = str(second_started)
            second_environment["QA_SECOND_ADD"] = str(second_add)
            second_environment["QA_REAL_PYTHON"] = sys.executable
            second = None
            try:
                deadline = time.monotonic() + 10
                while not ready.exists() and first.poll() is None and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue(ready.exists(), "first owner never reserved and paused at git add")

                second = subprocess.Popen(
                    [str(second_python), str(self.memory.root / SKILL_RELATIVE / "scripts/autodev_memory.py"),
                     "self-patch-commit", run_id, "--record", record, "--message", "DEN-20 resumed",
                     "--confirm-commit"],
                    cwd=str(self.memory.root), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                    env=second_environment)
                deadline = time.monotonic() + 10
                while not second_started.exists() and second.poll() is None and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue(second_started.exists(), "second owner never started")
                time.sleep(0.2)
                self.assertFalse(second_add.exists(), "second owner bypassed the lifecycle reservation lock")
            finally:
                release.touch()
                first_stdout, first_stderr = first.communicate(timeout=10)
                if second is not None:
                    second_stdout, second_stderr = second.communicate(timeout=20)
            self.assertNotEqual(0, first.returncode)
            self.assertNotIn("Traceback", first_stdout + first_stderr)
            self.assertIs(json.loads(first_stdout)["ok"], False)
            self.assertEqual(0, second.returncode, second_stdout + second_stderr)
            resumed = json.loads(second_stdout)
            self.assertTrue(resumed["ok"])
            self.assertTrue(resumed["postCommitValidationRequired"])
            self.assertTrue(self.memory.call("self-patch-record", run_id, "--record", record)["recorded"])

    def test_finalized_ledger_receipt_and_consolidation_never_rewrite_memory(self):
        for number in range(1, 6):
            run = "run%s" % number
            if number == 5:
                self.memory.gate_call("init", run, "--task-id", "DEN-20", "--task-type", "feature",
                                      "--source-revision", self.memory.head(), "--blast-radius", "memory",
                                      "--platform", "android")
            self.memory.start(run)
            self.memory.finalize(run, builds=["check:pass"], platforms=["android"], flaky=["retry"],
                                 reads=["source.txt"])
        ledger = self.memory.ledger("run5")
        self.assertEqual("success", ledger["outcome"])
        self.assertIsNotNone(ledger["finalizedAt"])
        self.assertEqual([{"name": "check", "status": "pass"}], ledger["builds"])
        self.assertEqual(["android"], ledger["platforms"])
        advice = self.memory.call("consolidate", "--run-id", "run5")
        self.assertFalse(advice["rewritten"])
        self.assertEqual("terminal-run-milestone", advice["advisory"]["reason"])
        before = self.memory.ledger("run5")
        self.memory.call("consolidate")
        self.assertEqual(before, self.memory.ledger("run5"))

        source = self.memory.head()
        receipt = self.memory.call("receipt", "run5", "--source-revision", source)
        value = receipt["value"]
        self.assertEqual("memory", value["kind"])
        self.assertEqual("pass", value["lint"]["status"])
        self.assertEqual(value["ledgerSha256"], sha_bytes(self.memory.memory_path("run5").read_bytes()))
        self.assertEqual(sorted(value["loaded"], key=lambda item: item["entryId"]), value["loaded"])
        pressure = self.memory.root / SKILL_RELATIVE / "memory/nav/settings/screen.md"
        pressure.write_text(pressure.read_text(encoding="utf-8") + (" pressure" * 600), encoding="utf-8")
        pressured = self.memory.call("consolidate")
        self.assertFalse(pressured["rewritten"])
        self.assertEqual("lint-pressure", pressured["advisory"]["reason"])
        self.assertEqual(before, self.memory.ledger("run5"))

    def test_lint_rejects_budget_toc_journal_lessons_and_route_conflicts(self):
        cases = []
        screen = self.memory.root / SKILL_RELATIVE / "memory/nav/settings/screen.md"
        cases.append(("budget", "memory/nav/settings/screen.md", screen.read_text(encoding="utf-8") + (" excess" * 600),
                      "exceeds word budget"))
        toc = self.memory.root / SKILL_RELATIVE / "memory/screen-navigation.md"
        cases.append(("toc", "memory/screen-navigation.md", toc.read_text(encoding="utf-8").replace("nav/settings/logic.md", "removed.md"),
                      "screen navigation index omits"))
        cases.append(("journal", "memory/nav/settings/screen.md", screen.read_text(encoding="utf-8") + "\nJournal observation.\n",
                      "diary or journal wording"))
        for number, (label, relative, content, expected) in enumerate(cases):
            if number:
                self.memory.close(); self.memory = MemoryHarness()
            path = self.memory.root / SKILL_RELATIVE / relative
            path.write_text(content, encoding="utf-8")
            result = self.memory.call("lint", expected=4)
            self.assertTrue(any(expected in item for item in result["errors"]), (label, result))

        lessons = self.memory.root / SKILL_RELATIVE / "memory/lessons.md"
        lessons.write_text(lessons.read_text(encoding="utf-8") + """
## Lesson One
Trigger: save
Scope: editor
Fact: Save requires a durable record.
Evidence: fixture
Next time: verify it
Invalidation: owner changes
Verification date: 2026-09-06
## Lesson Two
Trigger: save
Scope: editor
Fact: Save never requires a durable record.
Evidence: fixture
Next time: verify it
Invalidation: owner changes
Verification date: 2026-09-06
## Lesson Three
Trigger: other
Scope: list
Fact: Save requires a durable record.
Evidence: fixture
Next time: verify it
Invalidation: owner changes
Verification date: 2026-09-06
""", encoding="utf-8")
        result = self.memory.call("lint", expected=4)
        self.assertTrue(any("duplicate claims" in item for item in result["errors"]))
        self.assertTrue(any("contradicts a fact" in item for item in result["errors"]))

        self.memory.close(); self.memory = MemoryHarness()
        catalog_path = self.memory.root / SKILL_RELATIVE / "memory/catalog.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
        logic = next(item for item in catalog["entries"] if item["id"] == "nav.experiment-list.logic")
        logic.update({"kind": "nav-screen", "routeKey": "Gallery", "destination": "AppDestination.Settings"})
        catalog_path.write_text(json.dumps(catalog), encoding="utf-8")
        result = self.memory.call("lint", expected=4)
        self.assertTrue(any("contradictory route key destination" in item for item in result["errors"]))

    def test_tamper_symlink_traversal_and_stale_cas_are_fail_closed(self):
        self.memory.start("safe")
        self.memory.memory_path("safe").write_text("{}", encoding="utf-8")
        self.memory.denied("status", "safe")

        self.memory.close(); self.memory = MemoryHarness()
        self.memory.start("link")
        ledger = self.memory.memory_path("link")
        target = ledger.with_name("target.json")
        ledger.replace(target)
        ledger.symlink_to(target.name)
        self.memory.denied("status", "link")
        self.memory.denied("start", "../escape")
        self.memory.start("paths")
        self.memory.denied("finalize", "paths", "--outcome", "success", "--read", "../source.txt")

        # Independent processes either serialize or reject a stale compare-and-swap;
        # neither may corrupt a signed ledger or emit non-JSON output.
        self.memory.start("race")
        commands = [[sys.executable, str(self.memory.root / SKILL_RELATIVE / "scripts/autodev_memory.py"),
                     "plan", "race", "--tag", "navigation"] for _ in range(8)]
        processes = [subprocess.Popen(command, cwd=str(self.memory.root), text=True,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE) for command in commands]
        results = [process.communicate() for process in processes]
        for process, (stdout, stderr) in zip(processes, results):
            self.assertNotIn("Traceback", stdout + stderr)
            line = (stdout or stderr).strip()
            self.assertIsInstance(json.loads(line), dict)
            self.assertIn(process.returncode, (0, 3, 5))
        final = self.memory.call_raw("status", "race")
        self.assertNotIn("Traceback", final.stdout + final.stderr)
        self.assertIsInstance(json.loads(final.stdout.strip()), dict)
        self.assertIn(final.returncode, (0, 3, 5))

    def test_historical_sealed_runs_and_forged_ledger_anchor_are_rejected(self):
        original = self.memory.seal_gate_success("old")
        later = self.memory.commit_memory_change("Later tracked-memory wording remains historical, not a journal.")
        self.assertNotEqual(original, later)
        self.memory.call("status", "old")
        proposal = json.dumps({"schemaVersion": 1, "proposalId": "history", "kind": "code-correction",
                               "claim": "Preserve sealed history.", "trigger": "history", "nextTime": "verify",
                               "invalidation": "policy changes"})
        observed = self.memory.call("observe-correction", "old", "--proposal-json", proposal)
        self.assertEqual("candidate", observed["lifecycle"])
        anchor = self.memory.memory_path("old", "memory-ledger.json.anchor")
        anchor.write_text(json.dumps({"sha256": "0" * 64}), encoding="utf-8")
        self.memory.denied("status", "old")
        self.memory.denied("observe-correction", "old", "--proposal-json", proposal)

    def test_correction_lifecycle_rejects_same_run_divergence_tamper_and_failed_sources(self):
        self.memory.seal_gate_success("one")
        self.memory.commit_source("two")
        self.memory.seal_gate_success("two")
        self.memory.commit_source("three")
        self.memory.seal_gate_success("three")
        proposal = {"schemaVersion": 1, "proposalId": "correction", "kind": "code-correction",
                    "claim": "Keep evidence atomic.", "trigger": "finish", "nextTime": "check receipts",
                    "invalidation": "gate changes"}
        encoded = json.dumps(proposal)
        first = self.memory.call("observe-correction", "one", "--proposal-json", encoded)
        self.assertEqual("candidate", first["lifecycle"])
        self.memory.denied("observe-correction", "one", "--proposal-json", encoded)
        second = self.memory.call("observe-correction", "two", "--proposal-json", encoded)
        self.assertEqual("confirmed", second["lifecycle"])
        third = self.memory.call("observe-correction", "three", "--proposal-json", encoded)
        self.assertEqual("recurrence", third["lifecycle"])
        self.assertIsNotNone(third["advisory"])
        self.assertTrue(self.memory.call("store-correction", "correction", "--run-id", "two")["stored"])

        correction_log = self.memory.root / ".autodev/artifacts/correction-observations.json"
        correction_log.write_text("[]", encoding="utf-8")
        self.memory.denied("observe-correction", "three", "--proposal-json", encoded)
        self.memory.start("failed")
        self.memory.finalize("failed", outcome="failed")
        self.memory.denied("observe-correction", "failed", "--proposal-json", encoded)

    def test_divergent_correction_proposal_id_is_rejected_before_persistence(self):
        self.memory.seal_gate_success("one")
        self.memory.commit_source("second")
        self.memory.seal_gate_success("two")
        proposal = {"schemaVersion": 1, "proposalId": "same-id", "kind": "code-correction",
                    "claim": "First fact.", "trigger": "finish", "nextTime": "check receipts",
                    "invalidation": "gate changes"}
        self.memory.call("observe-correction", "one", "--proposal-json", json.dumps(proposal))
        divergent = dict(proposal); divergent["claim"] = "Different fact."
        self.memory.denied("observe-correction", "two", "--proposal-json", json.dumps(divergent))

    def test_self_patch_frozen_stale_and_fresh_evaluation_lifecycle(self):
        self.memory.seal_gate_success("patch")
        self.memory.denied("self-patch-validate", "patch",
                           "--record", ".agents/skills/habit-lab-autodev/scripts/autodev_memory.py")

        relative, record = self.memory.instruction_record("patch", "isolated", structure=True,
                                                           evaluation=".autodev/artifacts/patch/eval.json")
        base = self.memory.head()
        raw = (self.memory.root / relative).read_bytes()
        digest = canonical_digest({"sourceRevision": base,
                                   "changes": [{"path": relative, "sha256": sha_bytes(raw)}]})
        evaluation_path = self.memory.memory_path("patch", "eval.json")
        evaluation_path.write_text(json.dumps({
            "schemaVersion": 1, "kind": "autodev-memory-eval", "runId": "patch",
            "sourceRevision": "0" * 40, "status": "pass", "command": "python3 test.py", "exitCode": 0,
            "checkedChangeDigest": digest, "regressionResult": "pass",
        }), encoding="utf-8")
        self.memory.denied("self-patch-validate", "patch", "--record", relative)

        evaluation_path.write_text(json.dumps({
            "schemaVersion": 1, "kind": "autodev-memory-eval", "runId": "patch",
            "sourceRevision": base, "status": "pass", "command": "python3 test.py", "exitCode": 0,
            "checkedChangeDigest": digest, "regressionResult": "pass",
        }), encoding="utf-8")
        validated = self.memory.call("self-patch-validate", "patch", "--record", relative)
        self.assertTrue(validated["eligible"])
        committed = self.memory.call("self-patch-commit", "patch", "--record", relative,
                                     "--message", "DEN-20 record instruction", "--confirm-commit")
        self.assertTrue(committed["postCommitValidationRequired"])

        committed_head = self.memory.head()
        committed_raw = (self.memory.root / relative).read_bytes()
        committed_digest = canonical_digest({"sourceRevision": committed_head,
                                             "changes": [{"path": relative,
                                                          "sha256": sha_bytes(committed_raw)}]})
        evaluation_path.write_text(json.dumps({
            "schemaVersion": 1, "kind": "autodev-memory-eval", "runId": "patch",
            "sourceRevision": committed_head, "status": "pass", "command": "python3 test.py", "exitCode": 0,
            "checkedChangeDigest": committed_digest, "regressionResult": "pass",
        }), encoding="utf-8")
        self.assertTrue(self.memory.call("self-patch-record", "patch", "--record", relative)["recorded"])
        self.memory.denied("self-patch-validate", "patch", "--record", relative)


if __name__ == "__main__":
    unittest.main(verbosity=2)
