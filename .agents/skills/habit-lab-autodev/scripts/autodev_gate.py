#!/usr/bin/env python3
"""Fail-closed frozen-checklist and evidence gate for Habit Lab AutoDev."""

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import math
import os
import re
import secrets
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

try:
    import fcntl
except ImportError:  # pragma: no cover - intentionally unsupported host
    fcntl = None


SCHEMA_VERSION = 1
EXIT_USAGE = 2
EXIT_IO = 3
EXIT_VALIDATION = 4
EXIT_INTEGRITY = 5
EXIT_GATE = 6
EXIT_UNSUPPORTED = 7
RUN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}\Z")
ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")
REV_RE = re.compile(r"[0-9a-fA-F]{7,64}\Z")
PLATFORMS = ("android", "ios")
KINDS = ("main", "repro", "regression")
TASK_TYPES = ("feature", "bug", "perf")
OUTCOMES = ("success", "blocked", "failed", "partial")
PHASES = ("observation", "baseline", "fixed", "candidate", "repeat")
EVIDENCE_TYPES = ("junit", "command", "metric")
OBSERVATION_OUTCOMES = ("not-reproduced", "environment-blocked", "diagnostic-error")
MAX_SCAN_BYTES = 64 * 1024 * 1024


class GateError(Exception):
    def __init__(self, message: str, code: int = EXIT_VALIDATION) -> None:
        super().__init__(message)
        self.code = code


class JsonParser(argparse.ArgumentParser):
    def error(self, message: str) -> None:
        raise GateError(message, EXIT_USAGE)


def emit(payload: Dict[str, Any], stream: Any = sys.stdout) -> None:
    stream.write(json.dumps(payload, sort_keys=True, ensure_ascii=True) + "\n")


def now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def parse_timestamp(value: Any, label: str, code: int = EXIT_GATE) -> dt.datetime:
    if not isinstance(value, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", value):
        raise GateError("%s must use UTC YYYY-MM-DDTHH:MM:SSZ" % label, code)
    try:
        parsed = dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except ValueError:
        raise GateError("%s must use UTC YYYY-MM-DDTHH:MM:SSZ" % label, code)
    return parsed


def exact_keys(value: Dict[str, Any], expected: Sequence[str], label: str,
               code: int = EXIT_INTEGRITY) -> None:
    actual = set(value)
    required = set(expected)
    if actual != required:
        raise GateError("%s schema fields mismatch" % label, code)


def canonical_json(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def digest(value: Any) -> str:
    return hashlib.sha256(canonical_json(value)).hexdigest()


def validate_run_id(value: str) -> str:
    if not RUN_RE.fullmatch(value) or value in (".", "..") or "://" in value:
        raise GateError("run-id must be 1-64 safe ASCII characters")
    return value


def validate_id(value: str, label: str) -> str:
    if not ID_RE.fullmatch(value) or value in (".", ".."):
        raise GateError("%s is not a safe identifier" % label)
    return value


def parse_git_paths(raw: bytes, label: str) -> List[str]:
    """Decode NUL-delimited Git paths without quotePath-dependent parsing."""
    if not isinstance(raw, bytes) or (raw and not raw.endswith(b"\0")):
        raise GateError("%s Git path output is malformed" % label, EXIT_INTEGRITY)
    paths = []
    for encoded in (raw[:-1].split(b"\0") if raw else []):
        if not encoded:
            raise GateError("%s Git path output contains an empty path" % label, EXIT_INTEGRITY)
        try:
            path = encoded.decode("utf-8", "strict")
        except UnicodeDecodeError:
            raise GateError("%s Git path is not valid UTF-8" % label, EXIT_INTEGRITY)
        candidate = Path(path)
        if (candidate.is_absolute() or not candidate.parts or ".." in candidate.parts or
                "\\" in path or candidate.as_posix() != path):
            raise GateError("%s Git path is not a safe canonical relative path" % label, EXIT_INTEGRITY)
        paths.append(path)
    return paths


def parse_platforms(values: Optional[Sequence[str]]) -> List[str]:
    selected = list(values or PLATFORMS)
    if (not selected or any(not isinstance(item, str) for item in selected) or
            len(selected) != len(set(selected)) or any(item not in PLATFORMS for item in selected)):
        raise GateError("platforms must be unique values from android, ios")
    return sorted(selected)


def repo_root() -> Path:
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], text=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=False,
    )
    if result.returncode != 0:
        raise GateError("not inside a Git repository", EXIT_IO)
    root = Path(result.stdout.strip())
    if not root.is_absolute() or root.is_symlink() or not root.is_dir():
        raise GateError("repository root is unsafe", EXIT_INTEGRITY)
    return root.resolve()


def resolve_revision(root: Path, value: str) -> str:
    if not REV_RE.fullmatch(value):
        raise GateError("source revision must be a 7-64 character hexadecimal Git revision")
    result = subprocess.run(
        ["git", "rev-parse", "--verify", value + "^{commit}"], cwd=str(root), text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    revision = result.stdout.strip().lower()
    if result.returncode != 0 or not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise GateError("source revision does not resolve to a commit", EXIT_VALIDATION)
    return revision


def current_revision(root: Path) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", "HEAD"], cwd=str(root), text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    revision = result.stdout.strip().lower()
    if result.returncode != 0 or not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise GateError("current HEAD does not resolve to a commit", EXIT_GATE)
    return revision


def require_primitives() -> None:
    required = ("O_NOFOLLOW", "O_DIRECTORY", "O_EXCL")
    missing = [name for name in required if not hasattr(os, name)]
    dir_fd_functions = getattr(os, "supports_dir_fd", set())
    missing_dir_fd = [function.__name__ for function in (os.open, os.rename, os.unlink, os.stat)
                      if function not in dir_fd_functions]
    follow_functions = getattr(os, "supports_follow_symlinks", set())
    if (fcntl is None or not hasattr(fcntl, "flock") or missing or missing_dir_fd or
            os.stat not in follow_functions):
        raise GateError("host lacks required no-follow, exclusive-create, or locking primitives", EXIT_UNSUPPORTED)


def open_child_dir(parent_fd: int, name: str, create: bool = False,
                   require_owner: bool = True) -> int:
    if not name or name in (".", "..") or "/" in name or "\\" in name:
        raise GateError("unsafe child directory name", EXIT_INTEGRITY)
    if create:
        try:
            os.mkdir(name, 0o700, dir_fd=parent_fd)
        except FileExistsError:
            pass
    try:
        fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=parent_fd)
    except OSError as exc:
        raise GateError("cannot safely open runtime directory", EXIT_IO)
    info = os.fstat(fd)
    if (not stat.S_ISDIR(info.st_mode) or
            (require_owner and hasattr(os, "getuid") and info.st_uid != os.getuid())):
        os.close(fd)
        raise GateError("runtime directory identity or owner is invalid", EXIT_INTEGRITY)
    return fd


def verify_child_identity(parent_fd: int, name: str, child_fd: int) -> None:
    try:
        linked = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
    except OSError:
        raise GateError("runtime directory ancestor was removed or replaced", EXIT_INTEGRITY)
    opened = os.fstat(child_fd)
    if (linked.st_dev, linked.st_ino, linked.st_mode) != (opened.st_dev, opened.st_ino, opened.st_mode):
        raise GateError("runtime directory ancestor was removed or replaced", EXIT_INTEGRITY)


class RuntimeRoots:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.repo_chain: List[Tuple[int, Optional[int], Optional[str]]] = []
        filesystem_fd = os.open(os.path.sep, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        self.repo_chain.append((filesystem_fd, None, None))
        parent_fd = filesystem_fd
        relative_parts = root.parts[1:]
        for index, part in enumerate(relative_parts):
            child_fd = open_child_dir(parent_fd, part, require_owner=index == len(relative_parts) - 1)
            self.repo_chain.append((child_fd, parent_fd, part))
            parent_fd = child_fd
        self.repo_fd = parent_fd
        self.auto_fd = open_child_dir(self.repo_fd, ".autodev", create=True)
        self.state_fd = open_child_dir(self.auto_fd, "state", create=True)
        self.artifact_fd = open_child_dir(self.auto_fd, "artifacts", create=True)

    def __enter__(self) -> "RuntimeRoots":
        self.verify()
        return self

    def verify(self) -> None:
        for child_fd, parent_fd, name in self.repo_chain[1:]:
            verify_child_identity(parent_fd, name, child_fd)
        verify_child_identity(self.repo_fd, ".autodev", self.auto_fd)
        verify_child_identity(self.auto_fd, "state", self.state_fd)
        verify_child_identity(self.auto_fd, "artifacts", self.artifact_fd)

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> None:
        for fd in (self.artifact_fd, self.state_fd, self.auto_fd):
            os.close(fd)
        for fd, _, _ in reversed(self.repo_chain):
            os.close(fd)


def open_anchored(root: Any, parts: Sequence[str]) -> int:
    if not parts or any(part in ("", ".", "..") for part in parts):
        raise GateError("unsafe repository-relative path", EXIT_VALIDATION)
    directory_fd = os.dup(root) if isinstance(root, int) else os.open(
        str(root), os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in parts[:-1]:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW,
                              dir_fd=directory_fd)
            os.close(directory_fd)
            directory_fd = next_fd
        fd = os.open(parts[-1], os.O_RDONLY | os.O_NOFOLLOW, dir_fd=directory_fd)
    except OSError as exc:
        raise GateError("cannot safely open repository-relative file: %s" % exc, EXIT_IO)
    finally:
        os.close(directory_fd)
    info = os.fstat(fd)
    if not stat.S_ISREG(info.st_mode):
        os.close(fd)
        raise GateError("repository-relative path is not a regular file", EXIT_VALIDATION)
    return fd


def read_fd(fd: int) -> bytes:
    chunks = []
    while True:
        chunk = os.read(fd, 1024 * 1024)
        if not chunk:
            return b"".join(chunks)
        chunks.append(chunk)


def read_bytes_at(directory_fd: int, name: str) -> bytes:
    try:
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=directory_fd)
    except OSError as exc:
        raise GateError("cannot safely open runtime file", EXIT_IO)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode):
            raise GateError("runtime file is not regular", EXIT_INTEGRITY)
        return read_fd(fd)
    finally:
        os.close(fd)


def read_json_at(directory_fd: int, name: str, label: str) -> Dict[str, Any]:
    try:
        value = json.loads(read_bytes_at(directory_fd, name).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise GateError("%s contains invalid JSON" % label, EXIT_INTEGRITY)
    if not isinstance(value, dict):
        raise GateError("%s must be a JSON object" % label, EXIT_INTEGRITY)
    return value


def write_all(fd: int, data: bytes) -> None:
    offset = 0
    while offset < len(data):
        written = os.write(fd, data[offset:])
        if written <= 0:
            raise GateError("short filesystem write", EXIT_IO)
        offset += written


def exclusive_json_at(directory_fd: int, name: str, value: Dict[str, Any]) -> None:
    data = canonical_json(value) + b"\n"
    try:
        fd = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=directory_fd)
    except OSError:
        raise GateError("exclusive runtime write failed", EXIT_IO)
    try:
        write_all(fd, data)
        os.fsync(fd)
    finally:
        os.close(fd)
    os.fsync(directory_fd)


def atomic_write_at(directory_fd: int, name: str, data: bytes) -> None:
    temporary = ".%s.%s.tmp" % (name, secrets.token_hex(12))
    fd = -1
    try:
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=directory_fd)
        write_all(fd, data)
        os.fsync(fd)
        os.close(fd)
        fd = -1
        os.rename(temporary, name, src_dir_fd=directory_fd, dst_dir_fd=directory_fd)
        os.fsync(directory_fd)
    except Exception:
        if fd >= 0:
            os.close(fd)
        with contextlib.suppress(OSError):
            os.unlink(temporary, dir_fd=directory_fd)
        raise


def validate_manifest(value: Dict[str, Any], run_id: str) -> Dict[str, Any]:
    legacy_fields = ("schemaVersion", "runId", "taskId", "taskType", "sourceRevision",
                     "requestedPlatforms", "blastRadius", "createdAt", "deviceLeases")
    current_fields = legacy_fields + ("memoryReceiptContract",)
    if set(value) not in (set(legacy_fields), set(current_fields)):
        raise GateError("manifest schema fields mismatch", EXIT_INTEGRITY)
    if type(value["schemaVersion"]) is not int or value["schemaVersion"] != SCHEMA_VERSION:
        raise GateError("manifest schemaVersion is invalid", EXIT_INTEGRITY)
    if value["runId"] != run_id:
        raise GateError("manifest runId is invalid", EXIT_INTEGRITY)
    try:
        validate_id(value["taskId"], "manifest taskId")
    except (GateError, TypeError):
        raise GateError("manifest taskId is invalid", EXIT_INTEGRITY)
    if value["taskType"] not in TASK_TYPES:
        raise GateError("manifest taskType is invalid", EXIT_INTEGRITY)
    if not isinstance(value["sourceRevision"], str) or not re.fullmatch(r"[0-9a-f]{40,64}", value["sourceRevision"]):
        raise GateError("manifest sourceRevision is invalid", EXIT_INTEGRITY)
    platforms = value["requestedPlatforms"]
    if (not isinstance(platforms, list) or not platforms or
            any(not isinstance(item, str) for item in platforms) or platforms != sorted(platforms) or
            len(platforms) != len(set(platforms)) or any(item not in PLATFORMS for item in platforms)):
        raise GateError("manifest requestedPlatforms is invalid", EXIT_INTEGRITY)
    blast = value["blastRadius"]
    if not isinstance(blast, list) or not blast or any(not isinstance(item, str) or not item.strip() for item in blast):
        raise GateError("manifest blastRadius is invalid", EXIT_INTEGRITY)
    parse_timestamp(value["createdAt"], "manifest createdAt", EXIT_INTEGRITY)
    if value["deviceLeases"] != []:
        raise GateError("manifest deviceLeases must be the immutable acquired-none state", EXIT_INTEGRITY)
    if "memoryReceiptContract" in value and value["memoryReceiptContract"] != 2:
        raise GateError("manifest memory receipt contract is invalid", EXIT_INTEGRITY)
    return value


