#!/usr/bin/env python3
"""Hermetic black-box contracts for AutoDev progressive memory (Python 3.9+)."""

from __future__ import print_function

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
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

    def update_ledger(self, run_id, mutate):
        path = self.memory_path(run_id)
        value = json.loads(path.read_text(encoding="utf-8"))
        mutate(value)
        path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")

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

    def seed_finalized_memory_ledger(self, run_id):
        """Fixture only: permits gate/correction lifecycle testing despite DEN-20's ownership collision."""
        entries, reads = [], []
        for entry_id, relative in (("memory.screen-navigation", "memory/screen-navigation.md"),
                                   ("memory.lessons", "memory/lessons.md")):
            raw = (self.root / SKILL_RELATIVE / relative).read_bytes()
            digest = sha_bytes(raw)
            entries.append({"entryId": entry_id, "path": relative, "sha256": digest, "loadedAt": STAMP})
            reads.append({"path": relative, "sha256": digest})
        ledger = {
            "schemaVersion": 1, "runId": run_id, "createdAt": STAMP, "updatedAt": STAMP,
            "finalizedAt": STAMP, "initialEntryIds": ["memory.screen-navigation", "memory.lessons"],
            "plannedEntryIds": [], "loadedEntries": entries, "reads": reads, "writes": [],
            "durationSeconds": 1, "builds": [{"name": "check", "status": "pass"}],
            "iterations": 1, "attempts": 1, "outcome": "success", "platforms": ["android"],
            "flakySteps": [], "gateRun": None, "gateStatusSha256": None,
        }
        path = self.memory_path(run_id)
        encoded = json.dumps(ledger, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8") + b"\n"
        path.write_bytes(encoded)
        path.with_name("memory-ledger.json.anchor").write_bytes(
            json.dumps({"sha256": sha_bytes(encoded)}, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=True).encode("utf-8") + b"\n")

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
        # Gate init presently owns this artifact directory.  The dedicated
        # regression below proves that the helper cannot create its ledger
        # there; seed an otherwise strict helper-format ledger so the remaining
        # gate-dependent curation lifecycle remains testable in isolation.
        self.seed_finalized_memory_ledger(run_id)
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

    def test_gate_initialized_run_accepts_required_memory_start(self):
        # The documented lifecycle is gate init, then exactly two initial
        # memory loads.  This intentionally exposes a shared-artifact
        # ownership regression rather than papering it over with a fixture.
        revision = self.memory.head()
        self.memory.gate_call("init", "integrated", "--task-id", "DEN-20", "--task-type", "feature",
                              "--source-revision", revision, "--blast-radius", "memory", "--platform", "android")
        started = self.memory.start("integrated")
        self.assertEqual(2, len(started["loaded"]))

    def test_finalized_ledger_receipt_and_consolidation_never_rewrite_memory(self):
        for number in range(1, 6):
            run = "run%s" % number
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
