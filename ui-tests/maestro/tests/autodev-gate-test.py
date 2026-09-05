#!/usr/bin/env python3
"""Hermetic black-box contracts for the AutoDev frozen-checklist gate."""

from __future__ import print_function

import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
GATE = REPOSITORY_ROOT / ".agents/skills/habit-lab-autodev/scripts/autodev_gate.py"
MAX_SCAN_BYTES = 64 * 1024 * 1024
TIMESTAMP = "2026-09-05T12:00:00Z"


def canonical_digest(value):
    data = json.dumps(value, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=True).encode("utf-8")
    return hashlib.sha256(data).hexdigest()


class GateHarness(object):
    def __init__(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="habitlab-den19-gate-")
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.email", "qa@example.invalid")
        self.git("config", "user.name", "Gate QA")
        (self.root / ".gitignore").write_text(".autodev/\n", encoding="utf-8")
        (self.root / "source.txt").write_text("initial\n", encoding="utf-8")
        # Contract-v2 receipts are bound to the checked Git tree, not to an
        # incidental working-copy fixture.  Keep the two mandatory memories
        # tracked exactly as the gate will read them via git show.
        memory = self.root / ".agents/skills/habit-lab-autodev/memory"
        memory.mkdir(parents=True)
        source_memory = REPOSITORY_ROOT / ".agents/skills/habit-lab-autodev/memory"
        for name in ("catalog.json", "screen-navigation.md", "lessons.md"):
            shutil.copyfile(str(source_memory / name), str(memory / name))
        self.git("add", ".gitignore", "source.txt", ".agents/skills/habit-lab-autodev/memory")
        self.git("commit", "-qm", "initial")

    def close(self):
        self.temporary.cleanup()

    def git(self, *args):
        return subprocess.run(["git"] + list(args), cwd=str(self.root), check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              text=True).stdout.strip()

    def head(self):
        return self.git("rev-parse", "HEAD")

    def commit_source(self, value):
        (self.root / "source.txt").write_text(value + "\n", encoding="utf-8")
        self.git("add", "source.txt")
        self.git("commit", "-qm", value)
        return self.head()

    def unrelated_revision(self):
        tree = subprocess.run(["git", "mktree"], cwd=str(self.root), input="", text=True,
                              check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.strip()
        return subprocess.run(["git", "commit-tree", tree, "-m", "unrelated"], cwd=str(self.root),
                              text=True, check=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE).stdout.strip()

    def call(self, *args, **kwargs):
        expected = kwargs.pop("expected", 0)
        if kwargs:
            raise AssertionError("unexpected call kwargs: %r" % kwargs)
        result = subprocess.run(
            [sys.executable, str(GATE)] + list(args), cwd=str(self.root), text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        stream = result.stdout if result.returncode == 0 else result.stderr
        lines = [line for line in stream.splitlines() if line]
        if result.returncode != expected:
            raise AssertionError("gate exit %s, expected %s\nstdout=%s\nstderr=%s" % (
                result.returncode, expected, result.stdout, result.stderr))
        if len(lines) != 1:
            raise AssertionError("gate did not emit one JSON object: %r" % stream)
        try:
            payload = json.loads(lines[0])
        except ValueError as exc:
            raise AssertionError("gate output is not JSON: %r" % stream) from exc
        if not isinstance(payload, dict):
            raise AssertionError("gate output is not an object: %r" % payload)
        if result.returncode != 0:
            if "Traceback" in result.stdout or "Traceback" in result.stderr:
                raise AssertionError("gate exposed a traceback")
            if payload.get("ok") is not False:
                raise AssertionError("error response is not fail-closed: %r" % payload)
        return payload

    def expect_denied(self, *args):
        result = subprocess.run(
            [sys.executable, str(GATE)] + list(args), cwd=str(self.root), text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
        )
        if result.returncode == 0:
            raise AssertionError("expected denied command to fail: %s" % (args,))
        stream = result.stderr
        lines = [line for line in stream.splitlines() if line]
        if len(lines) != 1 or "Traceback" in stream:
            raise AssertionError("denial was not one JSON error: %s" % stream)
        payload = json.loads(lines[0])
        if payload.get("ok") is not False:
            raise AssertionError("denial was not fail closed: %r" % payload)
        return payload

    def init(self, run_id="run", task_type="feature", platforms=None):
        command = ["init", run_id, "--task-id", "DEN-19", "--task-type", task_type,
                   "--source-revision", self.head(), "--blast-radius", "gate"]
        for platform in platforms or []:
            command.extend(["--platform", platform])
        return self.call(*command)

    def state(self, run_id="run"):
        return self.root / ".autodev/state" / run_id

    def artifacts(self, run_id="run"):
        return self.root / ".autodev/artifacts" / run_id

    def relative_artifact(self, name, run_id="run"):
        return ".autodev/artifacts/%s/%s" % (run_id, name)

    def write_json(self, name, value, run_id="run"):
        path = self.artifacts(run_id) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, sort_keys=True), encoding="utf-8")
        return self.relative_artifact(name, run_id)

    def command_evidence(self, name, criterion="main", platform="android", result="pass",
                         revision=None, command="./gradlew check", run_id="run"):
        return self.write_json(name, {
            "schemaVersion": 1,
            "kind": "command-evidence",
            "sourceRevision": revision or self.head(),
            "timestamp": TIMESTAMP,
            "platform": platform,
            "criterionId": criterion,
            "result": result,
            "exitCode": 0 if result == "pass" else 1,
            "command": command,
        }, run_id)

    def observation_evidence(self, name, criterion="repro", platform="android",
                             scenario="repro-submit", outcome="not-reproduced",
                             revision=None, command="./gradlew reproduce",
                             diagnostic="fixture diagnostic"):
        return self.write_json(name, {
            "schemaVersion": 1,
            "kind": "bug-observation",
            "sourceRevision": revision or self.head(),
            "timestamp": TIMESTAMP,
            "platform": platform,
            "criterionId": criterion,
            "scenarioKey": scenario,
            "outcome": outcome,
            "command": command,
            "exitCode": 0 if outcome == "not-reproduced" else 1,
            "diagnostic": diagnostic,
        })

    def metric_evidence(self, name, criterion="perf", platform="android", result="pass",
                        phase="baseline", fingerprint="fixture-v1", revision=None, value=12.5,
                        metric_name="duration", unit="ms", instrumentation="trace-v1",
                        aggregation="median", sample_count=5, direction="decrease",
                        minimum_delta=2.0, delta_unit="absolute"):
        return self.write_json(name, {
            "schemaVersion": 1,
            "kind": "metric-evidence",
            "sourceRevision": revision or self.head(),
            "timestamp": TIMESTAMP,
            "platform": platform,
            "criterionId": criterion,
            "result": result,
            "phase": phase,
            "scenarioFingerprint": fingerprint,
            "metricName": metric_name,
            "value": value,
            "unit": unit,
            "instrumentation": instrumentation,
            "aggregation": aggregation,
            "sampleCount": sample_count,
            "threshold": {
                "direction": direction,
                "minimumDelta": minimum_delta,
                "deltaUnit": delta_unit,
            },
        })

    def attempt_count(self, run_id="run"):
        return len(list((self.state(run_id) / "attempts").glob("*.json")))

    def observation_count(self, run_id="run"):
        return len(list((self.state(run_id) / "observations").glob("*.json")))

    def snapshot_run(self, run_id="run"):
        snapshot = {}
        for root in (self.state(run_id), self.artifacts(run_id)):
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    snapshot[str(path.relative_to(self.root))] = hashlib.sha256(path.read_bytes()).hexdigest()
        return snapshot

    def bounded_scan_race(self, mode):
        target = self.root / "scan-race.bin"
        target.write_bytes(b"safe scan input\n" * 4096)
        script = r'''
import importlib.util
import json
import os
from pathlib import Path
import sys

gate_path, root_text, mode = sys.argv[1:]
spec = importlib.util.spec_from_file_location("autodev_gate_race", gate_path)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
root = Path(root_text)
target = root / "scan-race.bin"
repo_fd = os.open(str(root), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)

class Run(object):
    pass

run = Run()
run.repo_fd = repo_fd
original_read = gate.os.read
original_verify = gate.verify_artifact_path_identity
fired = [False]

def replace_target():
    replacement = root / "scan-race-replacement.bin"
    replacement.write_bytes(b"replacement scan input\n" * 4096)
    os.replace(str(replacement), str(target))

def raced_read(fd, amount):
    data = original_read(fd, amount)
    if data and not fired[0]:
        fired[0] = True
        if mode == "growth":
            with target.open("ab") as stream:
                stream.write(b"growth")
        elif mode == "content-change":
            with target.open("r+b") as stream:
                stream.write(b"changed")
                stream.seek(0, os.SEEK_END)
                stream.write(b"+")
        elif mode == "replacement-during-read":
            replace_target()
    return data

def raced_verify(current_run, parts, expected):
    if mode == "replacement-after-final-stat":
        replace_target()
    return original_verify(current_run, parts, expected)

gate.os.read = raced_read
gate.verify_artifact_path_identity = raced_verify
try:
    gate.bounded_scan(run, ["scan-race.bin"])
except gate.GateError as exc:
    print(json.dumps({"error": str(exc), "exitCode": exc.code}, sort_keys=True))
    sys.exit(0)
finally:
    os.close(repo_fd)
print(json.dumps({"error": "race unexpectedly passed"}, sort_keys=True))
sys.exit(1)
'''
        result = subprocess.run([sys.executable, "-c", script, str(GATE), str(self.root), mode],
                                cwd=str(self.root), text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, check=False)
        if result.returncode != 0:
            raise AssertionError("scan race helper failed: %s\n%s" % (result.stdout, result.stderr))
        return json.loads(result.stdout)

    def junit_evidence(self, name, criterion="main", platform="android", result="pass",
                       revision=None):
        failures = "0" if result == "pass" else "1"
        tests = "1"
        xml = ("<testsuite tests=\"%s\" failures=\"%s\" errors=\"0\" skipped=\"0\">"
               "<properties>"
               "<property name=\"autodev.sourceRevision\" value=\"%s\"/>"
               "<property name=\"autodev.platform\" value=\"%s\"/>"
               "<property name=\"autodev.criterionId\" value=\"%s\"/>"
               "<property name=\"autodev.result\" value=\"%s\"/>"
               "</properties></testsuite>") % (
                   tests, failures, revision or self.head(), platform, criterion, result)
        path = self.artifacts() / name
        path.write_text(xml, encoding="utf-8")
        return self.relative_artifact(name)

    def add(self, criterion="main", kind="main", evidence_type="command", platforms=None,
            reason=None, run_id="run"):
        command = ["add", run_id, "--criterion-id", criterion, "--text", "contract %s" % criterion,
                   "--kind", kind, "--evidence-type", evidence_type]
        for platform in platforms or []:
            command.extend(["--platform", platform])
        if reason is not None:
            command.extend(["--reason", reason])
        return self.call(*command)

    def freeze(self, run_id="run"):
        return self.call("freeze", run_id)

    def record(self, result, evidence, criterion="main", platform="android", revision=None,
               phase=None, scenario=None, fingerprint=None, hypothesis=None, reread=None,
               run_id="run"):
        command = [result, run_id, "--criterion", criterion, "--platform", platform,
                   "--evidence", evidence, "--source-revision", revision or self.head()]
        if phase is not None:
            command.extend(["--phase", phase])
        if scenario is not None:
            command.extend(["--scenario-key", scenario])
        if fingerprint is not None:
            command.extend(["--scenario-fingerprint", fingerprint])
        if hypothesis is not None:
            command.extend(["--hypothesis", hypothesis])
        if reread is not None:
            command.extend(["--reread-reference", reread])
        return self.call(*command)

    def observe(self, evidence, criterion="repro", platform="android", scenario="repro-submit",
                outcome="not-reproduced", revision=None, run_id="run"):
        return self.call("observe", run_id, "--criterion", criterion, "--platform", platform,
                         "--scenario-key", scenario, "--outcome", outcome,
                         "--evidence", evidence, "--source-revision", revision or self.head())

    def add_freeze_pass(self, platforms=("android",), evidence_type="command", task_type="feature",
                        criterion="main"):
        self.init(task_type=task_type, platforms=list(platforms))
        self.add(criterion=criterion, evidence_type=evidence_type, platforms=list(platforms))
        self.freeze()
        if evidence_type == "junit":
            evidence = self.junit_evidence("proof.xml", criterion=criterion)
        else:
            evidence = self.command_evidence("proof.json", criterion=criterion)
        for platform in platforms:
            if evidence_type == "junit":
                evidence = self.junit_evidence("proof-%s.xml" % platform, criterion=criterion,
                                              platform=platform)
            else:
                evidence = self.command_evidence("proof-%s.json" % platform, criterion=criterion,
                                                 platform=platform)
            self.record("pass", evidence, criterion=criterion, platform=platform)

    def receipt(self, name, kind, revision=None, status="pass", platforms=None, **extra):
        value = {
            "schemaVersion": 1,
            "kind": kind,
            "sourceRevision": revision or self.head(),
            "timestamp": TIMESTAMP,
            "status": status,
        }
        if kind in ("build", "test"):
            value.update({"command": "./gradlew %s" % kind,
                          "exitCode": 0 if status == "pass" else 1,
                          "platforms": platforms or ["android"]})
        elif kind == "memory":
            if extra.pop("legacy", False):
                value.update({"read": ["AGENTS.md"], "written": [],
                              "lint": {"command": "./gradlew checkDocumentation",
                                       "status": "pass", "exitCode": 0}})
            else:
                return self.strict_memory_receipt(name, revision or self.head(), status=status, **extra)
        elif kind == "review":
            value.update({"independent": True, "reviewedRevision": revision or self.head(),
                          "unresolvedJustifiedFindings": 0,
                          "lateRegressionDigest": canonical_digest([])})
        elif kind == "cleanup":
            value.update({"residualScratch": [], "forbiddenHooks": [],
                          "secretScan": {"status": "pass", "findings": 0,
                                         "checkedPaths": [], "allowlistedPaths": [],
                                         "coverage": "fixture bounded scan"}})
        value.update(extra)
        return self.write_json(name, value)

    def strict_memory_receipt(self, name, revision, status="pass", **extra):
        if status != "pass":
            raise AssertionError("the v2 fixture models only a passing sealed memory receipt")
        entries = []
        reads = []
        for entry_id, path in (("memory.screen-navigation", "memory/screen-navigation.md"),
                               ("memory.lessons", "memory/lessons.md")):
            source = ".agents/skills/habit-lab-autodev/" + path
            # The fixture changes only source.txt after its initial commit, so
            # these tracked bytes are identical at every descendant revision.
            content = (self.root / source).read_bytes()
            digest = hashlib.sha256(content).hexdigest()
            entries.append({"entryId": entry_id, "path": path, "sha256": digest, "loadedAt": TIMESTAMP})
            reads.append({"path": path, "sha256": digest})
        ledger = {
            "schemaVersion": 1, "runId": "run", "createdAt": TIMESTAMP, "updatedAt": TIMESTAMP,
            "finalizedAt": TIMESTAMP, "initialEntryIds": ["memory.screen-navigation", "memory.lessons"],
            "plannedEntryIds": [], "loadedEntries": entries, "reads": reads, "writes": [],
            "durationSeconds": 0, "builds": [], "iterations": 0, "attempts": 0, "outcome": "success",
            "platforms": [], "flakySteps": [], "gateRun": None, "gateStatusSha256": None,
        }
        ledger_path = self.write_json("memory-ledger.json", ledger)
        ledger_digest = hashlib.sha256((self.artifacts() / "memory-ledger.json").read_bytes()).hexdigest()
        value = {
            "schemaVersion": 1, "kind": "memory", "sourceRevision": revision, "timestamp": TIMESTAMP,
            "status": "pass", "runId": "run", "ledger": ledger_path, "ledgerSha256": ledger_digest,
            "loaded": [{key: item[key] for key in ("entryId", "path", "sha256")}
                       for item in sorted(entries, key=lambda item: item["entryId"])],
            "lint": {"command": "python3 .agents/skills/habit-lab-autodev/scripts/autodev_memory.py lint",
                     "status": "pass", "exitCode": 0},
            "structureChanged": False, "evalReceipt": None, "instructionPatchCount": 0,
        }
        value.update(extra)
        return self.write_json(name, value)

    def legacy_memory_manifest(self, run_id="run"):
        """Model an already-sealed v1 run: migration must remain explicit."""
        manifest_path = self.state(run_id) / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest.pop("memoryReceiptContract")
        manifest_path.write_text(json.dumps(manifest, sort_keys=True), encoding="utf-8")
        (self.state(run_id) / "manifest.anchor").write_text(
            json.dumps({"manifestDigest": canonical_digest(manifest)}, sort_keys=True), encoding="utf-8")

    def successful_finish(self, platforms=("android",), late_digest=None, legacy_memory=False):
        revision = self.head()
        build = self.receipt("build.json", "build", revision, platforms=list(platforms))
        test = self.receipt("test.json", "test", revision, platforms=list(platforms))
        memory = self.receipt("memory.json", "memory", revision, legacy=legacy_memory)
        review_extra = {"lateRegressionDigest": late_digest or canonical_digest([])}
        review = self.receipt("review.json", "review", revision, **review_extra)
        cleanup = self.receipt("cleanup.json", "cleanup", revision)
        return self.finish_with_receipts(revision, build, test, memory, review, cleanup)

    def finish_with_receipts(self, revision, build, test, memory, review, cleanup, expected=0):
        return self.call("finish", "run", "--outcome", "success", "--source-revision", revision,
                         "--build-receipt", build, "--test-receipt", test,
                         "--memory-receipt", memory, "--review-receipt", review,
                         "--cleanup-receipt", cleanup, expected=expected)