def validate_artifact_record(value: Any, label: str) -> None:
    if not isinstance(value, dict):
        raise GateError("%s artifact record is invalid" % label, EXIT_INTEGRITY)
    exact_keys(value, ("path", "size", "sha256", "modifiedNs"), "%s artifact" % label)
    if (not isinstance(value["path"], str) or type(value["size"]) is not int or value["size"] < 0 or
            not isinstance(value["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", value["sha256"]) or
            type(value["modifiedNs"]) is not int or value["modifiedNs"] < 0):
        raise GateError("%s artifact record fields are invalid" % label, EXIT_INTEGRITY)


def validate_event(folder: str, event: Dict[str, Any]) -> None:
    common = ("sequence", "previousDigest", "digest")
    if folder in ("criteria", "late-regressions"):
        exact_keys(event, common + ("criterionId", "text", "kind", "requiredPlatforms",
                   "evidenceType", "reason", "createdAt"), "%s event" % folder)
        try:
            validate_id(event["criterionId"], "criterionId")
        except (GateError, TypeError):
            raise GateError("criterion event id is invalid", EXIT_INTEGRITY)
        platforms = event["requiredPlatforms"]
        if (not isinstance(event["text"], str) or not event["text"].strip() or
                event["kind"] not in KINDS or event["evidenceType"] not in EVIDENCE_TYPES or
                not isinstance(platforms, list) or not platforms or
                any(not isinstance(item, str) for item in platforms) or platforms != sorted(platforms) or
                len(platforms) != len(set(platforms)) or any(item not in PLATFORMS for item in platforms)):
            raise GateError("criterion event fields are invalid", EXIT_INTEGRITY)
        if folder == "late-regressions":
            if event["kind"] != "regression" or not isinstance(event["reason"], str) or not event["reason"].strip():
                raise GateError("late regression event is invalid", EXIT_INTEGRITY)
        elif event["reason"] is not None:
            raise GateError("core criterion reason must be null", EXIT_INTEGRITY)
        parse_timestamp(event["createdAt"], "criterion createdAt", EXIT_INTEGRITY)
    elif folder == "attempts":
        exact_keys(event, common + ("criterionId", "platform", "result", "phase", "scenarioKey",
                   "scenarioFingerprint", "evidence", "evidenceType", "evidenceMetadata",
                   "hypothesis", "rereadReference", "rereadMetadata", "sourceRevision",
                   "recordedAt", "checklistDigest", "terminalPartial"), "attempt event")
        validate_artifact_record(event["evidence"], "evidence")
        try:
            validate_id(event["criterionId"], "attempt criterionId")
        except (GateError, TypeError):
            raise GateError("attempt criterionId is invalid", EXIT_INTEGRITY)
        if event["rereadReference"] is not None:
            validate_artifact_record(event["rereadReference"], "reread")
        if (event["platform"] not in PLATFORMS or event["result"] not in ("pass", "fail") or
                event["phase"] not in PHASES or event["evidenceType"] not in EVIDENCE_TYPES or
                not isinstance(event["evidenceMetadata"], dict) or
                (event["rereadReference"] is None) != (event["rereadMetadata"] is None) or
                (event["rereadMetadata"] is not None and not isinstance(event["rereadMetadata"], dict)) or
                not isinstance(event["sourceRevision"], str) or
                not re.fullmatch(r"[0-9a-f]{40,64}", event["sourceRevision"]) or
                not isinstance(event["checklistDigest"], str) or
                not re.fullmatch(r"[0-9a-f]{64}", event["checklistDigest"]) or
                type(event["terminalPartial"]) is not bool):
            raise GateError("attempt event fields are invalid", EXIT_INTEGRITY)
        for field in ("scenarioKey", "scenarioFingerprint", "hypothesis"):
            if event[field] is not None and (not isinstance(event[field], str) or not event[field].strip()):
                raise GateError("attempt optional field is invalid", EXIT_INTEGRITY)
        parse_timestamp(event["recordedAt"], "attempt recordedAt", EXIT_INTEGRITY)
    elif folder == "observations":
        exact_keys(event, common + ("criterionId", "platform", "scenarioKey", "outcome", "evidence",
                   "evidenceMetadata", "sourceRevision", "recordedAt", "checklistDigest"),
                   "observation event")
        validate_artifact_record(event["evidence"], "observation evidence")
        try:
            validate_id(event["criterionId"], "observation criterionId")
        except (GateError, TypeError):
            raise GateError("observation criterionId is invalid", EXIT_INTEGRITY)
        if (event["platform"] not in PLATFORMS or
                event["outcome"] not in OBSERVATION_OUTCOMES or
                not isinstance(event["scenarioKey"], str) or not event["scenarioKey"].strip() or
                not isinstance(event["evidenceMetadata"], dict) or
                not isinstance(event["sourceRevision"], str) or
                not re.fullmatch(r"[0-9a-f]{40,64}", event["sourceRevision"]) or
                not isinstance(event["checklistDigest"], str) or
                not re.fullmatch(r"[0-9a-f]{64}", event["checklistDigest"])):
            raise GateError("observation event fields are invalid", EXIT_INTEGRITY)
        parse_timestamp(event["recordedAt"], "observation recordedAt", EXIT_INTEGRITY)
    else:
        raise GateError("unknown state event directory", EXIT_INTEGRITY)
    if (type(event["sequence"]) is not int or event["sequence"] < 1 or
            not isinstance(event["previousDigest"], str) or
            not re.fullmatch(r"[0-9a-f]{64}", event["previousDigest"]) or
            not isinstance(event["digest"], str) or not re.fullmatch(r"[0-9a-f]{64}", event["digest"])):
        raise GateError("event chain fields are invalid", EXIT_INTEGRITY)


class Run:
    def __init__(self, root: Path, roots: RuntimeRoots, run_id: str) -> None:
        self.root = root
        self.repo_fd = roots.repo_fd
        self.state_root_fd = roots.state_fd
        self.artifact_root_fd = roots.artifact_fd
        self.run_id = validate_run_id(run_id)
        self.state = root / ".autodev" / "state" / run_id
        self.artifacts = root / ".autodev" / "artifacts" / run_id
        self.state_fd = open_child_dir(roots.state_fd, run_id)
        try:
            self.artifact_fd = open_child_dir(roots.artifact_fd, run_id)
            self.anchors_fd = open_child_dir(self.state_fd, "anchors")
        except Exception:
            os.close(self.state_fd)
            raise
        self.lock_fd: Optional[int] = None

    def __enter__(self) -> "Run":
        try:
            self.lock_fd = os.open(".lock", os.O_RDWR | os.O_NOFOLLOW, dir_fd=self.state_fd)
        except FileNotFoundError:
            raise GateError("run lock was deleted", EXIT_INTEGRITY)
        except OSError:
            raise GateError("cannot open run lock", EXIT_IO)
        lock_info = os.fstat(self.lock_fd)
        if (not stat.S_ISREG(lock_info.st_mode) or
                (hasattr(os, "getuid") and lock_info.st_uid != os.getuid())):
            os.close(self.lock_fd)
            self.lock_fd = None
            raise GateError("run lock identity or owner is invalid", EXIT_INTEGRITY)
        fcntl.flock(self.lock_fd, fcntl.LOCK_EX)
        self.verify_paths()
        self.verify_owner()
        return self

    def verify_paths(self) -> None:
        verify_child_identity(self.state_root_fd, self.run_id, self.state_fd)
        verify_child_identity(self.artifact_root_fd, self.run_id, self.artifact_fd)
        verify_child_identity(self.state_fd, "anchors", self.anchors_fd)

    def __exit__(self, exc_type: Any, exc: Any, tb: Any) -> None:
        if self.lock_fd is not None:
            fcntl.flock(self.lock_fd, fcntl.LOCK_UN)
            os.close(self.lock_fd)
        os.close(self.anchors_fd)
        os.close(self.artifact_fd)
        os.close(self.state_fd)

    def verify_owner(self) -> None:
        try:
            owner = read_json_at(self.state_fd, "owner.json", "run owner")
        except GateError:
            raise GateError("run owner metadata is missing or invalid", EXIT_INTEGRITY)
        exact_keys(owner, ("schemaVersion", "runId", "stateDevice", "stateInode",
                           "artifactDevice", "artifactInode"), "run owner")
        info = os.fstat(self.state_fd)
        artifact_info = os.fstat(self.artifact_fd)
        expected = {
            "stateDevice": info.st_dev,
            "stateInode": info.st_ino,
            "artifactDevice": artifact_info.st_dev,
            "artifactInode": artifact_info.st_ino,
        }
        for key, value in expected.items():
            if owner.get(key) != value:
                raise GateError("run ownership check failed: %s" % key, EXIT_INTEGRITY)
        integer_fields = ("schemaVersion", "stateDevice", "stateInode", "artifactDevice", "artifactInode")
        if any(type(owner[field]) is not int for field in integer_fields):
            raise GateError("run owner field types are invalid", EXIT_INTEGRITY)
        if owner.get("runId") != self.run_id or owner.get("schemaVersion") != SCHEMA_VERSION:
            raise GateError("run owner metadata mismatch", EXIT_INTEGRITY)

    @property
    def manifest(self) -> Dict[str, Any]:
        names = set(os.listdir(self.state_fd))
        if ("manifest.json" in names) != ("manifest.anchor" in names) or "manifest.json" not in names:
            raise GateError("manifest or its immutable anchor was deleted", EXIT_INTEGRITY)
        manifest = read_json_at(self.state_fd, "manifest.json", "manifest")
        anchor = read_json_at(self.state_fd, "manifest.anchor", "manifest anchor")
        exact_keys(anchor, ("manifestDigest",), "manifest anchor")
        if anchor["manifestDigest"] != digest(manifest):
            raise GateError("manifest does not match its immutable anchor", EXIT_INTEGRITY)
        return validate_manifest(manifest, self.run_id)

    def events(self, folder: str) -> List[Dict[str, Any]]:
        directory_fd = open_child_dir(self.state_fd, folder)
        anchor_fd = open_child_dir(self.anchors_fd, folder)
        try:
            names = sorted(os.listdir(directory_fd))
            expected = ["%06d.json" % number for number in range(1, len(names) + 1)]
            if names != expected:
                raise GateError("%s event sequence was mutated or deleted" % folder, EXIT_INTEGRITY)
            events: List[Dict[str, Any]] = []
            previous = "0" * 64
            for index, name in enumerate(names, 1):
                event = read_json_at(directory_fd, name, "%s event" % folder)
                validate_event(folder, event)
                if event["sequence"] != index or event["previousDigest"] != previous:
                    raise GateError("%s integrity chain is invalid" % folder, EXIT_INTEGRITY)
                event_digest = event["digest"]
                unsigned = dict(event)
                unsigned.pop("digest")
                if event_digest != digest(unsigned):
                    raise GateError("%s event digest mismatch" % folder, EXIT_INTEGRITY)
                previous = event_digest
                events.append(event)
            anchor_names = sorted(os.listdir(anchor_fd))
            expected_anchors = ["%06d-%s.anchor" % (item["sequence"], item["digest"])
                                for item in events]
            if anchor_names != expected_anchors:
                raise GateError("%s append anchors show mutation or deletion" % folder, EXIT_INTEGRITY)
            for event, anchor_name in zip(events, anchor_names):
                anchor = read_json_at(anchor_fd, anchor_name, "%s event anchor" % folder)
                exact_keys(anchor, ("sequence", "digest"), "%s event anchor" % folder)
                if (type(anchor["sequence"]) is not int or anchor["sequence"] != event["sequence"] or
                        anchor["digest"] != event["digest"]):
                    raise GateError("%s event anchor payload is invalid" % folder, EXIT_INTEGRITY)
            verify_child_identity(self.state_fd, folder, directory_fd)
            verify_child_identity(self.anchors_fd, folder, anchor_fd)
            return events
        finally:
            os.close(directory_fd)
            os.close(anchor_fd)

    def append_event(self, folder: str, body: Dict[str, Any]) -> Dict[str, Any]:
        events = self.events(folder)
        event = dict(body)
        event.update({
            "sequence": len(events) + 1,
            "previousDigest": events[-1]["digest"] if events else "0" * 64,
        })
        event["digest"] = digest(event)
        validate_event(folder, event)
        directory_fd = open_child_dir(self.state_fd, folder)
        anchor_fd = open_child_dir(self.anchors_fd, folder)
        try:
            exclusive_json_at(directory_fd, "%06d.json" % event["sequence"], event)
            exclusive_json_at(anchor_fd, "%06d-%s.anchor" % (
                event["sequence"], event["digest"]),
                {"sequence": event["sequence"], "digest": event["digest"]})
            verify_child_identity(self.state_fd, folder, directory_fd)
            verify_child_identity(self.anchors_fd, folder, anchor_fd)
        finally:
            os.close(directory_fd)
            os.close(anchor_fd)
        persisted = self.events(folder)
        if not persisted or persisted[-1] != event:
            raise GateError("appended event did not remain integrity-valid", EXIT_INTEGRITY)
        return event

    def frozen(self) -> Optional[Dict[str, Any]]:
        names = set(os.listdir(self.state_fd))
        has_record = "freeze.json" in names
        has_anchor = "freeze.anchor" in names
        if has_record != has_anchor:
            raise GateError("freeze record or its anchor was deleted", EXIT_INTEGRITY)
        if not has_record:
            return None
        frozen = read_json_at(self.state_fd, "freeze.json", "freeze record")
        anchor = read_json_at(self.state_fd, "freeze.anchor", "freeze anchor")
        exact_keys(anchor, ("freezeDigest",), "freeze anchor")
        if anchor.get("freezeDigest") != digest(frozen):
            raise GateError("freeze record does not match its anchor", EXIT_INTEGRITY)
        exact_keys(frozen, ("schemaVersion", "runId", "frozenAt", "criteria", "checklistDigest"),
                   "freeze record")
        if (type(frozen["schemaVersion"]) is not int or frozen["schemaVersion"] != SCHEMA_VERSION or
                frozen["runId"] != self.run_id or not isinstance(frozen["criteria"], list) or
                not isinstance(frozen["checklistDigest"], str) or
                not re.fullmatch(r"[0-9a-f]{64}", frozen["checklistDigest"])):
            raise GateError("freeze record fields are invalid", EXIT_INTEGRITY)
        parse_timestamp(frozen["frozenAt"], "freeze timestamp", EXIT_INTEGRITY)
        for item in frozen["criteria"]:
            if not isinstance(item, dict):
                raise GateError("freeze criterion snapshot is invalid", EXIT_INTEGRITY)
            exact_keys(item, ("criterionId", "text", "kind", "requiredPlatforms",
                              "evidenceType", "digest"), "freeze criterion")
        return frozen

    def terminal(self) -> Optional[Dict[str, Any]]:
        names = set(os.listdir(self.state_fd))
        has_record = "terminal.json" in names
        has_anchor = "terminal.anchor" in names
        if has_record != has_anchor:
            raise GateError("terminal record or its anchor was deleted", EXIT_INTEGRITY)
        if not has_record:
            return None
        terminal = read_json_at(self.state_fd, "terminal.json", "terminal record")
        anchor = read_json_at(self.state_fd, "terminal.anchor", "terminal anchor")
        exact_keys(anchor, ("terminalDigest",), "terminal anchor")
        if anchor.get("terminalDigest") != digest(terminal):
            raise GateError("terminal record does not match its anchor", EXIT_INTEGRITY)
        exact_keys(terminal, ("schemaVersion", "runId", "outcome", "reason", "checkedRevision",
                              "finishedAt", "reportSha256", "receiptIndexSha256"), "terminal record")
        if (type(terminal["schemaVersion"]) is not int or terminal["schemaVersion"] != SCHEMA_VERSION or
                terminal["runId"] != self.run_id or terminal["outcome"] not in OUTCOMES or
                not isinstance(terminal["checkedRevision"], str) or
                not re.fullmatch(r"[0-9a-f]{40,64}", terminal["checkedRevision"]) or
                not isinstance(terminal["reportSha256"], str) or
                not re.fullmatch(r"[0-9a-f]{64}", terminal["reportSha256"]) or
                (terminal["receiptIndexSha256"] is not None and
                 (not isinstance(terminal["receiptIndexSha256"], str) or
                  not re.fullmatch(r"[0-9a-f]{64}", terminal["receiptIndexSha256"]))) or
                (terminal["reason"] is not None and not isinstance(terminal["reason"], str))):
            raise GateError("terminal record fields are invalid", EXIT_INTEGRITY)
        parse_timestamp(terminal["finishedAt"], "terminal timestamp", EXIT_INTEGRITY)
        return terminal


def init_run(args: argparse.Namespace, root: Path, roots: RuntimeRoots) -> Dict[str, Any]:
    validate_run_id(args.run_id)
    validate_id(args.task_id, "task-id")
    source_revision = resolve_revision(root, args.source_revision)
    platforms = parse_platforms(args.platform)
    if not args.blast_radius or any(not item.strip() for item in args.blast_radius):
        raise GateError("at least one non-empty --blast-radius is required")
    try:
        os.mkdir(args.run_id, 0o700, dir_fd=roots.state_fd)
    except FileExistsError:
        raise GateError("run already exists", EXIT_VALIDATION)
    try:
        os.mkdir(args.run_id, 0o700, dir_fd=roots.artifact_fd)
    except Exception:
        raise
    state_fd = open_child_dir(roots.state_fd, args.run_id)
    artifact_fd = open_child_dir(roots.artifact_fd, args.run_id)
    state_info = os.fstat(state_fd)
    artifact_info = os.fstat(artifact_fd)
    owner = {
        "schemaVersion": SCHEMA_VERSION, "runId": args.run_id,
        "stateDevice": state_info.st_dev, "stateInode": state_info.st_ino,
        "artifactDevice": artifact_info.st_dev, "artifactInode": artifact_info.st_ino,
    }
    exclusive_json_at(state_fd, "owner.json", owner)
    lock_fd = os.open(".lock", os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                      0o600, dir_fd=state_fd)
    os.close(lock_fd)
    os.mkdir("anchors", 0o700, dir_fd=state_fd)
    anchors_fd = open_child_dir(state_fd, "anchors")
    for folder in ("criteria", "late-regressions", "attempts", "observations"):
        os.mkdir(folder, 0o700, dir_fd=state_fd)
        os.mkdir(folder, 0o700, dir_fd=anchors_fd)
    manifest = {
        "schemaVersion": SCHEMA_VERSION, "runId": args.run_id, "taskId": args.task_id,
        "taskType": args.task_type, "sourceRevision": source_revision,
        "requestedPlatforms": platforms, "blastRadius": [item.strip() for item in args.blast_radius],
        "createdAt": now(), "deviceLeases": [], "memoryReceiptContract": 2,
    }
    exclusive_json_at(state_fd, "manifest.json", manifest)
    exclusive_json_at(state_fd, "manifest.anchor", {"manifestDigest": digest(manifest)})
    verify_child_identity(roots.state_fd, args.run_id, state_fd)
    verify_child_identity(roots.artifact_fd, args.run_id, artifact_fd)
    os.close(anchors_fd)
    os.close(artifact_fd)
    os.close(state_fd)
    return {"ok": True, "command": "init", "runId": args.run_id, "manifest": manifest}


def criteria(run: Run) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    return run.events("criteria"), run.events("late-regressions")


def add_criterion(args: argparse.Namespace, run: Run) -> Dict[str, Any]:
    validate_id(args.criterion_id, "criterion-id")
    if not args.text.strip():
        raise GateError("criterion text must not be empty")
    if not args.evidence_type.strip():
        raise GateError("evidence-type must not be empty")
    manifest = run.manifest
    if (manifest["taskType"] == "perf") != (args.evidence_type == "metric"):
        raise GateError("performance criteria require metric evidence; other tasks forbid it")
    selected = parse_platforms(args.platform or manifest["requestedPlatforms"])
    if not set(selected).issubset(set(manifest["requestedPlatforms"])):
        raise GateError("criterion platform is outside the run scope")
    core, late = criteria(run)
    if any(item["criterionId"] == args.criterion_id for item in core + late):
        raise GateError("criterion id already exists")
    frozen = run.frozen()
    if frozen:
        if args.kind != "regression" or not args.reason or not args.reason.strip():
            raise GateError("after freeze only a new regression with --reason may be appended")
        folder = "late-regressions"
    else:
        if args.reason:
            raise GateError("--reason is reserved for late regressions")
        folder = "criteria"
    event = run.append_event(folder, {
        "criterionId": args.criterion_id, "text": args.text.strip(), "kind": args.kind,
        "requiredPlatforms": selected, "evidenceType": args.evidence_type.strip(),
        "reason": args.reason.strip() if args.reason else None, "createdAt": now(),
    })
    return {"ok": True, "command": "add", "runId": run.run_id, "criterion": event,
            "reviewInvalidated": bool(frozen)}


def freeze(args: argparse.Namespace, run: Run) -> Dict[str, Any]:
    if run.frozen():
        raise GateError("checklist is already frozen")
    core, late = criteria(run)
    if late:
        raise GateError("late regression events exist before freeze", EXIT_INTEGRITY)
    if not core:
        raise GateError("cannot freeze an empty checklist")
    manifest = run.manifest
    for item in core:
        required = ("criterionId", "text", "kind", "requiredPlatforms", "evidenceType")
        if any(not item.get(key) for key in required):
            raise GateError("criterion is incomplete: %s" % item.get("criterionId"))
        if not set(item["requiredPlatforms"]).issubset(set(manifest["requestedPlatforms"])):
            raise GateError("criterion platform escaped run scope", EXIT_INTEGRITY)
    snapshot = [{key: item[key] for key in (
        "criterionId", "text", "kind", "requiredPlatforms", "evidenceType", "digest"
    )} for item in core]
    frozen = {
        "schemaVersion": SCHEMA_VERSION, "runId": run.run_id, "frozenAt": now(),
        "criteria": snapshot, "checklistDigest": digest(snapshot),
    }
    exclusive_json_at(run.state_fd, "freeze.json", frozen)
    exclusive_json_at(run.state_fd, "freeze.anchor", {"freezeDigest": digest(frozen)})
    verify_freeze(run)
    return {"ok": True, "command": "freeze", "runId": run.run_id,
            "checklistDigest": frozen["checklistDigest"], "criteriaCount": len(core)}


def artifact_record(root: Path, run: Run, raw: str) -> Dict[str, Any]:
    candidate = Path(raw)
    if (candidate.is_absolute() or ".." in candidate.parts or not candidate.parts or
            "://" in raw):
        raise GateError("artifact path must be repository-relative without traversal")
    parts = candidate.parts
    allowed = ((".autodev", "artifacts", run.run_id),
               ("build", "maestro", run.run_id))
    if not any(len(parts) > len(prefix) and tuple(parts[:len(prefix)]) == prefix
               for prefix in allowed):
        raise GateError("artifact is outside the closed run allowlist: %s" % raw)
    if tuple(parts) in ((".autodev", "artifacts", run.run_id, "report.md"),
                        (".autodev", "artifacts", run.run_id, "receipts.json")):
        raise GateError("gate-owned report/index files cannot be evidence inputs")
    fd = open_anchored(run.repo_fd, parts)
    try:
        initial = os.fstat(fd)
        if initial.st_size > MAX_SCAN_BYTES:
            raise GateError("structured artifact exceeds the 64 MiB bound")
        hasher = hashlib.sha256()
        scanned = 0
        while True:
            chunk = os.read(fd, min(1024 * 1024, MAX_SCAN_BYTES - scanned + 1))
            if not chunk:
                break
            scanned += len(chunk)
            if scanned > MAX_SCAN_BYTES:
                raise GateError("structured artifact grew beyond the 64 MiB bound")
            hasher.update(chunk)
        final = os.fstat(fd)
        if ((initial.st_dev, initial.st_ino, initial.st_size, initial.st_mtime_ns, initial.st_ctime_ns) !=
                (final.st_dev, final.st_ino, final.st_size, final.st_mtime_ns, final.st_ctime_ns) or
                scanned != final.st_size):
            raise GateError("structured artifact changed while hashing", EXIT_INTEGRITY)
    finally:
        os.close(fd)
    verify_artifact_path_identity(run, parts, final)
    return {"path": candidate.as_posix(), "size": final.st_size, "sha256": hasher.hexdigest(),
            "modifiedNs": final.st_mtime_ns}


def verify_artifact_path_identity(run: Run, parts: Sequence[str], expected: os.stat_result) -> None:
    fd = open_anchored(run.repo_fd, parts)
    try:
        current = os.fstat(fd)
    finally:
        os.close(fd)
    if ((current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns, current.st_ctime_ns) !=
            (expected.st_dev, expected.st_ino, expected.st_size, expected.st_mtime_ns,
             expected.st_ctime_ns)):
        raise GateError("structured artifact path was replaced while reading", EXIT_INTEGRITY)


def json_object_bytes(data: bytes, label: str) -> Dict[str, Any]:
    if len(data) > MAX_SCAN_BYTES:
        raise GateError("%s exceeds the 64 MiB structured evidence bound" % label)
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        raise GateError("%s is not valid structured JSON" % label)
    if not isinstance(value, dict):
        raise GateError("%s must be a JSON object" % label)
    return value


def validate_evidence(evidence_type: str, data: bytes, criterion_id: str, platform: str,
                      result: str, source_revision: str, phase: str,
                      fingerprint: Optional[str]) -> Dict[str, Any]:
    if evidence_type == "junit":
        if len(data) > MAX_SCAN_BYTES or b"<!DOCTYPE" in data.upper():
            raise GateError("JUnit evidence is oversized or contains a forbidden doctype")
        try:
            root = ET.fromstring(data)
        except ET.ParseError:
            raise GateError("JUnit evidence is malformed")
        tag = root.tag.rsplit("}", 1)[-1]
        suites = [root] if tag == "testsuite" else list(root) if tag == "testsuites" else []
        if not suites or any(not isinstance(item.tag, str) or
                             item.tag.rsplit("}", 1)[-1] != "testsuite" for item in suites):
            raise GateError("JUnit evidence requires testsuite or testsuites")
        totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        bindings: Dict[str, str] = {}
        for suite in suites:
            for key in totals:
                raw = suite.attrib.get(key, "0")
                if not raw.isdigit():
                    raise GateError("JUnit count fields must be nonnegative integers")
                totals[key] += int(raw)
            for properties in suite.findall("./properties"):
                for prop in properties.findall("./property"):
                    name = prop.attrib.get("name")
                    value = prop.attrib.get("value")
                    if name in bindings and bindings[name] != value:
                        raise GateError("JUnit AutoDev binding property is ambiguous")
                    if isinstance(name, str) and isinstance(value, str):
                        bindings[name] = value
        required_bindings = {
            "autodev.sourceRevision": source_revision,
            "autodev.platform": platform,
            "autodev.criterionId": criterion_id,
            "autodev.result": result,
        }
        if any(bindings.get(key) != value for key, value in required_bindings.items()):
            raise GateError("JUnit evidence is not bound to this revision/criterion/platform/result")
        executed = totals["tests"] - totals["skipped"]
        consistent = (executed > 0 and totals["failures"] == 0 and totals["errors"] == 0
                      if result == "pass" else totals["failures"] + totals["errors"] > 0)
        if not consistent:
            raise GateError("JUnit contents do not match the recorded result")
        return {"format": "junit", **totals, "executed": executed}
    value = json_object_bytes(data, "%s evidence" % evidence_type)
    if evidence_type == "command":
        expected = ("schemaVersion", "kind", "sourceRevision", "timestamp", "platform",
                    "criterionId", "result", "exitCode", "command")
        exact_keys(value, expected, "command evidence", EXIT_VALIDATION)
        if (type(value["schemaVersion"]) is not int or value["schemaVersion"] != SCHEMA_VERSION or
                value["kind"] != "command-evidence" or value["sourceRevision"] != source_revision or
                value["platform"] != platform or value["criterionId"] != criterion_id or
                value["result"] != result or type(value["exitCode"]) is not int or
                not isinstance(value["command"], str) or not value["command"]):
            raise GateError("command evidence fields do not match the attempt")
        parse_timestamp(value["timestamp"], "command evidence")
        if (result == "pass") != (value["exitCode"] == 0):
            raise GateError("command evidence exitCode is inconsistent with result")
        return {"format": "command", "exitCode": value["exitCode"], "command": value["command"]}
    if evidence_type == "metric":
        expected = ("schemaVersion", "kind", "sourceRevision", "timestamp", "platform",
                    "criterionId", "result", "phase", "scenarioFingerprint", "metricName",
                    "value", "unit", "instrumentation", "aggregation", "sampleCount",
                    "threshold")
        exact_keys(value, expected, "metric evidence", EXIT_VALIDATION)
        numeric = value["value"]
        threshold = value["threshold"]
        if not isinstance(threshold, dict):
            raise GateError("metric threshold must be an exact object")
        exact_keys(threshold, ("direction", "minimumDelta", "deltaUnit"),
                   "metric threshold", EXIT_VALIDATION)
        minimum_delta = threshold["minimumDelta"]
        if (type(value["schemaVersion"]) is not int or value["schemaVersion"] != SCHEMA_VERSION or
                value["kind"] != "metric-evidence" or value["sourceRevision"] != source_revision or
                value["platform"] != platform or value["criterionId"] != criterion_id or
                value["result"] != result or value["phase"] != phase or
                value["scenarioFingerprint"] != fingerprint or
                not isinstance(value["metricName"], str) or not value["metricName"] or
                type(numeric) not in (int, float) or not math.isfinite(numeric) or
                not isinstance(value["unit"], str) or not value["unit"] or
                not isinstance(value["instrumentation"], str) or not value["instrumentation"] or
                not isinstance(value["aggregation"], str) or not value["aggregation"] or
                type(value["sampleCount"]) is not int or value["sampleCount"] < 1 or
                threshold["direction"] not in ("increase", "decrease") or
                type(minimum_delta) not in (int, float) or not math.isfinite(minimum_delta) or
                minimum_delta < 0 or threshold["deltaUnit"] not in ("absolute", "percent")):
            raise GateError("metric evidence fields do not match the attempt")
        parse_timestamp(value["timestamp"], "metric evidence")
        return {"format": "metric", "metricName": value["metricName"],
                "value": numeric, "unit": value["unit"], "scenarioFingerprint": fingerprint,
                "instrumentation": value["instrumentation"],
                "aggregation": value["aggregation"], "sampleCount": value["sampleCount"],
                "threshold": threshold}
    raise GateError("unsupported evidence type")


def validate_reread(data: bytes, criterion_id: str, platform: str, hypothesis: str,
                    source_revision: str) -> Dict[str, Any]:
    value = json_object_bytes(data, "reread reference")
    expected = ("schemaVersion", "kind", "sourceRevision", "timestamp", "platform",
                "criterionId", "hypothesis", "reference")
    exact_keys(value, expected, "reread reference", EXIT_VALIDATION)
    if (type(value["schemaVersion"]) is not int or value["schemaVersion"] != SCHEMA_VERSION or
            value["kind"] != "reread-reference" or value["sourceRevision"] != source_revision or
            value["platform"] != platform or value["criterionId"] != criterion_id or
            value["hypothesis"] != hypothesis or not isinstance(value["reference"], str) or
            not value["reference"]):
        raise GateError("reread reference fields do not match the attempt")
    parse_timestamp(value["timestamp"], "reread reference")
    return {"format": "reread-reference", "reference": value["reference"]}


def validate_observation_evidence(data: bytes, criterion_id: str, platform: str,
                                  scenario_key: str, outcome: str,
                                  source_revision: str) -> Dict[str, Any]:
    value = json_object_bytes(data, "bug observation evidence")
    exact_keys(value, ("schemaVersion", "kind", "sourceRevision", "timestamp", "platform",
                       "criterionId", "scenarioKey", "outcome", "command", "exitCode",
                       "diagnostic"),
               "bug observation evidence", EXIT_VALIDATION)
    exit_code = value["exitCode"]
    if (type(value["schemaVersion"]) is not int or value["schemaVersion"] != SCHEMA_VERSION or
            value["kind"] != "bug-observation" or value["sourceRevision"] != source_revision or
            value["platform"] != platform or value["criterionId"] != criterion_id or
            not isinstance(value["scenarioKey"], str) or not value["scenarioKey"].strip() or
            value["scenarioKey"] != scenario_key or
            value["outcome"] != outcome or outcome not in OBSERVATION_OUTCOMES or
            not isinstance(value["command"], str) or not value["command"].strip() or
            type(exit_code) is not int or
            not isinstance(value["diagnostic"], str) or not value["diagnostic"].strip()):
        raise GateError("bug observation evidence fields do not match the command")
    if ((outcome == "not-reproduced" and exit_code != 0) or
            (outcome != "not-reproduced" and exit_code == 0)):
        raise GateError("bug observation outcome does not match exitCode")
    parse_timestamp(value["timestamp"], "bug observation evidence")
    return {"format": "bug-observation", "scenarioKey": scenario_key, "outcome": outcome,
            "command": value["command"].strip(), "exitCode": exit_code,
            "diagnostic": value["diagnostic"].strip()}


def computed_perf_metadata(baseline: Dict[str, Any], candidate: Dict[str, Any],
                           baseline_sha256: str) -> Dict[str, Any]:
    comparable = ("metricName", "unit", "scenarioFingerprint", "instrumentation",
                  "aggregation", "sampleCount", "threshold")
    if any(baseline.get(field) != candidate.get(field) for field in comparable):
        raise GateError("performance baseline and candidate contracts are not identical")
    threshold = candidate["threshold"]
    baseline_value = baseline["value"]
    candidate_value = candidate["value"]
    raw_improvement = (candidate_value - baseline_value if threshold["direction"] == "increase"
                       else baseline_value - candidate_value)
    if not math.isfinite(raw_improvement):
        raise GateError("computed performance delta is not finite")
    if threshold["deltaUnit"] == "percent":
        if baseline_value == 0:
            raise GateError("percent performance delta is undefined for a zero baseline")
        computed_delta = raw_improvement / abs(baseline_value) * 100.0
    else:
        computed_delta = raw_improvement
    if not math.isfinite(computed_delta):
        raise GateError("computed performance delta is not finite")
    computed_result = "pass" if computed_delta >= threshold["minimumDelta"] else "fail"
    enriched = dict(candidate)
    enriched.update({
        "baselineValue": baseline_value, "candidateValue": candidate_value,
        "computedDelta": computed_delta, "computedResult": computed_result,
        "baselineEvidenceSha256": baseline_sha256,
    })
    return enriched


def require_later_revision(root: Path, initial: str, candidate: str, label: str,
                           code: int = EXIT_VALIDATION) -> None:
    if candidate == initial:
        raise GateError("%s must use a later revision" % label, code)
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", initial, candidate], cwd=str(root),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if result.returncode == 1:
        raise GateError("%s revision is not descended from the immutable initial revision" % label,
                        code)
    if result.returncode != 0:
        raise GateError("cannot verify source revision ancestry", EXIT_IO)


def find_criterion(run: Run, criterion_id: str) -> Dict[str, Any]:
    core, late = criteria(run)
    matches = [item for item in core + late if item["criterionId"] == criterion_id]
    if len(matches) != 1:
        raise GateError("unknown criterion id")
    return matches[0]


def verify_freeze(run: Run) -> Tuple[Dict[str, Any], List[Dict[str, Any]], List[Dict[str, Any]]]:
    frozen = run.frozen()
    if not frozen:
        raise GateError("checklist is not frozen")
    core, late = criteria(run)
    snapshot = [{key: item[key] for key in (
        "criterionId", "text", "kind", "requiredPlatforms", "evidenceType", "digest"
    )} for item in core]
    if frozen.get("criteria") != snapshot or frozen.get("checklistDigest") != digest(snapshot):
        raise GateError("frozen checklist was mutated or deleted", EXIT_INTEGRITY)
    return frozen, core, late


def record_attempt(args: argparse.Namespace, run: Run, result: str) -> Dict[str, Any]:
    frozen, _, _ = verify_freeze(run)
    criterion = find_criterion(run, args.criterion)
    if args.platform not in criterion["requiredPlatforms"]:
        raise GateError("platform is not required by this criterion")
    attempts = run.events("attempts")
    key_attempts = [event for event in attempts if event["criterionId"] == args.criterion
                    and event["platform"] == args.platform]
    if any(event.get("terminalPartial") for event in key_attempts):
        raise GateError("criterion/platform is terminal partial after its third failure", EXIT_GATE)
    failures = [event for event in key_attempts if event["result"] == "fail"]
    source_revision = resolve_revision(run.root, args.source_revision)
    reread = None
    reread_metadata = None
    hypothesis = args.hypothesis.strip() if args.hypothesis else None
    if len(failures) >= 2:
        if not hypothesis or not args.reread_reference:
            raise GateError("after two failures, --hypothesis and --reread-reference are required")
        prior = {str(event.get("hypothesis", "")).strip().casefold() for event in key_attempts}
        if hypothesis.casefold() in prior:
            raise GateError("hypothesis must be genuinely new")
        reread = artifact_record(run.root, run, args.reread_reference)
        reread_metadata = validate_reread(
            read_recorded_bytes(run, reread), args.criterion, args.platform,
            hypothesis, source_revision)
        prior_refs = {(event.get("rereadReference") or {}).get("path", "") + ":" +
                      (event.get("rereadReference") or {}).get("sha256", "") for event in key_attempts}
        if reread["path"] + ":" + reread["sha256"] in prior_refs:
            raise GateError("reread reference must be fresh")
        if failures and reread["modifiedNs"] <= failures[-1]["evidence"]["modifiedNs"]:
            raise GateError("reread reference must be newer than the latest failed evidence")
    evidence = artifact_record(run.root, run, args.evidence)
    manifest = run.manifest
    scenario = args.scenario_key
    fingerprint = args.scenario_fingerprint
    phase = args.phase
    evidence_metadata = validate_evidence(
        criterion["evidenceType"], read_recorded_bytes(run, evidence), args.criterion,
        args.platform, result, source_revision, phase, fingerprint)
    if manifest["taskType"] == "bug" and criterion["kind"] == "repro":
        if not scenario:
            raise GateError("bug reproduction attempts require --scenario-key")
        baseline = [event for event in key_attempts if event.get("phase") == "baseline"
                    and event["result"] == "fail" and event.get("scenarioKey") == scenario]
        if result == "fail" and phase != "baseline":
            raise GateError("bug reproduction failure must use --phase baseline")
        if result == "fail" and source_revision != manifest["sourceRevision"]:
            raise GateError("bug baseline must be recorded at the immutable initial source revision")
        if result == "pass" and (phase != "fixed" or not baseline):
            raise GateError("bug fix pass requires a prior failing baseline for the same scenario key")
        if result == "pass" and any(event["evidence"]["path"] == evidence["path"] for event in baseline):
            raise GateError("fixed evidence must be separate from baseline evidence")
        if result == "pass":
            require_later_revision(run.root, manifest["sourceRevision"], source_revision,
                                   "bug fixed evidence")
    elif manifest["taskType"] == "perf":
        if not fingerprint:
            raise GateError("performance attempts require --scenario-fingerprint")
        baselines = [event for event in key_attempts if event.get("phase") == "baseline"
                     and event.get("scenarioFingerprint") == fingerprint]
        if phase == "baseline":
            if result != "pass":
                raise GateError("performance baseline recording uses pass --phase baseline")
            if source_revision != manifest["sourceRevision"]:
                raise GateError("performance baseline must use the immutable initial source revision")
            if baselines:
                raise GateError("performance baseline is immutable and already recorded")
        elif phase not in ("candidate", "repeat") or not baselines:
            raise GateError("performance result requires baseline plus candidate/repeat with the same fingerprint")
        else:
            if len(baselines) != 1:
                raise GateError("performance comparison requires exactly one baseline", EXIT_INTEGRITY)
            require_later_revision(run.root, manifest["sourceRevision"], source_revision,
                                   "performance candidate/repeat")
            evidence_metadata = computed_perf_metadata(
                baselines[0]["evidenceMetadata"], evidence_metadata,
                baselines[0]["evidence"]["sha256"])
            if result != evidence_metadata["computedResult"]:
                raise GateError("performance result does not match the computed threshold outcome")
    elif phase != "observation":
        raise GateError("non bug/performance attempts must use --phase observation")
    derive(run, require_frozen=True)
    failure_number = len(failures) + (1 if result == "fail" else 0)
    event = run.append_event("attempts", {
        "criterionId": args.criterion, "platform": args.platform, "result": result,
        "phase": phase, "scenarioKey": scenario, "scenarioFingerprint": fingerprint,
        "evidence": evidence, "evidenceType": criterion["evidenceType"],
        "evidenceMetadata": evidence_metadata,
        "hypothesis": hypothesis, "rereadReference": reread,
        "rereadMetadata": reread_metadata,
        "sourceRevision": source_revision, "recordedAt": now(),
        "checklistDigest": frozen["checklistDigest"],
        "terminalPartial": result == "fail" and failure_number >= 3,
    })
    return {"ok": True, "command": result, "runId": run.run_id, "attempt": event}


def record_observation(args: argparse.Namespace, run: Run) -> Dict[str, Any]:
    frozen, _, _ = verify_freeze(run)
    manifest = run.manifest
    criterion = find_criterion(run, args.criterion)
    if manifest["taskType"] != "bug" or criterion["kind"] != "repro":
        raise GateError("observe is limited to bug reproduction criteria")
    if args.platform not in criterion["requiredPlatforms"]:
        raise GateError("platform is not required by this criterion")
    source_revision = resolve_revision(run.root, args.source_revision)
    if source_revision != manifest["sourceRevision"]:
        raise GateError("bug reproduction observations must use the immutable initial revision")
    scenario_key = args.scenario_key.strip()
    evidence = artifact_record(run.root, run, args.evidence)
    metadata = validate_observation_evidence(
        read_recorded_bytes(run, evidence), args.criterion, args.platform,
        scenario_key, args.outcome, source_revision)
    derive(run, require_frozen=True)
    event = run.append_event("observations", {
        "criterionId": args.criterion, "platform": args.platform,
        "scenarioKey": scenario_key,
        "outcome": args.outcome, "evidence": evidence, "evidenceMetadata": metadata,
        "sourceRevision": source_revision, "recordedAt": now(),
        "checklistDigest": frozen["checklistDigest"],
    })
    return {"ok": True, "command": "observe", "runId": run.run_id,
            "observation": event}


def revalidate_record(root: Path, run: Run, record: Dict[str, Any]) -> None:
    current = artifact_record(root, run, record.get("path", ""))
    if current["size"] != record.get("size") or current["sha256"] != record.get("sha256"):
        raise GateError("artifact changed after recording: %s" % record.get("path"), EXIT_INTEGRITY)


def read_recorded_bytes(run: Run, record: Dict[str, Any]) -> bytes:
    raw = record.get("path")
    if not isinstance(raw, str):
        raise GateError("recorded artifact path is invalid", EXIT_INTEGRITY)
    fd = open_anchored(run.repo_fd, Path(raw).parts)
    try:
        initial = os.fstat(fd)
        if initial.st_size > MAX_SCAN_BYTES:
            raise GateError("structured artifact exceeds the 64 MiB bound")
        chunks = []
        scanned = 0
        while True:
            chunk = os.read(fd, min(1024 * 1024, MAX_SCAN_BYTES - scanned + 1))
            if not chunk:
                break
            scanned += len(chunk)
            if scanned > MAX_SCAN_BYTES:
                raise GateError("structured artifact grew beyond the 64 MiB bound")
            chunks.append(chunk)
        final = os.fstat(fd)
        if ((initial.st_dev, initial.st_ino, initial.st_size, initial.st_mtime_ns, initial.st_ctime_ns) !=
                (final.st_dev, final.st_ino, final.st_size, final.st_mtime_ns, final.st_ctime_ns) or
                scanned != final.st_size):
            raise GateError("structured artifact changed while reading", EXIT_INTEGRITY)
        data = b"".join(chunks)
    finally:
        os.close(fd)
    verify_artifact_path_identity(run, Path(raw).parts, final)
    if len(data) != record.get("size") or hashlib.sha256(data).hexdigest() != record.get("sha256"):
        raise GateError("artifact changed while reading: %s" % raw, EXIT_INTEGRITY)
    return data


def derive(run: Run, require_frozen: bool = False) -> Dict[str, Any]:
    frozen = run.frozen()
    if frozen:
        frozen, core, late = verify_freeze(run)
    else:
        if require_frozen:
            raise GateError("checklist is not frozen", EXIT_GATE)
        core, late = criteria(run)
        if late:
            raise GateError("late regressions exist without a freeze", EXIT_INTEGRITY)
    attempts = run.events("attempts")
    observations = run.events("observations")
    manifest = run.manifest
    validated_attempts: List[Dict[str, Any]] = []
    for event in attempts:
        criterion = next((item for item in core + late
                          if item["criterionId"] == event["criterionId"]), None)
        if (criterion is None or event["platform"] not in criterion["requiredPlatforms"] or
                event["evidenceType"] != criterion["evidenceType"] or
                event["checklistDigest"] != (frozen["checklistDigest"] if frozen else None)):
            raise GateError("attempt is not bound to the frozen checklist", EXIT_INTEGRITY)
        revalidate_record(run.root, run, event["evidence"])
        metadata = validate_evidence(
            event["evidenceType"], read_recorded_bytes(run, event["evidence"]),
            event["criterionId"], event["platform"], event["result"],
            event["sourceRevision"], event["phase"], event.get("scenarioFingerprint"))
        if manifest["taskType"] == "bug" and criterion["kind"] == "repro":
            scenario = event.get("scenarioKey")
            if not scenario:
                raise GateError("bug reproduction attempt lacks a scenario key", EXIT_INTEGRITY)
            if event["phase"] == "baseline":
                if (event["result"] != "fail" or
                        event["sourceRevision"] != manifest["sourceRevision"]):
                    raise GateError("bug baseline provenance is invalid", EXIT_INTEGRITY)
            elif event["phase"] == "fixed":
                baselines = [prior for prior in validated_attempts
                             if prior["criterionId"] == event["criterionId"] and
                             prior["platform"] == event["platform"] and
                             prior["phase"] == "baseline" and prior["result"] == "fail" and
                             prior.get("scenarioKey") == scenario]
                if not baselines or event["result"] != "pass":
                    raise GateError("bug fixed evidence lacks its failing baseline", EXIT_INTEGRITY)
                if any(prior["evidence"]["path"] == event["evidence"]["path"]
                       for prior in baselines):
                    raise GateError("bug fixed evidence reused its baseline artifact", EXIT_INTEGRITY)
                require_later_revision(run.root, manifest["sourceRevision"],
                                       event["sourceRevision"], "bug fixed evidence",
                                       EXIT_INTEGRITY)
            else:
                raise GateError("bug reproduction attempt phase is invalid", EXIT_INTEGRITY)
        elif manifest["taskType"] == "perf":
            if event["phase"] == "baseline":
                if event["sourceRevision"] != manifest["sourceRevision"]:
                    raise GateError("performance baseline revision is not the immutable initial revision",
                                    EXIT_INTEGRITY)
            elif event["phase"] in ("candidate", "repeat"):
                baselines = [prior for prior in validated_attempts
                             if prior["criterionId"] == event["criterionId"] and
                             prior["platform"] == event["platform"] and
                             prior["phase"] == "baseline" and
                             prior.get("scenarioFingerprint") == event.get("scenarioFingerprint")]
                if len(baselines) != 1:
                    raise GateError("performance comparison baseline is missing or ambiguous",
                                    EXIT_INTEGRITY)
                require_later_revision(run.root, manifest["sourceRevision"],
                                       event["sourceRevision"], "performance candidate/repeat",
                                       EXIT_INTEGRITY)
                metadata = computed_perf_metadata(
                    baselines[0]["evidenceMetadata"], metadata,
                    baselines[0]["evidence"]["sha256"])
                if event["result"] != metadata["computedResult"]:
                    raise GateError("stored performance result differs from computed outcome",
                                    EXIT_INTEGRITY)
        if metadata != event["evidenceMetadata"]:
            raise GateError("recorded evidence metadata was mutated", EXIT_INTEGRITY)
        if event.get("rereadReference"):
            revalidate_record(run.root, run, event["rereadReference"])
            metadata = validate_reread(
                read_recorded_bytes(run, event["rereadReference"]),
                event["criterionId"], event["platform"], event["hypothesis"],
                event["sourceRevision"])
            if metadata != event.get("rereadMetadata"):
                raise GateError("recorded reread metadata was mutated", EXIT_INTEGRITY)
        validated_attempts.append(event)
    for event in observations:
        criterion = next((item for item in core + late
                          if item["criterionId"] == event["criterionId"]), None)
        if (manifest["taskType"] != "bug" or criterion is None or
                criterion["kind"] != "repro" or
                event["platform"] not in criterion["requiredPlatforms"] or
                event["sourceRevision"] != manifest["sourceRevision"] or
                event["checklistDigest"] != (frozen["checklistDigest"] if frozen else None)):
            raise GateError("observation is not bound to the initial bug checklist", EXIT_INTEGRITY)
        revalidate_record(run.root, run, event["evidence"])
        metadata = validate_observation_evidence(
            read_recorded_bytes(run, event["evidence"]), event["criterionId"],
            event["platform"], event["scenarioKey"], event["outcome"],
            event["sourceRevision"])
        if metadata != event["evidenceMetadata"]:
            raise GateError("recorded observation metadata was mutated", EXIT_INTEGRITY)
    matrix = []
    passing = True
    terminal_partial = False
    for criterion in core + late:
        for platform in criterion["requiredPlatforms"]:
            relevant = [event for event in attempts if event["criterionId"] == criterion["criterionId"]
                        and event["platform"] == platform]
            terminal_partial = terminal_partial or any(event.get("terminalPartial") for event in relevant)
            final = relevant[-1]["result"] if relevant else "missing"
            if manifest["taskType"] == "perf":
                final_events = [event for event in relevant if event["phase"] in ("candidate", "repeat")]
                final = final_events[-1]["result"] if final_events else "missing"
            if manifest["taskType"] == "bug" and criterion["kind"] == "repro":
                fixed = [event for event in relevant if event["phase"] == "fixed"]
                final = fixed[-1]["result"] if fixed else "missing"
            passing = passing and final == "pass"
            matrix.append({"criterionId": criterion["criterionId"], "kind": criterion["kind"],
                           "text": criterion["text"],
                           "evidenceType": criterion["evidenceType"],
                           "platform": platform, "result": final,
                           "attempts": len(relevant),
                           "evidencePaths": [event["evidence"]["path"] for event in relevant],
                           "lateRegression": criterion in late})
    late_digest = digest([event["digest"] for event in late])
    return {"frozen": bool(frozen),
            "checklistDigest": frozen["checklistDigest"] if frozen else None,
            "lateRegressionDigest": late_digest,
            "matrix": matrix, "passing": passing and bool(matrix),
            "terminalPartial": terminal_partial, "attempts": attempts,
            "observations": observations}


def status(args: argparse.Namespace, run: Run) -> Dict[str, Any]:
    result = derive(run)
    terminal = revalidate_terminal(run)
    if terminal:
        result["terminal"] = terminal
    result.update({"ok": True, "command": "status", "runId": run.run_id})
    return result


def revalidate_terminal(run: Run) -> Optional[Dict[str, Any]]:
    terminal = run.terminal()
    if not terminal:
        return None
    report = read_bytes_at(run.artifact_fd, "report.md")
    if hashlib.sha256(report).hexdigest() != terminal.get("reportSha256"):
        raise GateError("terminal report was mutated or deleted", EXIT_INTEGRITY)
    expected_index_hash = terminal.get("receiptIndexSha256")
    artifact_names = set(os.listdir(run.artifact_fd))
    if ("receipts.json" in artifact_names) != bool(expected_index_hash):
        raise GateError("terminal receipt index was mutated or deleted", EXIT_INTEGRITY)
    if expected_index_hash:
        index_data = read_bytes_at(run.artifact_fd, "receipts.json")
        if hashlib.sha256(index_data).hexdigest() != expected_index_hash:
            raise GateError("terminal receipt index hash mismatch", EXIT_INTEGRITY)
        try:
            index = json.loads(index_data.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise GateError("terminal receipt index is invalid: %s" % exc, EXIT_INTEGRITY)
        if not isinstance(index, dict) or not isinstance(index.get("receipts"), list):
            raise GateError("terminal receipt index has an invalid schema", EXIT_INTEGRITY)
        exact_keys(index, ("schemaVersion", "runId", "sourceRevision", "receipts"),
                   "terminal receipt index")
        if (type(index["schemaVersion"]) is not int or index["schemaVersion"] != SCHEMA_VERSION or
                index["runId"] != run.run_id or index["sourceRevision"] != terminal["checkedRevision"]):
            raise GateError("terminal receipt index fields are invalid", EXIT_INTEGRITY)
        for item in index["receipts"]:
            if not isinstance(item, dict) or not isinstance(item.get("artifact"), dict):
                raise GateError("terminal receipt index entry is invalid", EXIT_INTEGRITY)
            exact_keys(item, ("kind", "artifact"), "terminal receipt index entry")
            if item["kind"] not in ("build", "test", "memory", "review", "cleanup"):
                raise GateError("terminal receipt kind is invalid", EXIT_INTEGRITY)
            validate_artifact_record(item["artifact"], "receipt")
            revalidate_record(run.root, run, item["artifact"])
            parse_receipt(run.root, run, item["artifact"]["path"], item["kind"],
                          terminal["checkedRevision"], require_pass=terminal["outcome"] == "success")
    return terminal


def git_tree_blob(root: Path, revision: str, relative: str, label: str) -> bytes:
    candidate = Path(relative)
    if (not re.fullmatch(r"[0-9a-f]{40,64}", revision) or candidate.is_absolute() or not candidate.parts or
            ".." in candidate.parts or ":" in relative):
        raise GateError("%s Git tree reference is unsafe" % label, EXIT_GATE)
    tree_ref = "%s:%s" % (revision, candidate.as_posix())
    exists = subprocess.run(["git", "cat-file", "-e", tree_ref], cwd=str(root), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if exists.returncode:
        raise GateError("%s is absent from the checked revision tree" % label, EXIT_GATE)
    result = subprocess.run(["git", "show", tree_ref], cwd=str(root), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode or len(result.stdout) > MAX_SCAN_BYTES:
        raise GateError("cannot safely read %s from the checked revision tree" % label, EXIT_GATE)
    return result.stdout


def structural_instruction_contract(root: Path, source_revision: str,
                                   checked_revision: str) -> Tuple[List[str], str]:
    result = subprocess.run(["git", "diff", "--name-only", "-z", source_revision, checked_revision, "--"],
                            cwd=str(root), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise GateError("cannot determine structural instruction changes", EXIT_GATE)
    prefix = ".agents/skills/habit-lab-autodev/memory/instructions/"
    paths = sorted(path for path in parse_git_paths(result.stdout, "structural instruction diff")
                   if path.startswith(prefix))
    if len(paths) > 1:
        raise GateError("at most one structural instruction change may be sealed", EXIT_GATE)
    if paths:
        isolated = subprocess.run(["git", "diff-tree", "--no-commit-id", "--name-only", "-z", "-r", checked_revision],
                                  cwd=str(root), stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, check=False)
        if isolated.returncode:
            raise GateError("cannot determine isolated structural instruction change", EXIT_GATE)
        isolated_paths = sorted(parse_git_paths(isolated.stdout, "structural instruction commit"))
        if isolated_paths != paths:
            raise GateError("structural instruction change is not an isolated commit", EXIT_GATE)
    records = [{"path": path,
                "sha256": hashlib.sha256(git_tree_blob(root, checked_revision, path,
                                                         "structural instruction record")).hexdigest()}
               for path in paths]
    return paths, digest({"sourceRevision": checked_revision, "paths": records})


def memory_catalog_at_revision(root: Path, revision: str) -> Dict[str, str]:
    data = git_tree_blob(root, revision, ".agents/skills/habit-lab-autodev/memory/catalog.json", "memory catalog")
    try:
        catalog = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GateError("current memory catalog is invalid: %s" % exc, EXIT_GATE)
    exact_keys(catalog, ("schemaVersion", "entries"), "current memory catalog", EXIT_GATE)
    if catalog["schemaVersion"] != SCHEMA_VERSION or not isinstance(catalog["entries"], list):
        raise GateError("current memory catalog has an invalid schema", EXIT_GATE)
    mapped: Dict[str, str] = {}
    for item in catalog["entries"]:
        exact_keys(item, ("id", "path", "tags", "kind", "wordBudget", "routeKey", "destination"),
                   "current memory catalog entry", EXIT_GATE)
        if (not isinstance(item["id"], str) or not ID_RE.fullmatch(item["id"]) or
                not isinstance(item["path"], str) or not item["path"].startswith("memory/") or
                ".." in Path(item["path"]).parts or item["id"] in mapped):
            raise GateError("current memory catalog entry is invalid", EXIT_GATE)
        mapped[item["id"]] = item["path"]
    if (mapped.get("memory.screen-navigation") != "memory/screen-navigation.md" or
            mapped.get("memory.lessons") != "memory/lessons.md"):
        raise GateError("current memory catalog does not pin the initial entry paths", EXIT_GATE)
    return mapped


def validate_memory_file_records(root: Path, run: Run, checked_revision: str, label: str,
                                 records: List[Dict[str, Any]], seen_paths: set) -> None:
    """Bind every declared read/write to bytes that still exist at its proper scope."""
    for item in records:
        path = item["path"]
        candidate = Path(path)
        if (candidate.is_absolute() or not candidate.parts or ".." in candidate.parts or
                "\\" in path or candidate.as_posix() != path):
            raise GateError("memory ledger %s path is not a safe canonical relative path" % label, EXIT_GATE)
        if path in seen_paths:
            raise GateError("memory ledger repeats an exact read/write path", EXIT_GATE)
        seen_paths.add(path)
        artifact_prefixes = ((".autodev", "artifacts", run.run_id), ("build", "maestro", run.run_id))
        if any(tuple(candidate.parts[:len(prefix)]) == prefix for prefix in artifact_prefixes):
            actual = artifact_record(root, run, path)["sha256"]
        else:
            source = ".agents/skills/habit-lab-autodev/" + path if path.startswith("memory/") else path
            actual = hashlib.sha256(git_tree_blob(root, checked_revision, source,
                                                   "memory ledger %s source" % label)).hexdigest()
        if actual != item["sha256"]:
            raise GateError("memory ledger %s digest does not match recorded bytes" % label, EXIT_GATE)


def validate_legacy_memory_receipt(receipt: Dict[str, Any]) -> None:
    lint = receipt.get("lint")
    if (not isinstance(receipt.get("read"), list) or not receipt["read"] or
            not isinstance(receipt.get("written"), list) or not isinstance(lint, dict) or
            lint.get("status") not in ("pass", "fail", "blocked", "skipped") or
            type(lint.get("exitCode")) is not int or not isinstance(lint.get("command"), str) or
            not lint["command"]):
        raise GateError("legacy memory receipt is invalid", EXIT_GATE)
    if receipt["status"] == "pass" and (lint["status"] != "pass" or lint["exitCode"] != 0):
        raise GateError("legacy passing memory receipt lacks passing lint", EXIT_GATE)


def validate_memory_receipt(root: Path, run: Run, receipt: Dict[str, Any],
                            checked_revision: str) -> None:
    exact_keys(receipt, ("schemaVersion", "kind", "sourceRevision", "timestamp", "status", "runId",
                         "ledger", "ledgerSha256", "loaded", "lint", "structureChanged", "evalReceipt",
                         "instructionPatchCount"), "memory receipt", EXIT_GATE)
    if (receipt["runId"] != run.run_id or receipt["sourceRevision"] != checked_revision or
            receipt["status"] != "pass" or not isinstance(receipt["ledger"], str) or
            receipt["ledger"] != ".autodev/artifacts/%s/memory-ledger.json" % run.run_id or
            not isinstance(receipt["ledgerSha256"], str) or
            not re.fullmatch(r"[0-9a-f]{64}", receipt["ledgerSha256"])):
        raise GateError("memory receipt is not bound to this checked run", EXIT_GATE)
    ledger_record = artifact_record(root, run, receipt["ledger"])
    if ledger_record["sha256"] != receipt["ledgerSha256"]:
        raise GateError("memory receipt ledger digest does not match its artifact", EXIT_GATE)
    try:
        ledger = json.loads(read_recorded_bytes(run, ledger_record).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GateError("memory ledger JSON is invalid: %s" % exc, EXIT_GATE)
    ledger_fields = ("schemaVersion", "runId", "createdAt", "updatedAt", "finalizedAt", "initialEntryIds",
                     "plannedEntryIds", "loadedEntries", "reads", "writes", "durationSeconds", "builds",
                     "iterations", "attempts", "outcome", "platforms", "flakySteps", "gateRun",
                     "gateStatusSha256")
    exact_keys(ledger, ledger_fields, "memory ledger", EXIT_GATE)
    if (ledger["schemaVersion"] != SCHEMA_VERSION or ledger["runId"] != run.run_id or
            ledger["outcome"] != "success" or not isinstance(ledger["loadedEntries"], list)):
        raise GateError("memory ledger is not a finalized successful run ledger", EXIT_GATE)
    for key in ("createdAt", "updatedAt", "finalizedAt"):
        parse_timestamp(ledger.get(key), "memory ledger %s" % key, EXIT_GATE)
    if ledger["initialEntryIds"] != ["memory.screen-navigation", "memory.lessons"]:
        raise GateError("memory ledger initial memory contract is invalid", EXIT_GATE)
    planned = ledger["plannedEntryIds"]
    if (not isinstance(planned, list) or len(planned) > 3 or len(planned) != len(set(planned)) or
            any(not isinstance(item, str) or not ID_RE.fullmatch(item) or
                item in ledger["initialEntryIds"] for item in planned)):
        raise GateError("memory ledger planned entries are invalid", EXIT_GATE)
    for field in ("reads", "writes", "builds", "platforms", "flakySteps"):
        if not isinstance(ledger[field], list):
            raise GateError("memory ledger %s is invalid" % field, EXIT_GATE)
    for field in ("durationSeconds", "iterations", "attempts"):
        if type(ledger[field]) is not int or ledger[field] < 0:
            raise GateError("memory ledger %s is invalid" % field, EXIT_GATE)
    for field in ("reads", "writes"):
        seen = set()
        for item in ledger[field]:
            exact_keys(item, ("path", "sha256"), "memory ledger %s record" % field, EXIT_GATE)
            if (not isinstance(item["path"], str) or not item["path"] or
                    not isinstance(item["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", item["sha256"]) or
                    item["path"] in seen):
                raise GateError("memory ledger %s records are invalid" % field, EXIT_GATE)
            seen.add(item["path"])
    validate_memory_file_records(root, run, checked_revision, "read", ledger["reads"], set())
    validate_memory_file_records(root, run, checked_revision, "write", ledger["writes"], set())
    build_names = set()
    for item in ledger["builds"]:
        exact_keys(item, ("name", "status"), "memory ledger build", EXIT_GATE)
        if (not isinstance(item["name"], str) or not ID_RE.fullmatch(item["name"]) or
                item["status"] not in ("pass", "fail", "skipped") or item["name"] in build_names):
            raise GateError("memory ledger builds are invalid", EXIT_GATE)
        build_names.add(item["name"])
    if (ledger["platforms"] != sorted(ledger["platforms"]) or len(ledger["platforms"]) != len(set(ledger["platforms"])) or
            any(item not in PLATFORMS for item in ledger["platforms"]) or
            ledger["flakySteps"] != sorted(ledger["flakySteps"]) or len(ledger["flakySteps"]) != len(set(ledger["flakySteps"])) or
            any(not isinstance(item, str) or not item for item in ledger["flakySteps"])):
        raise GateError("memory ledger platforms or flaky steps are invalid", EXIT_GATE)
    if ledger["gateRun"] is not None:
        validate_run_id(ledger["gateRun"])
        if ledger["gateRun"] != run.run_id:
            raise GateError("memory ledger gate run escaped this run", EXIT_GATE)
    elif ledger["gateStatusSha256"] is not None:
        raise GateError("memory ledger gate status has no matching gate run", EXIT_GATE)
    if ledger["gateStatusSha256"] is not None and (not isinstance(ledger["gateStatusSha256"], str) or
                                                    not re.fullmatch(r"[0-9a-f]{64}", ledger["gateStatusSha256"])):
        raise GateError("memory ledger gate status digest is invalid", EXIT_GATE)
    catalog = memory_catalog_at_revision(root, checked_revision)
    actual_loaded = []
    for item in ledger["loadedEntries"]:
        exact_keys(item, ("entryId", "path", "sha256", "loadedAt"), "memory ledger loaded entry", EXIT_GATE)
        if (not isinstance(item["entryId"], str) or not ID_RE.fullmatch(item["entryId"]) or
                not isinstance(item["path"], str) or not item["path"].startswith("memory/") or
                not isinstance(item["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", item["sha256"])):
            raise GateError("memory ledger loaded entry is invalid", EXIT_GATE)
        parse_timestamp(item["loadedAt"], "memory ledger loaded entry", EXIT_GATE)
        if catalog.get(item["entryId"]) != item["path"]:
            raise GateError("memory ledger entry does not match the current catalog", EXIT_GATE)
        source_relative = ".agents/skills/habit-lab-autodev/" + item["path"]
        if hashlib.sha256(git_tree_blob(root, checked_revision, source_relative, "memory entry")).hexdigest() != item["sha256"]:
            raise GateError("memory ledger entry does not match checked tracked memory bytes", EXIT_GATE)
        actual_loaded.append({"entryId": item["entryId"], "path": item["path"], "sha256": item["sha256"]})
    loaded_ids = {item["entryId"] for item in actual_loaded}
    if len(loaded_ids) != len(actual_loaded) or not set(ledger["initialEntryIds"]).issubset(loaded_ids):
        raise GateError("memory ledger has duplicate loaded entries", EXIT_GATE)
    non_initial_loaded = loaded_ids - set(ledger["initialEntryIds"])
    if len(non_initial_loaded) > 3 or not non_initial_loaded.issubset(set(planned)):
        raise GateError("memory ledger loaded entries escape its plan", EXIT_GATE)
    supplied_loaded = receipt["loaded"]
    if not isinstance(supplied_loaded, list):
        raise GateError("memory receipt loaded entries are invalid", EXIT_GATE)
    for item in supplied_loaded:
        exact_keys(item, ("entryId", "path", "sha256"), "memory receipt loaded entry", EXIT_GATE)
    if supplied_loaded != sorted(actual_loaded, key=lambda item: item["entryId"]):
        raise GateError("memory receipt loaded paths and digests do not match its ledger", EXIT_GATE)
    lint = receipt["lint"]
    exact_keys(lint, ("command", "status", "exitCode"), "memory receipt lint", EXIT_GATE)
    if (lint["command"] != "python3 .agents/skills/habit-lab-autodev/scripts/autodev_memory.py lint" or
            lint["status"] != "pass" or lint["exitCode"] != 0):
        raise GateError("memory receipt lint did not pass exactly", EXIT_GATE)
    if type(receipt["structureChanged"]) is not bool or type(receipt["instructionPatchCount"]) is not int or not 0 <= receipt["instructionPatchCount"] <= 1:
        raise GateError("memory receipt structure fields are invalid", EXIT_GATE)
    instruction_paths, change_digest = structural_instruction_contract(
        root, run.manifest["sourceRevision"], checked_revision)
    if receipt["structureChanged"] != bool(instruction_paths) or receipt["instructionPatchCount"] != len(instruction_paths):
        raise GateError("memory receipt structure declaration does not match changed paths", EXIT_GATE)
    evaluation = receipt["evalReceipt"]
    if receipt["structureChanged"]:
        exact_keys(evaluation, ("path", "sha256", "status"), "memory receipt evaluation", EXIT_GATE)
        if (not isinstance(evaluation["path"], str) or not isinstance(evaluation["sha256"], str) or
                not re.fullmatch(r"[0-9a-f]{64}", evaluation["sha256"]) or evaluation["status"] != "pass"):
            raise GateError("memory receipt evaluation is invalid", EXIT_GATE)
        evaluation_record = artifact_record(root, run, evaluation["path"])
        if evaluation_record["sha256"] != evaluation["sha256"]:
            raise GateError("memory receipt evaluation digest does not match its artifact", EXIT_GATE)
        try:
            evaluation_value = json.loads(read_recorded_bytes(run, evaluation_record).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise GateError("memory evaluation receipt is invalid: %s" % exc, EXIT_GATE)
        exact_keys(evaluation_value, ("schemaVersion", "kind", "runId", "sourceRevision", "status", "command",
                                      "exitCode", "checkedChangeDigest", "regressionResult"),
                   "memory evaluation receipt", EXIT_GATE)
        if (evaluation_value["schemaVersion"] != SCHEMA_VERSION or
                evaluation_value["kind"] != "autodev-memory-eval" or
                evaluation_value["runId"] != run.run_id or evaluation_value["sourceRevision"] != checked_revision or
                evaluation_value["status"] != "pass" or not isinstance(evaluation_value["command"], str) or
                not evaluation_value["command"] or evaluation_value["exitCode"] != 0 or
                evaluation_value["checkedChangeDigest"] != change_digest or
                evaluation_value["regressionResult"] != "pass"):
            raise GateError("memory evaluation receipt did not pass", EXIT_GATE)
    elif evaluation is not None:
        raise GateError("non-structural memory receipt cannot carry an evaluation receipt", EXIT_GATE)


def parse_receipt(root: Path, run: Run, path: str, expected_kind: str,
                  checked_revision: str, require_pass: bool = True) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    record = artifact_record(root, run, path)
    try:
        receipt = json.loads(read_recorded_bytes(run, record).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GateError("invalid receipt JSON in %s: %s" % (path, exc), EXIT_GATE)
    if not isinstance(receipt, dict):
        raise GateError("receipt must be a JSON object: %s" % path, EXIT_GATE)
    receipt_revision = receipt.get("sourceRevision")
    statuses = ("pass", "fail", "blocked", "skipped")
    common = (type(receipt.get("schemaVersion")) is int and
              receipt.get("schemaVersion") == SCHEMA_VERSION and
              receipt.get("kind") == expected_kind and
              isinstance(receipt_revision, str) and receipt_revision.lower() == checked_revision and
              receipt.get("status") in statuses)
    if not common:
        raise GateError("invalid %s receipt: %s" % (expected_kind, path), EXIT_GATE)
    if require_pass and receipt["status"] != "pass":
        raise GateError("%s receipt is not passing: %s" % (expected_kind, path), EXIT_GATE)
    parse_timestamp(receipt.get("timestamp"), "%s receipt" % expected_kind)
    if expected_kind in ("build", "test"):
        if type(receipt.get("exitCode")) is not int or not isinstance(receipt.get("command"), str) or not receipt["command"]:
            raise GateError("%s receipt requires command and integer exitCode" % expected_kind, EXIT_GATE)
        if receipt["status"] == "pass" and receipt["exitCode"] != 0:
            raise GateError("passing %s receipt requires exitCode 0" % expected_kind, EXIT_GATE)
        if receipt["status"] == "fail" and receipt["exitCode"] == 0:
            raise GateError("failed %s receipt requires a nonzero exitCode" % expected_kind, EXIT_GATE)
        if not isinstance(receipt.get("platforms"), list):
            raise GateError("%s receipt requires an explicit platforms array" % expected_kind, EXIT_GATE)
        parse_platforms(receipt["platforms"])
    elif expected_kind == "memory":
        if run.manifest.get("memoryReceiptContract") == 2:
            validate_memory_receipt(root, run, receipt, checked_revision)
        else:
            validate_legacy_memory_receipt(receipt)
    elif expected_kind == "review":
        reviewed = receipt.get("reviewedRevision")
        if not isinstance(reviewed, str) or reviewed.lower() != checked_revision:
            raise GateError("reviewedRevision does not match checked revision", EXIT_GATE)
        unresolved = receipt.get("unresolvedJustifiedFindings")
        if type(unresolved) is not int or unresolved < 0:
            raise GateError("review receipt requires a nonnegative finding count", EXIT_GATE)
        if receipt["status"] == "pass" and unresolved != 0:
            raise GateError("passing review has unresolved justified findings", EXIT_GATE)
        if receipt.get("independent") is not True:
            raise GateError("review receipt must attest an independent review", EXIT_GATE)
    elif expected_kind == "cleanup":
        scan = receipt.get("secretScan")
        if not isinstance(receipt.get("residualScratch"), list) or not isinstance(receipt.get("forbiddenHooks"), list):
            raise GateError("cleanup receipt requires residualScratch and forbiddenHooks arrays", EXIT_GATE)
        if (not isinstance(scan, dict) or scan.get("status") not in statuses or
                type(scan.get("findings")) is not int or scan.get("findings") < 0 or
                not isinstance(scan.get("checkedPaths"), list) or
                not isinstance(scan.get("allowlistedPaths"), list) or
                not isinstance(scan.get("coverage"), str) or not scan["coverage"]):
            raise GateError("cleanup receipt requires a bounded passing secret scan", EXIT_GATE)
        if receipt["status"] == "pass" and (receipt["residualScratch"] != [] or
                receipt["forbiddenHooks"] != [] or scan["status"] != "pass" or scan["findings"] != 0):
            raise GateError("passing cleanup receipt reports residuals or findings", EXIT_GATE)
    return receipt, record


def changed_files(root: Path, base: str, checked: str) -> List[str]:
    result = subprocess.run(["git", "diff", "--name-only", "-z", "--diff-filter=ACMR", base, checked, "--"],
                            cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            check=False)
    if result.returncode != 0:
        raise GateError("cannot determine changed source for bounded scan", EXIT_GATE)
    return parse_git_paths(result.stdout, "changed source")


def require_clean_revision(root: Path) -> None:
    result = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=normal"], cwd=str(root),
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if result.returncode != 0:
        raise GateError("cannot verify the checked working tree", EXIT_GATE)
    if result.stdout.strip():
        raise GateError("working tree differs from the checked revision", EXIT_GATE)


def bounded_scan(run: Run, paths: Iterable[str]) -> Dict[str, Any]:
    hook = re.compile(b"AUTODEV_" + b"(?:DEBUG|BYPASS|DISABLE_GATE)")
    secrets = [
        re.compile(b"AK" + b"IA[0-9A-Z]{16}"),
        re.compile(b"gh" + b"p_[A-Za-z0-9]{30,}"),
        re.compile(b"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ]
    checked, skipped, findings = [], [], []
    allowlisted: List[str] = []
    for raw in sorted(set(paths)):
        if raw in allowlisted:
            skipped.append(raw)
            continue
        candidate = Path(raw)
        if candidate.is_absolute() or ".." in candidate.parts:
            raise GateError("scan input is outside the repository: %s" % raw, EXIT_GATE)
        fd = open_anchored(run.repo_fd, candidate.parts)
        try:
            initial = os.fstat(fd)
            if initial.st_size > MAX_SCAN_BYTES:
                raise GateError("scan input exceeds the documented 64 MiB bound: %s" % raw, EXIT_GATE)
            scanned = 0
            tail = b""
            hook_found = False
            secret_found = False
            while True:
                chunk = os.read(fd, min(1024 * 1024, MAX_SCAN_BYTES - scanned + 1))
                if not chunk:
                    break
                scanned += len(chunk)
                if scanned > MAX_SCAN_BYTES:
                    raise GateError("scan input grew beyond the documented 64 MiB bound: %s" % raw,
                                    EXIT_GATE)
                window = tail + chunk
                hook_found = hook_found or bool(hook.search(window))
                secret_found = secret_found or any(pattern.search(window) for pattern in secrets)
                tail = window[-512:]
            final = os.fstat(fd)
            if ((initial.st_dev, initial.st_ino, initial.st_size, initial.st_mtime_ns,
                 initial.st_ctime_ns) !=
                    (final.st_dev, final.st_ino, final.st_size, final.st_mtime_ns,
                     final.st_ctime_ns) or scanned != final.st_size):
                raise GateError("scan input changed while reading: %s" % raw, EXIT_INTEGRITY)
        finally:
            os.close(fd)
        verify_artifact_path_identity(run, candidate.parts, final)
        checked.append(raw)
        if hook_found:
            findings.append({"path": raw, "type": "forbidden-autodev-hook"})
        if secret_found:
            findings.append({"path": raw, "type": "known-secret-marker"})
    if findings:
        raise GateError("bounded scan found forbidden markers in %d file(s)" % len({x['path'] for x in findings}), EXIT_GATE)
    return {"status": "pass", "coverage": "changed source and checked inputs, regular files <=64 MiB",
            "checkedPaths": checked, "allowlistedPaths": skipped, "findings": 0}


def report_markdown(run: Run, outcome: str, reason: Optional[str], derived: Dict[str, Any],
                    receipts: Dict[str, List[Dict[str, Any]]], checked_revision: str,
                    scan: Optional[Dict[str, Any]]) -> bytes:
    manifest = run.manifest
    lines = ["# AutoDev report: %s" % run.run_id, "", "- Outcome: `%s`" % outcome,
             "- Task: `%s` (`%s`)" % (manifest["taskId"], manifest["taskType"]),
             "- Initial source revision: `%s`" % manifest["sourceRevision"],
             "- Checked source revision: `%s`" % checked_revision,
             "- Draft PR: %s" % ("eligible" if outcome == "success" else "not eligible"),
             "- Reason: %s" % (reason or "none"), "", "## Blast radius", ""]
    lines.extend("- %s" % item for item in manifest["blastRadius"])
    lines.extend(["", "## Checklist and evidence", "",
                  "| Criterion | Assertion | Kind | Platform | Result | Type | Evidence | Late regression |",
                  "| --- | --- | --- | --- | --- | --- | --- | --- |"])
    for row in derived["matrix"]:
        lines.append("| %s | %s | %s | %s | %s | %s | %s | %s |" % (
            row["criterionId"], row["text"].replace("|", "\\|"), row["kind"],
            row["platform"], row["result"], row["evidenceType"],
            "<br>".join("`%s`" % path for path in row["evidencePaths"]) or "none",
            "yes" if row["lateRegression"] else "no"))
    lines.extend(["", "## Attempts and hypotheses", ""])
    if not derived["attempts"]:
        lines.append("- No attempts recorded.")
    for event in derived["attempts"]:
        lines.append("- %s/%s: %s (%s), evidence `%s`, hypothesis: %s" % (
            event["criterionId"], event["platform"], event["result"], event["phase"],
            event["evidence"]["path"], event.get("hypothesis") or "none"))
        if event["evidenceType"] == "metric":
            metadata = event["evidenceMetadata"]
            comparison = ("; computed %s delta %s %s against minimum %s (%s), "
                          "baselineEvidenceSha256 `%s`" % (
                metadata["threshold"]["direction"],
                metadata["computedDelta"], metadata["threshold"]["deltaUnit"],
                metadata["threshold"]["minimumDelta"], metadata["computedResult"],
                metadata["baselineEvidenceSha256"])
                if "computedDelta" in metadata else "; immutable baseline")
            lines.append("  - Metric: %s=%s %s, %s/%s, samples=%s%s" % (
                metadata["metricName"], metadata["value"], metadata["unit"],
                metadata["instrumentation"], metadata["aggregation"],
                metadata["sampleCount"], comparison))
    lines.extend(["", "## Bug reproduction observations", ""])
    if not derived["observations"]:
        lines.append("- No diagnostic observations recorded.")
    for event in derived["observations"]:
        metadata = event["evidenceMetadata"]
        lines.append("- %s/%s scenario %s: %s at `%s`, evidence `%s`, command exit %s; "
                     "diagnostic: %s" % (
            event["criterionId"], event["platform"],
            json.dumps(event["scenarioKey"], ensure_ascii=True),
            event["outcome"], event["sourceRevision"], event["evidence"]["path"],
            metadata["exitCode"],
            json.dumps(metadata["diagnostic"], ensure_ascii=True)))
    lines.extend(["", "## Builds and tests", ""])
    for kind in ("build", "test"):
        values = receipts.get(kind, [])
        lines.append("- %s: %s" % (kind.capitalize(), ", ".join(
            "%s (`%s`, exit %s)" % (item["status"], item["command"], item["exitCode"])
            for item in values) or "not supplied"))
    lines.extend(["", "## Memory, review, cleanup, and devices", ""])
    memory = receipts.get("memory", [])
    review = receipts.get("review", [])
    cleanup = receipts.get("cleanup", [])
    if memory:
        memory_receipt = memory[0]
        if "loaded" in memory_receipt:
            memory_summary = "%s; %s loaded; lint %s; instruction patches %s" % (
                memory_receipt["status"], len(memory_receipt["loaded"]),
                memory_receipt["lint"]["status"], memory_receipt["instructionPatchCount"])
        else:
            memory_summary = "%s; %s read, %s written; lint %s" % (
                memory_receipt["status"], len(memory_receipt["read"]),
                len(memory_receipt["written"]), memory_receipt["lint"]["status"])
    else:
        memory_summary = "not supplied"
    lines.append("- Memory: %s" % memory_summary)
    lines.append("- Independent review: %s" % ("%s; %s unresolved justified findings" % (
        review[0]["status"], review[0]["unresolvedJustifiedFindings"]) if review else "not supplied"))
    lines.append("- Cleanup: %s" % ("%s; %s residual scratch entries, %s hook entries, scan %s" % (
        cleanup[0]["status"], len(cleanup[0]["residualScratch"]),
        len(cleanup[0]["forbiddenHooks"]), cleanup[0]["secretScan"]["status"])
        if cleanup else "not supplied"))
    lines.append("- Device leases: none acquired (immutable manifest state)")
    lines.extend(["", "## Limitations", ""])
    if scan:
        lines.append("- Secret/hook scan was deterministic and bounded to %s; it is not a universal secret scanner." % scan["coverage"])
    else:
        lines.append("- Terminal non-success preserves partial evidence; success-only receipt and source scans were not required.")
    lines.append("")
    return "\n".join(lines).encode("utf-8")


def finish(args: argparse.Namespace, run: Run) -> Dict[str, Any]:
    derived = derive(run)
    checked = resolve_revision(run.root, args.source_revision)
    if run.terminal():
        raise GateError("run already has a terminal outcome")
    if derived["terminalPartial"] and args.outcome != "partial":
        raise GateError("third failure requires terminal outcome partial", EXIT_GATE)
    if args.outcome != "success" and (not args.reason or not args.reason.strip()):
        raise GateError("non-success finish requires --reason")
    if args.reason:
        args.reason = args.reason.strip()
    receipt_values: Dict[str, List[Dict[str, Any]]] = {}
    receipt_index: List[Dict[str, Any]] = []
    scan = None
    supplied = {
        "build": args.build_receipt, "test": args.test_receipt,
        "memory": [args.memory_receipt] if args.memory_receipt else [],
        "review": [args.review_receipt] if args.review_receipt else [],
        "cleanup": [args.cleanup_receipt] if args.cleanup_receipt else [],
    }
    if args.outcome == "success":
        if not derived["frozen"]:
            raise GateError("success requires a frozen checklist", EXIT_GATE)
        if not derived["passing"]:
            raise GateError("not every criterion/platform has passing evidence", EXIT_GATE)
        head = current_revision(run.root)
        if head != checked:
            raise GateError("checked revision is not the current HEAD", EXIT_GATE)
        require_clean_revision(run.root)
        final_attempts: Dict[Tuple[str, str], Dict[str, Any]] = {}
        for event in derived["attempts"]:
            if event["result"] == "pass" and event["phase"] != "baseline":
                final_attempts[(event["criterionId"], event["platform"])] = event
        if any(event["sourceRevision"] != checked for event in final_attempts.values()):
            raise GateError("final passing evidence is not bound to the checked revision", EXIT_GATE)
        if run.manifest["taskType"] == "bug":
            _, core, late = verify_freeze(run)
            if not any(item["kind"] == "repro" for item in core + late):
                raise GateError("bug success requires a reproduction criterion", EXIT_GATE)
        if any(not paths for paths in supplied.values()):
            raise GateError("success requires build, test, memory, review, and cleanup receipts", EXIT_GATE)
    for kind, paths in supplied.items():
        receipt_values[kind] = []
        for path in paths:
            receipt, record = parse_receipt(
                run.root, run, path, kind, checked, require_pass=args.outcome == "success")
            receipt_values[kind].append(receipt)
            receipt_index.append({"kind": kind, "artifact": record})
    if args.outcome == "success":
        covered = {kind: set() for kind in ("build", "test")}
        for kind in covered:
            for item in receipt_values[kind]:
                covered[kind].update(item["platforms"])
            missing = set(run.manifest["requestedPlatforms"]) - covered[kind]
            if missing:
                raise GateError("%s receipts do not cover: %s" % (kind, ", ".join(sorted(missing))), EXIT_GATE)
        review = receipt_values["review"][0]
        if review.get("lateRegressionDigest") != derived["lateRegressionDigest"]:
            raise GateError("independent review predates the latest late regression", EXIT_GATE)
        _, _, late = verify_freeze(run)
        if late and parse_timestamp(review["timestamp"], "review receipt") <= parse_timestamp(
                late[-1]["createdAt"], "late regression"):
            raise GateError("independent review timestamp predates the latest late regression", EXIT_GATE)
        state_names = set(os.listdir(run.state_fd))
        if "scratch" in state_names:
            scratch_fd = open_child_dir(run.state_fd, "scratch")
            try:
                if os.listdir(scratch_fd):
                    raise GateError("run-owned scratch remains", EXIT_GATE)
            finally:
                os.close(scratch_fd)
        changed = changed_files(run.root, run.manifest["sourceRevision"], checked)
        checked_inputs = [item["artifact"]["path"] for item in receipt_index]
        checked_inputs.extend(event["evidence"]["path"] for event in derived["attempts"])
        checked_inputs.extend(event["evidence"]["path"] for event in derived["observations"])
        checked_inputs.extend(event["rereadReference"]["path"] for event in derived["attempts"] if event.get("rereadReference"))
        scan = bounded_scan(run, changed + checked_inputs)
    report = report_markdown(run, args.outcome, args.reason, derived, receipt_values, checked, scan)
    report_path = run.artifacts / "report.md"
    atomic_write_at(run.artifact_fd, "report.md", report)
    receipt_index_data = None
    if receipt_index:
        receipt_index_data = canonical_json({
            "schemaVersion": SCHEMA_VERSION, "runId": run.run_id,
            "sourceRevision": checked, "receipts": receipt_index,
        }) + b"\n"
        atomic_write_at(run.artifact_fd, "receipts.json", receipt_index_data)
    terminal = {"schemaVersion": SCHEMA_VERSION, "runId": run.run_id, "outcome": args.outcome,
                "reason": args.reason, "checkedRevision": checked, "finishedAt": now(),
                "reportSha256": hashlib.sha256(report).hexdigest(),
                "receiptIndexSha256": hashlib.sha256(receipt_index_data).hexdigest()
                if receipt_index_data else None}
    exclusive_json_at(run.state_fd, "terminal.json", terminal)
    exclusive_json_at(run.state_fd, "terminal.anchor", {"terminalDigest": digest(terminal)})
    revalidate_terminal(run)
    return {"ok": True, "command": "finish", "runId": run.run_id, "outcome": args.outcome,
            "report": report_path.relative_to(run.root).as_posix(),
            "draftPr": "eligible" if args.outcome == "success" else "not eligible"}


def build_parser() -> JsonParser:
    parser = JsonParser(prog="autodev_gate.py", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    init = sub.add_parser("init", help="create an exclusive run")
    init.add_argument("run_id")
    init.add_argument("--task-id", required=True)
    init.add_argument("--task-type", choices=TASK_TYPES, required=True)
    init.add_argument("--source-revision", required=True)
    init.add_argument("--platform", action="append", choices=PLATFORMS)
    init.add_argument("--blast-radius", action="append", required=True)
    add = sub.add_parser("add", help="append a checklist criterion")
    add.add_argument("run_id")
    add.add_argument("--criterion-id", required=True)
    add.add_argument("--text", required=True)
    add.add_argument("--kind", choices=KINDS, required=True)
    add.add_argument("--platform", action="append", choices=PLATFORMS)
    add.add_argument("--evidence-type", choices=EVIDENCE_TYPES, required=True,
                     help="closed validated format: junit, command, or metric (performance only)")
    add.add_argument("--reason")
    freeze_parser = sub.add_parser("freeze", help="freeze the core checklist")
    freeze_parser.add_argument("run_id")
    for command in ("pass", "fail"):
        attempt = sub.add_parser(command, help="append an immutable evidence attempt")
        attempt.add_argument("run_id")
        attempt.add_argument("--criterion", required=True)
        attempt.add_argument("--platform", choices=PLATFORMS, required=True)
        attempt.add_argument("--evidence", required=True,
                             help="repo-relative structured evidence file under this run's allowlist")
        attempt.add_argument("--source-revision", required=True)
        attempt.add_argument("--phase", choices=PHASES, default="observation")
        attempt.add_argument("--scenario-key")
        attempt.add_argument("--scenario-fingerprint")
        attempt.add_argument("--hypothesis")
        attempt.add_argument("--reread-reference")
    observe = sub.add_parser("observe", help="append an immutable bug reproduction diagnostic")
    observe.add_argument("run_id")
    observe.add_argument("--criterion", required=True)
    observe.add_argument("--platform", choices=PLATFORMS, required=True)
    observe.add_argument("--scenario-key", required=True)
    observe.add_argument("--outcome", choices=OBSERVATION_OUTCOMES, required=True)
    observe.add_argument("--evidence", required=True,
                         help="repo-relative structured bug observation JSON")
    observe.add_argument("--source-revision", required=True)
    status_parser = sub.add_parser("status", help="derive and revalidate run status")
    status_parser.add_argument("run_id")
    finish_parser = sub.add_parser("finish", help="write the terminal report and evaluate success")
    finish_parser.add_argument("run_id")
    finish_parser.add_argument("--outcome", choices=OUTCOMES, required=True)
    finish_parser.add_argument("--source-revision", required=True)
    finish_parser.add_argument("--reason")
    finish_parser.add_argument("--build-receipt", action="append", default=[])
    finish_parser.add_argument("--test-receipt", action="append", default=[])
    finish_parser.add_argument("--memory-receipt")
    finish_parser.add_argument("--review-receipt")
    finish_parser.add_argument("--cleanup-receipt")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        require_primitives()
        args = build_parser().parse_args(argv)
        root = repo_root()
        with RuntimeRoots(root) as roots:
            if args.command == "init":
                payload = init_run(args, root, roots)
            else:
                with Run(root, roots, args.run_id) as run:
                    _ = run.manifest
                    terminal = revalidate_terminal(run)
                    if terminal and args.command != "status":
                        raise GateError("terminal run is immutable; only status is permitted", EXIT_GATE)
                    if args.command == "add":
                        payload = add_criterion(args, run)
                    elif args.command == "freeze":
                        payload = freeze(args, run)
                    elif args.command in ("pass", "fail"):
                        payload = record_attempt(args, run, args.command)
                    elif args.command == "observe":
                        payload = record_observation(args, run)
                    elif args.command == "status":
                        payload = status(args, run)
                    elif args.command == "finish":
                        payload = finish(args, run)
                    else:
                        raise GateError("unsupported command", EXIT_USAGE)
                    run.verify_paths()
            roots.verify()
        emit(payload)
        return 0
    except GateError as exc:
        emit({"ok": False, "error": str(exc), "exitCode": exc.code}, sys.stderr)
        return exc.code
    except (OSError, subprocess.SubprocessError) as exc:
        emit({"ok": False, "error": str(exc), "exitCode": EXIT_IO}, sys.stderr)
        return EXIT_IO
    except Exception:
        emit({"ok": False, "error": "unexpected state or internal integrity failure",
              "exitCode": EXIT_INTEGRITY}, sys.stderr)
        return EXIT_INTEGRITY


if __name__ == "__main__":
    sys.exit(main())