class AutoDevGateTest(unittest.TestCase):
    def setUp(self):
        self.gate = GateHarness()

    def tearDown(self):
        self.gate.close()

    def test_init_is_exclusive_and_platform_selection_is_closed(self):
        default = self.gate.init("default")
        self.assertEqual(["android", "ios"], default["manifest"]["requestedPlatforms"])
        self.gate.expect_denied("init", "default", "--task-id", "DEN-19", "--task-type", "feature",
                                "--source-revision", self.gate.head(), "--blast-radius", "gate")
        explicit = self.gate.init("android-only", platforms=["android"])
        self.assertEqual(["android"], explicit["manifest"]["requestedPlatforms"])
        self.gate.expect_denied("init", "invalid/id", "--task-id", "DEN-19", "--task-type", "feature",
                                "--source-revision", self.gate.head(), "--blast-radius", "gate")

    def test_concurrent_init_has_one_owner_and_no_reused_run(self):
        command = [sys.executable, str(GATE), "init", "race", "--task-id", "DEN-19",
                   "--task-type", "feature", "--source-revision", self.gate.head(),
                   "--blast-radius", "gate"]
        first = subprocess.Popen(command, cwd=str(self.gate.root), text=True,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        second = subprocess.Popen(command, cwd=str(self.gate.root), text=True,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        results = [first.communicate(), second.communicate()]
        codes = [first.returncode, second.returncode]
        self.assertEqual(1, sum(code == 0 for code in codes))
        for code, (stdout, stderr) in zip(codes, results):
            payload = json.loads((stdout if code == 0 else stderr).strip())
            self.assertIsInstance(payload, dict)
        self.assertTrue((self.gate.state("race") / "owner.json").is_file())
        self.assertTrue((self.gate.artifacts("race")).is_dir())

    def test_manifest_and_freeze_tampering_are_fail_closed(self):
        for target in ("manifest", "core", "core-delete", "freeze", "anchor"):
            if target != "manifest":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.init(platforms=["android"])
            self.gate.add(platforms=["android"])
            self.gate.freeze()
            if target == "manifest":
                path = self.gate.state() / "manifest.json"
                path.write_bytes(b"{}")
            elif target == "core":
                path = self.gate.state() / "criteria/000001.json"
                path.write_bytes(b"{}")
            elif target == "core-delete":
                (self.gate.state() / "criteria/000001.json").unlink()
            elif target == "freeze":
                path = self.gate.state() / "freeze.json"
                path.write_bytes(b"{}")
            else:
                path = next((self.gate.state() / "anchors/criteria").iterdir())
                path.write_bytes(b"{}")
            self.gate.expect_denied("status", "run")

    def test_manifest_tamper_of_platform_and_source_is_rejected(self):
        for field, replacement in (("requestedPlatforms", ["android", "android"]),
                                   ("sourceRevision", "not-a-revision")):
            if field != "requestedPlatforms":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.init()
            manifest_path = self.gate.state() / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest[field] = replacement
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            self.gate.expect_denied("status", "run")

    def test_manifest_device_leases_are_immutable_acquired_none(self):
        self.gate.init()
        manifest_path = self.gate.state() / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual([], manifest["deviceLeases"])
        manifest["deviceLeases"] = ["unreleased-device"]
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        self.gate.expect_denied("status", "run")

    def test_reanchored_unsafe_manifest_fields_still_fail_schema_validation(self):
        for field, replacement in (("schemaVersion", 2),
                                   ("requestedPlatforms", ["android", "android"]),
                                   ("deviceLeases", ["unreleased"]),
                                   ("sourceRevision", "not-a-revision")):
            if field != "schemaVersion":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.init()
            manifest_path = self.gate.state() / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest[field] = replacement
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            (self.gate.state() / "manifest.anchor").write_text(
                json.dumps({"manifestDigest": canonical_digest(manifest)}), encoding="utf-8")
            self.gate.expect_denied("status", "run")

    def test_add_freeze_and_late_regressions_preserve_the_core(self):
        self.gate.init(platforms=["android"])
        self.gate.expect_denied("freeze", "run")
        self.gate.add()
        self.gate.freeze()
        self.gate.expect_denied("add", "run", "--criterion-id", "other", "--text", "other",
                                "--kind", "main", "--evidence-type", "command")
        self.gate.expect_denied("add", "run", "--criterion-id", "regression", "--text", "late",
                                "--kind", "regression", "--evidence-type", "command")
        late = self.gate.add("regression", kind="regression", reason="new regression")
        self.assertTrue(late["reviewInvalidated"])
        status = self.gate.call("status", "run")
        self.assertEqual(2, len(status["matrix"]))
        self.assertNotEqual(canonical_digest([]), status["lateRegressionDigest"])

    def test_evidence_paths_and_closed_structured_proofs_are_rejected(self):
        self.gate.init()
        self.gate.add(evidence_type="command", platforms=["android"])
        self.gate.freeze()
        bad = self.gate.artifacts() / "bad.json"
        bad.write_text("true", encoding="utf-8")
        paths = [
            "https://example.invalid/proof.json",
            "/tmp/proof.json",
            "../proof.json",
            "build/maestro/foreign/proof.json",
            ".autodev/artifacts/foreign/proof.json",
            self.gate.relative_artifact("bad.json"),
        ]
        for path in paths:
            self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", path, "--source-revision", self.gate.head())
        target = self.gate.artifacts() / "target.json"
        target.write_text("{}", encoding="utf-8")
        link = self.gate.artifacts() / "link.json"
        link.symlink_to(target.name)
        self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                "--evidence", self.gate.relative_artifact("link.json"),
                                "--source-revision", self.gate.head())

    def test_junit_and_command_semantics_and_status_revalidation(self):
        self.gate.init()
        self.gate.add(evidence_type="junit", platforms=["android"])
        self.gate.freeze()
        invalid = self.gate.junit_evidence("invalid.xml")
        (self.gate.artifacts() / "invalid.xml").write_text(
            "<testsuite tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\"/>",
            encoding="utf-8")
        self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                "--evidence", invalid, "--source-revision", self.gate.head())
        valid = self.gate.junit_evidence("valid.xml")
        self.gate.record("pass", valid)
        self.gate.call("status", "run")
        (self.gate.artifacts() / "valid.xml").write_text("<testsuite/>", encoding="utf-8")
        self.gate.expect_denied("status", "run")
        self.gate.close()
        self.gate = GateHarness()
        self.gate.init(platforms=["android"])
        self.gate.add(evidence_type="command", platforms=["android"])
        self.gate.freeze()
        invalid_command = self.gate.command_evidence("wrong-command.json")
        payload = json.loads((self.gate.artifacts() / "wrong-command.json").read_text(encoding="utf-8"))
        payload["exitCode"] = 1
        (self.gate.artifacts() / "wrong-command.json").write_text(json.dumps(payload), encoding="utf-8")
        self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                "--evidence", invalid_command, "--source-revision", self.gate.head())

    def test_recorded_evidence_detects_delete_growth_hash_and_content_changes(self):
        mutations = ("delete", "growth", "content")
        for mutation in mutations:
            if mutation != "delete":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.add_freeze_pass()
            proof = self.gate.artifacts() / "proof-android.json"
            if mutation == "delete":
                proof.unlink()
            elif mutation == "growth":
                proof.write_bytes(proof.read_bytes() + b" ")
            else:
                payload = json.loads(proof.read_text(encoding="utf-8"))
                payload["command"] = "different command"
                proof.write_text(json.dumps(payload), encoding="utf-8")
            self.gate.expect_denied("status", "run")
            self.gate.expect_denied("finish", "run", "--outcome", "blocked",
                                    "--source-revision", self.gate.head(), "--reason", "evidence changed")

    def test_missing_ios_cannot_be_promoted_to_success(self):
        self.gate.init()
        self.gate.add(evidence_type="command")
        self.gate.freeze()
        self.gate.record("pass", self.gate.command_evidence("android.json"))
        build = self.gate.receipt("build.json", "build", platforms=["android", "ios"])
        test = self.gate.receipt("test.json", "test", platforms=["android", "ios"])
        memory = self.gate.receipt("memory.json", "memory")
        review = self.gate.receipt("review.json", "review")
        cleanup = self.gate.receipt("cleanup.json", "cleanup")
        self.gate.expect_denied("finish", "run", "--outcome", "success", "--source-revision",
                                self.gate.head(), "--build-receipt", build, "--test-receipt", test,
                                "--memory-receipt", memory, "--review-receipt", review,
                                "--cleanup-receipt", cleanup)

    def test_success_requires_receipts_and_emits_complete_eligible_report(self):
        self.gate.add_freeze_pass()
        self.gate.expect_denied("finish", "run", "--outcome", "success", "--source-revision", self.gate.head())
        finished = self.gate.successful_finish()
        self.assertEqual("eligible", finished["draftPr"])
        report = (self.gate.artifacts() / "report.md").read_text(encoding="utf-8")
        for expected in ("Outcome: `success`", "Draft PR: eligible", "Checklist and evidence",
                         "Builds and tests", "Memory, review, cleanup, and devices"):
            self.assertIn(expected, report)

    def test_legacy_sealed_manifest_keeps_legacy_memory_receipt_coverage(self):
        self.gate.add_freeze_pass()
        self.gate.legacy_memory_manifest()
        finished = self.gate.successful_finish(legacy_memory=True)
        self.assertEqual("eligible", finished["draftPr"])
        self.assertIn("Memory: pass; 1 read, 0 written; lint pass",
                      (self.gate.artifacts() / "report.md").read_text(encoding="utf-8"))

    def test_v2_memory_receipt_rejects_empty_forged_bad_hash_and_overplanned_ledgers(self):
        cases = ("empty", "forged", "read-hash", "write-hash", "four-plan")
        for index, case in enumerate(cases):
            if index:
                self.gate.close()
                self.gate = GateHarness()
            self.gate.add_freeze_pass()
            revision = self.gate.head()
            build = self.gate.receipt("build.json", "build", revision)
            test = self.gate.receipt("test.json", "test", revision)
            memory = self.gate.receipt("memory.json", "memory", revision)
            review = self.gate.receipt("review.json", "review", revision)
            cleanup = self.gate.receipt("cleanup.json", "cleanup", revision)
            ledger_path = self.gate.artifacts() / "memory-ledger.json"
            receipt_path = self.gate.artifacts() / "memory.json"
            ledger = json.loads(ledger_path.read_text(encoding="utf-8"))
            receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
            if case == "empty":
                ledger["loadedEntries"] = []
            elif case == "forged":
                receipt["loaded"][0]["sha256"] = "0" * 64
            elif case == "read-hash":
                ledger["reads"][0]["sha256"] = "0" * 64
            elif case == "write-hash":
                ledger["writes"] = [{"path": "source.txt", "sha256": "0" * 64}]
            else:
                ledger["plannedEntryIds"] = [
                    "nav.daily-check-in.logic", "nav.daily-check-in.screen",
                    "nav.experiment-details.logic", "nav.experiment-details.screen",
                ]
            ledger_path.write_text(json.dumps(ledger, sort_keys=True), encoding="utf-8")
            receipt["ledgerSha256"] = hashlib.sha256(ledger_path.read_bytes()).hexdigest()
            receipt_path.write_text(json.dumps(receipt, sort_keys=True), encoding="utf-8")
            self.gate.finish_with_receipts(revision, build, test, memory, review, cleanup, expected=6)

    def test_receipt_schema_revision_coverage_review_and_cleanup_block_success(self):
        failures = ("prose", "stale", "coverage", "review", "cleanup")
        for failure in failures:
            if failure != "prose":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.add_freeze_pass()
            revision = self.gate.head()
            build = self.gate.receipt("build.json", "build", revision, platforms=["android"])
            test = self.gate.receipt("test.json", "test", revision, platforms=["android"])
            memory = self.gate.receipt("memory.json", "memory", revision)
            review = self.gate.receipt("review.json", "review", revision)
            cleanup = self.gate.receipt("cleanup.json", "cleanup", revision)
            if failure == "prose":
                (self.gate.artifacts() / "build.json").write_text("pass", encoding="utf-8")
            elif failure == "stale":
                stale = "0" * 40
                review = self.gate.receipt("review.json", "review", stale)
            elif failure == "coverage":
                self.gate.init("unused")
                # The feature run itself requests Android only, so alter its sealed manifest is invalid.
                # Coverage is exercised by a fresh default-platform lifecycle below.
                self.gate.close()
                self.gate = GateHarness()
                self.gate.init()
                self.gate.add(evidence_type="command")
                self.gate.freeze()
                for platform in ("android", "ios"):
                    self.gate.record("pass", self.gate.command_evidence("%s.json" % platform,
                                                                         platform=platform), platform=platform)
                revision = self.gate.head()
                build = self.gate.receipt("build.json", "build", revision, platforms=["android"])
                test = self.gate.receipt("test.json", "test", revision, platforms=["android"])
                memory = self.gate.receipt("memory.json", "memory", revision)
                review = self.gate.receipt("review.json", "review", revision)
                cleanup = self.gate.receipt("cleanup.json", "cleanup", revision)
            elif failure == "review":
                review = self.gate.receipt("review.json", "review", revision,
                                           unresolvedJustifiedFindings=1)
            else:
                cleanup = self.gate.receipt("cleanup.json", "cleanup", revision,
                                            residualScratch=["run-owned"])
            self.gate.expect_denied("finish", "run", "--outcome", "success", "--source-revision", revision,
                                    "--build-receipt", build, "--test-receipt", test,
                                    "--memory-receipt", memory, "--review-receipt", review,
                                    "--cleanup-receipt", cleanup)

    def test_scratch_hooks_and_known_secret_markers_block_success(self):
        cases = ("scratch", "hook", "secret")
        for case in cases:
            if case != "scratch":
                self.gate.close()
                self.gate = GateHarness()
            if case in ("hook", "secret"):
                self.gate.init(platforms=["android"])
                self.gate.add(evidence_type="command", platforms=["android"])
                self.gate.freeze()
                marker = "AUTODEV_DEBUG fixture" if case == "hook" else "ghp_" + ("A" * 30)
                proof = self.gate.command_evidence("proof-android.json", command=marker)
                self.gate.record("pass", proof)
            else:
                self.gate.add_freeze_pass()
            if case == "scratch":
                scratch = self.gate.state() / "scratch"
                scratch.mkdir()
                (scratch / "owned.tmp").write_text("left over", encoding="utf-8")
            # Hook and token-shaped input are recorded before finish so the deterministic
            # success-only scan, rather than hash revalidation, is the rejecting boundary.
            self.gate.expect_denied("finish", "run", "--outcome", "success", "--source-revision", self.gate.head(),
                                    "--build-receipt", self.gate.receipt("build.json", "build"),
                                    "--test-receipt", self.gate.receipt("test.json", "test"),
                                    "--memory-receipt", self.gate.receipt("memory.json", "memory"),
                                    "--review-receipt", self.gate.receipt("review.json", "review"),
                                    "--cleanup-receipt", self.gate.receipt("cleanup.json", "cleanup"))

    def test_retry_requires_fresh_reread_new_hypothesis_and_third_failure_is_partial(self):
        self.gate.init()
        self.gate.add(evidence_type="command", platforms=["android"])
        self.gate.freeze()
        for number in (1, 2):
            evidence = self.gate.command_evidence("fail-%s.json" % number, result="fail")
            self.gate.record("fail", evidence)
        third = self.gate.command_evidence("fail-3.json", result="fail")
        self.gate.expect_denied("fail", "run", "--criterion", "main", "--platform", "android",
                                "--evidence", third, "--source-revision", self.gate.head())
        time.sleep(0.02)
        reread = self.gate.write_json("reread.json", {
            "schemaVersion": 1, "kind": "reread-reference", "sourceRevision": self.gate.head(),
            "timestamp": TIMESTAMP, "platform": "android", "criterionId": "main",
            "hypothesis": "fresh cause", "reference": "new logs",
        })
        third_result = self.gate.record("fail", third, hypothesis="fresh cause", reread=reread)
        self.assertTrue(third_result["attempt"]["terminalPartial"])
        self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                "--evidence", self.gate.command_evidence("late-pass.json"),
                                "--source-revision", self.gate.head())
        self.gate.expect_denied("finish", "run", "--outcome", "failed", "--source-revision", self.gate.head(),
                                "--reason", "third failure")
        partial = self.gate.call("finish", "run", "--outcome", "partial", "--source-revision", self.gate.head(),
                                 "--reason", "third failure")
        self.assertEqual("partial", partial["outcome"])

    def test_prior_attempt_or_reread_tampering_blocks_new_append(self):
        for mutation in ("attempt-tamper", "attempt-delete"):
            if mutation != "attempt-tamper":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.init(platforms=["android"])
            self.gate.add(evidence_type="command", platforms=["android"])
            self.gate.freeze()
            failed = self.gate.command_evidence("failed.json", result="fail")
            self.gate.record("fail", failed)
            prior = self.gate.state() / "attempts/000001.json"
            if mutation == "attempt-tamper":
                prior.write_text("{}", encoding="utf-8")
            else:
                prior.unlink()
            count = self.gate.attempt_count()
            later = self.gate.command_evidence("later.json")
            self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", later, "--source-revision", self.gate.head())
            self.assertEqual(count, self.gate.attempt_count())
        for mutation in ("reread-tamper", "reread-delete"):
            self.gate.close()
            self.gate = GateHarness()
            self.gate.init(platforms=["android"])
            self.gate.add(evidence_type="command", platforms=["android"])
            self.gate.freeze()
            for number in (1, 2):
                self.gate.record("fail", self.gate.command_evidence("fail-%s.json" % number, result="fail"))
            time.sleep(0.02)
            reread = self.gate.write_json("reread.json", {
                "schemaVersion": 1, "kind": "reread-reference", "sourceRevision": self.gate.head(),
                "timestamp": TIMESTAMP, "platform": "android", "criterionId": "main",
                "hypothesis": "fresh cause", "reference": "new logs",
            })
            self.gate.record("pass", self.gate.command_evidence("third-pass.json"),
                             hypothesis="fresh cause", reread=reread)
            reread_path = self.gate.artifacts() / "reread.json"
            if mutation == "reread-tamper":
                reread_path.write_text("{}", encoding="utf-8")
            else:
                reread_path.unlink()
            count = self.gate.attempt_count()
            later = self.gate.command_evidence("later-fail.json", result="fail")
            self.gate.expect_denied("fail", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", later, "--source-revision", self.gate.head(),
                                    "--hypothesis", "new cause", "--reread-reference", reread)
            self.assertEqual(count, self.gate.attempt_count())

    def test_timestamp_contract_is_exact_utc_and_generated_values_are_valid(self):
        pattern = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z\Z")
        initialized = self.gate.init(platforms=["android"])
        self.assertIsNotNone(pattern.fullmatch(initialized["manifest"]["createdAt"]))
        self.gate.add(evidence_type="command", platforms=["android"])
        frozen = self.gate.freeze()
        self.assertIsNotNone(pattern.fullmatch(json.loads(
            (self.gate.state() / "freeze.json").read_text(encoding="utf-8"))["frozenAt"]))
        invalid_values = (
            "2026-09-05T12:00:00+00:00",
            "20260905T120000Z",
            "2026-09-05T12:00:00.1Z",
            "2026-09-05T12:00:00",
        )
        for index, value in enumerate(invalid_values):
            evidence = self.gate.command_evidence("bad-time-%s.json" % index)
            payload = json.loads((self.gate.artifacts() / ("bad-time-%s.json" % index)).read_text(encoding="utf-8"))
            payload["timestamp"] = value
            (self.gate.artifacts() / ("bad-time-%s.json" % index)).write_text(
                json.dumps(payload), encoding="utf-8")
            self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", evidence, "--source-revision", self.gate.head())
        proof = self.gate.command_evidence("proof.json")
        recorded = self.gate.record("pass", proof)
        self.assertIsNotNone(pattern.fullmatch(recorded["attempt"]["recordedAt"]))
        invalid_receipt = self.gate.receipt("build.json", "build", timestamp="2026-09-05T12:00:00+00:00")
        self.gate.expect_denied("finish", "run", "--outcome", "success", "--source-revision", self.gate.head(),
                                "--build-receipt", invalid_receipt,
                                "--test-receipt", self.gate.receipt("test.json", "test"),
                                "--memory-receipt", self.gate.receipt("memory.json", "memory"),
                                "--review-receipt", self.gate.receipt("review.json", "review"),
                                "--cleanup-receipt", self.gate.receipt("cleanup.json", "cleanup"))
        blocked = self.gate.call("finish", "run", "--outcome", "blocked", "--source-revision", self.gate.head(),
                                 "--reason", "timestamp fixture")
        self.assertEqual("blocked", blocked["outcome"])
        terminal = self.gate.call("status", "run")["terminal"]
        self.assertIsNotNone(pattern.fullmatch(terminal["finishedAt"]))

    def test_terminal_outcomes_are_write_barriers_with_readable_status(self):
        for outcome in ("blocked", "failed", "partial", "success"):
            if outcome != "blocked":
                self.gate.close()
                self.gate = GateHarness()
            if outcome == "success":
                self.gate.add_freeze_pass()
                self.gate.command_evidence("preterminal.json")
                self.gate.successful_finish()
            else:
                self.gate.init(platforms=["android"])
                self.gate.command_evidence("preterminal.json")
                self.gate.call("finish", "run", "--outcome", outcome,
                               "--source-revision", self.gate.head(), "--reason", "%s fixture" % outcome)
            before = self.gate.snapshot_run()
            terminal = self.gate.call("status", "run")["terminal"]
            self.assertEqual(outcome, terminal["outcome"])
            self.gate.expect_denied("add", "run", "--criterion-id", "late", "--text", "late",
                                    "--kind", "main", "--evidence-type", "command")
            self.gate.expect_denied("freeze", "run")
            evidence = self.gate.relative_artifact("preterminal.json")
            self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", evidence, "--source-revision", self.gate.head())
            self.gate.expect_denied("fail", "run", "--criterion", "main", "--platform", "android",
                                    "--evidence", evidence, "--source-revision", self.gate.head())
            self.gate.expect_denied("observe", "run", "--criterion", "main", "--platform", "android",
                                    "--scenario-key", "late", "--outcome", "not-reproduced",
                                    "--evidence", evidence, "--source-revision", self.gate.head())
            self.gate.expect_denied("finish", "run", "--outcome", "blocked",
                                    "--source-revision", self.gate.head(), "--reason", "again")
            self.assertEqual(before, self.gate.snapshot_run())

    def test_bug_baseline_and_fixed_evidence_must_match_initial_revision_and_scenario(self):
        initial = self.gate.head()
        self.gate.init(task_type="bug", platforms=["android"])
        self.gate.add(criterion="repro", kind="repro", evidence_type="command", platforms=["android"])
        self.gate.freeze()
        baseline = self.gate.command_evidence("baseline.json", criterion="repro", result="fail", revision=initial)
        self.gate.record("fail", baseline, criterion="repro", revision=initial, phase="baseline", scenario="save")
        count = self.gate.attempt_count()
        same_revision = self.gate.command_evidence("same-revision.json", criterion="repro", revision=initial)
        self.gate.expect_denied("pass", "run", "--criterion", "repro", "--platform", "android",
                                "--evidence", same_revision, "--source-revision", initial, "--phase", "fixed",
                                "--scenario-key", "save")
        self.assertEqual(count, self.gate.attempt_count())
        unrelated = self.gate.unrelated_revision()
        unrelated_fixed = self.gate.command_evidence("unrelated.json", criterion="repro", revision=unrelated)
        self.gate.expect_denied("pass", "run", "--criterion", "repro", "--platform", "android",
                                "--evidence", unrelated_fixed, "--source-revision", unrelated, "--phase", "fixed",
                                "--scenario-key", "save")
        self.assertEqual(count, self.gate.attempt_count())
        checked = self.gate.commit_source("fix")
        fixed = self.gate.command_evidence("fixed.json", criterion="repro", revision=checked)
        self.gate.expect_denied("pass", "run", "--criterion", "repro", "--platform", "android",
                                "--evidence", fixed, "--source-revision", checked, "--phase", "fixed",
                                "--scenario-key", "different")
        self.gate.record("pass", fixed, criterion="repro", revision=checked, phase="fixed", scenario="save")
        self.assertEqual("pass", self.gate.call("status", "run")["matrix"][0]["result"])
        fixed_path = self.gate.artifacts() / "fixed.json"
        legacy_payload = json.loads(fixed_path.read_text(encoding="utf-8"))
        legacy_payload["sourceRevision"] = initial
        fixed_path.write_text(json.dumps(legacy_payload), encoding="utf-8")
        fixed_event_path = self.gate.state() / "attempts/000002.json"
        fixed_event = json.loads(fixed_event_path.read_text(encoding="utf-8"))
        fixed_event["sourceRevision"] = initial
        fixed_event["evidence"]["size"] = fixed_path.stat().st_size
        fixed_event["evidence"]["sha256"] = hashlib.sha256(fixed_path.read_bytes()).hexdigest()
        fixed_event["evidence"]["modifiedNs"] = fixed_path.stat().st_mtime_ns
        unsigned = dict(fixed_event)
        unsigned.pop("digest")
        fixed_event["digest"] = canonical_digest(unsigned)
        fixed_event_path.write_text(json.dumps(fixed_event), encoding="utf-8")
        anchors = self.gate.state() / "anchors/attempts"
        old_anchor = next(anchors.glob("000002-*.anchor"))
        old_anchor.unlink()
        (anchors / ("000002-%s.anchor" % fixed_event["digest"])).write_text(
            json.dumps({"sequence": 2, "digest": fixed_event["digest"]}), encoding="utf-8")
        self.gate.expect_denied("status", "run")
        self.gate.expect_denied("finish", "run", "--outcome", "blocked",
                                "--source-revision", checked, "--reason", "legacy invalid")

    def test_bug_observations_are_diagnostic_immutable_and_reported(self):
        initial = self.gate.head()
        self.gate.init(task_type="bug", platforms=["android"])
        self.gate.add(criterion="repro", kind="repro", evidence_type="command", platforms=["android"])
        self.gate.freeze()
        invalid_time = self.gate.observation_evidence("bad-observation-time.json", revision=initial)
        invalid_time_path = self.gate.artifacts() / "bad-observation-time.json"
        invalid_time_data = json.loads(invalid_time_path.read_text(encoding="utf-8"))
        invalid_time_data["timestamp"] = "2026-09-05T12:00:00+00:00"
        invalid_time_path.write_text(json.dumps(invalid_time_data), encoding="utf-8")
        self.gate.expect_denied("observe", "run", "--criterion", "repro", "--platform", "android",
                                "--scenario-key", "repro-submit", "--outcome", "not-reproduced",
                                "--evidence", invalid_time, "--source-revision", initial)
        malformed = self.gate.observation_evidence("bad-observation.json", revision=initial)
        malformed_path = self.gate.artifacts() / "bad-observation.json"
        malformed_data = json.loads(malformed_path.read_text(encoding="utf-8"))
        malformed_data["exitCode"] = 1
        malformed_path.write_text(json.dumps(malformed_data), encoding="utf-8")
        self.gate.expect_denied("observe", "run", "--criterion", "repro", "--platform", "android",
                                "--scenario-key", "repro-submit", "--outcome", "not-reproduced",
                                "--evidence", malformed, "--source-revision", initial)
        self.assertEqual(0, self.gate.observation_count())

        observations = (
            ("not-reproduced", "selector was absent"),
            ("environment-blocked", "emulator service unavailable"),
            ("diagnostic-error", "instrumentation crashed"),
        )
        for index, (outcome, diagnostic) in enumerate(observations):
            evidence = self.gate.observation_evidence(
                "observation-%s.json" % index, revision=initial, outcome=outcome,
                diagnostic=diagnostic)
            recorded = self.gate.observe(evidence, outcome=outcome, revision=initial)
            self.assertEqual(outcome, recorded["observation"]["outcome"])
        status = self.gate.call("status", "run")
        self.assertEqual("missing", status["matrix"][0]["result"])
        self.assertEqual(3, len(status["observations"]))
        self.assertEqual("instrumentation crashed",
                         status["observations"][-1]["evidenceMetadata"]["diagnostic"])

        later = self.gate.commit_source("later diagnostic")
        later_evidence = self.gate.observation_evidence("later-observation.json", revision=later)
        self.gate.expect_denied("observe", "run", "--criterion", "repro", "--platform", "android",
                                "--scenario-key", "repro-submit", "--outcome", "not-reproduced",
                                "--evidence", later_evidence, "--source-revision", later)
        self.assertEqual(3, self.gate.observation_count())

        self.gate.call("finish", "run", "--outcome", "blocked", "--source-revision", later,
                       "--reason", "diagnostic observations only")
        report = (self.gate.artifacts() / "report.md").read_text(encoding="utf-8")
        self.assertIn("## Bug reproduction observations", report)
        self.assertIn("environment-blocked", report)
        self.assertIn('diagnostic: "instrumentation crashed"', report)
        recorded_path = self.gate.artifacts() / "observation-0.json"
        recorded_path.write_text("{}", encoding="utf-8")
        self.gate.expect_denied("status", "run")

    def test_bounded_scan_detects_growth_and_replacement_races(self):
        expected = {
            "growth": "scan input changed while reading",
            "content-change": "scan input changed while reading",
            "replacement-during-read": "scan input changed while reading",
            "replacement-after-final-stat": "structured artifact path was replaced while reading",
        }
        for index, (mode, message) in enumerate(sorted(expected.items())):
            if index:
                self.gate.close()
                self.gate = GateHarness()
            result = self.gate.bounded_scan_race(mode)
            self.assertIn(message, result["error"])

    def test_perf_computes_absolute_and_percent_directional_thresholds(self):
        cases = (
            ("increase", "absolute", 10.0, 13.0, 2.0, 3.0),
            ("decrease", "absolute", 10.0, 7.0, 2.0, 3.0),
            ("increase", "percent", 10.0, 13.0, 25.0, 30.0),
            ("decrease", "percent", 10.0, 7.0, 25.0, 30.0),
        )
        for index, (direction, unit, baseline_value, candidate_value, minimum, expected) in enumerate(cases):
            if index:
                self.gate.close()
                self.gate = GateHarness()
            initial = self.gate.head()
            self.gate.init(task_type="perf", platforms=["android"])
            self.gate.add(criterion="perf", evidence_type="metric", platforms=["android"])
            self.gate.freeze()
            baseline = self.gate.metric_evidence(
                "baseline.json", revision=initial, value=baseline_value, direction=direction,
                minimum_delta=minimum, delta_unit=unit)
            self.gate.record("pass", baseline, criterion="perf", revision=initial,
                             phase="baseline", fingerprint="fixture-v1")
            candidate_revision = self.gate.commit_source("candidate-%s-%s" % (direction, unit))
            candidate = self.gate.metric_evidence(
                "candidate.json", revision=candidate_revision, phase="candidate", value=candidate_value,
                direction=direction, minimum_delta=minimum, delta_unit=unit)
            recorded = self.gate.record("pass", candidate, criterion="perf", revision=candidate_revision,
                                        phase="candidate", fingerprint="fixture-v1")
            metadata = recorded["attempt"]["evidenceMetadata"]
            self.assertEqual(expected, metadata["computedDelta"])
            self.assertEqual("pass", metadata["computedResult"])
            status = self.gate.call("status", "run")
            self.assertEqual("pass", status["matrix"][0]["result"])
            self.assertEqual(expected, status["attempts"][-1]["evidenceMetadata"]["computedDelta"])

    def test_perf_repeat_and_success_report_carry_computed_comparison(self):
        initial = self.gate.head()
        self.gate.init(task_type="perf", platforms=["android"])
        self.gate.add(criterion="perf", evidence_type="metric", platforms=["android"])
        self.gate.freeze()
        baseline = self.gate.metric_evidence("baseline.json", revision=initial, value=10.0)
        baseline_sha256 = hashlib.sha256(
            (self.gate.artifacts() / "baseline.json").read_bytes()).hexdigest()
        self.gate.record("pass", baseline, criterion="perf", revision=initial,
                         phase="baseline", fingerprint="fixture-v1")
        candidate_revision = self.gate.commit_source("candidate")
        candidate = self.gate.metric_evidence("candidate.json", revision=candidate_revision,
                                               phase="candidate", value=7.0)
        self.gate.record("pass", candidate, criterion="perf", revision=candidate_revision,
                         phase="candidate", fingerprint="fixture-v1")
        repeat_revision = self.gate.commit_source("repeat")
        repeat = self.gate.metric_evidence("repeat.json", revision=repeat_revision,
                                            phase="repeat", value=6.0)
        self.gate.record("pass", repeat, criterion="perf", revision=repeat_revision,
                         phase="repeat", fingerprint="fixture-v1")
        finished = self.gate.successful_finish()
        self.assertEqual("eligible", finished["draftPr"])
        status = self.gate.call("status", "run")
        comparison = status["attempts"][-1]["evidenceMetadata"]
        self.assertEqual(4.0, comparison["computedDelta"])
        self.assertEqual("pass", comparison["computedResult"])
        self.assertEqual(baseline_sha256, comparison["baselineEvidenceSha256"])
        report = (self.gate.artifacts() / "report.md").read_text(encoding="utf-8")
        self.assertIn("computed decrease delta 4.0 absolute against minimum 2.0 (pass)", report)
        self.assertIn("baselineEvidenceSha256 `%s`" % baseline_sha256, report)

    def test_perf_rejects_invalid_schema_provenance_and_self_asserted_result(self):
        initial = self.gate.head()
        self.gate.init(task_type="perf", platforms=["android"])
        self.gate.add(criterion="perf", evidence_type="metric", platforms=["android"])
        self.gate.freeze()
        malformed = self.gate.metric_evidence("malformed.json", revision=initial)
        malformed_payload = json.loads((self.gate.artifacts() / "malformed.json").read_text(encoding="utf-8"))
        malformed_payload["value"] = float("nan")
        (self.gate.artifacts() / "malformed.json").write_text(json.dumps(malformed_payload), encoding="utf-8")
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", malformed, "--source-revision", initial, "--phase", "baseline",
                                "--scenario-fingerprint", "fixture-v1")
        baseline = self.gate.metric_evidence("baseline.json", revision=initial, value=10.0)
        self.gate.record("pass", baseline, criterion="perf", revision=initial,
                         phase="baseline", fingerprint="fixture-v1")
        same_revision = self.gate.metric_evidence("same.json", revision=initial, phase="candidate", value=7.0)
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", same_revision, "--source-revision", initial,
                                "--phase", "candidate", "--scenario-fingerprint", "fixture-v1")
        unrelated = self.gate.unrelated_revision()
        non_descendant = self.gate.metric_evidence("unrelated.json", revision=unrelated,
                                                    phase="candidate", value=7.0)
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", non_descendant, "--source-revision", unrelated,
                                "--phase", "candidate", "--scenario-fingerprint", "fixture-v1")
        candidate_revision = self.gate.commit_source("candidate")
        worse = self.gate.metric_evidence("worse.json", revision=candidate_revision,
                                          phase="candidate", value=12.0)
        before = self.gate.attempt_count()
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", worse, "--source-revision", candidate_revision,
                                "--phase", "candidate", "--scenario-fingerprint", "fixture-v1")
        self.assertEqual(before, self.gate.attempt_count())
        for name, values in (
                ("metricName", {"metric_name": "other-duration"}),
                ("unit", {"unit": "s"}),
                ("instrumentation", {"instrumentation": "trace-v2"}),
                ("aggregation", {"aggregation": "mean"}),
                ("sampleCount", {"sample_count": 6}),
                ("threshold", {"minimum_delta": 3.0}),
                ("fingerprint", {"fingerprint": "different"})):
            evidence = self.gate.metric_evidence("mismatch-%s.json" % name,
                                                  revision=candidate_revision, phase="candidate",
                                                  value=7.0, **values)
            self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                    "--evidence", evidence, "--source-revision", candidate_revision,
                                    "--phase", "candidate", "--scenario-fingerprint",
                                    values.get("fingerprint", "fixture-v1"))
        self.assertEqual(before, self.gate.attempt_count())

    def test_perf_rejects_zero_percent_baseline_and_nonfinite_threshold(self):
        initial = self.gate.head()
        self.gate.init(task_type="perf", platforms=["android"])
        self.gate.add(criterion="perf", evidence_type="metric", platforms=["android"])
        self.gate.freeze()
        zero = self.gate.metric_evidence("zero.json", revision=initial, value=0.0,
                                         minimum_delta=1.0, delta_unit="percent")
        self.gate.record("pass", zero, criterion="perf", revision=initial,
                         phase="baseline", fingerprint="fixture-v1")
        candidate_revision = self.gate.commit_source("candidate")
        candidate = self.gate.metric_evidence("candidate.json", revision=candidate_revision,
                                               phase="candidate", value=1.0,
                                               minimum_delta=1.0, delta_unit="percent")
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", candidate, "--source-revision", candidate_revision,
                                "--phase", "candidate", "--scenario-fingerprint", "fixture-v1")
        self.gate.close()
        self.gate = GateHarness()
        initial = self.gate.head()
        self.gate.init(task_type="perf", platforms=["android"])
        self.gate.add(criterion="perf", evidence_type="metric", platforms=["android"])
        self.gate.freeze()
        nonfinite = self.gate.metric_evidence("nonfinite.json", revision=initial,
                                              minimum_delta=float("inf"))
        self.gate.expect_denied("pass", "run", "--criterion", "perf", "--platform", "android",
                                "--evidence", nonfinite, "--source-revision", initial, "--phase", "baseline",
                                "--scenario-fingerprint", "fixture-v1")

    def test_non_success_reports_preserve_artifacts_and_reasons(self):
        for outcome in ("blocked", "failed"):
            if outcome != "blocked":
                self.gate.close()
                self.gate = GateHarness()
            self.gate.init()
            proof = self.gate.command_evidence("retained.json")
            result = self.gate.call("finish", "run", "--outcome", outcome, "--source-revision", self.gate.head(),
                                    "--reason", "%s fixture" % outcome)
            self.assertEqual(outcome, result["outcome"])
            self.assertTrue((self.gate.artifacts() / "retained.json").is_file())
            report = (self.gate.artifacts() / "report.md").read_text(encoding="utf-8")
            self.assertIn("Outcome: `%s`" % outcome, report)
            self.assertIn("Draft PR: not eligible", report)
            self.assertIn("%s fixture" % outcome, report)

    def test_terminal_report_receipt_and_state_mutation_are_revalidated(self):
        self.gate.add_freeze_pass()
        self.gate.successful_finish()
        (self.gate.artifacts() / "report.md").write_text("tampered", encoding="utf-8")
        self.gate.expect_denied("status", "run")
        self.gate.close()
        self.gate = GateHarness()
        self.gate.add_freeze_pass()
        self.gate.successful_finish()
        (self.gate.artifacts() / "receipts.json").write_text("{}", encoding="utf-8")
        self.gate.expect_denied("status", "run")
        self.gate.close()
        self.gate = GateHarness()
        self.gate.add_freeze_pass()
        self.gate.successful_finish()
        (self.gate.state() / "terminal.json").write_text("{}", encoding="utf-8")
        self.gate.expect_denied("status", "run")
        self.gate.close()
        self.gate = GateHarness()
        self.gate.init()
        (self.gate.state() / "owner.json").write_text("{", encoding="utf-8")
        self.gate.expect_denied("status", "run")

    def test_oversized_structured_artifacts_are_bounded_without_traceback(self):
        self.gate.init()
        self.gate.add(evidence_type="command", platforms=["android"])
        self.gate.freeze()
        oversized = self.gate.artifacts() / "oversized.json"
        with oversized.open("wb") as stream:
            stream.truncate(MAX_SCAN_BYTES + 1)
        payload = self.gate.expect_denied("pass", "run", "--criterion", "main", "--platform", "android",
                                          "--evidence", self.gate.relative_artifact("oversized.json"),
                                          "--source-revision", self.gate.head())
        self.assertIn("64 MiB", payload["error"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
