#!/usr/bin/env python3
"""Fail-closed, JSON-only progressive memory helper for Habit Lab AutoDev.

This helper records only memory use and narrow curation proposals.  It has no
device, shell, PR, or arbitrary-command facility.  A normal Git commit is
available only through the explicit, single-record self-patch command.
"""

import argparse
import contextlib
import datetime as dt
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
from pathlib import Path

try:
    import fcntl
except ImportError:  # pragma: no cover - intentionally fail closed
    fcntl = None


SCHEMA_VERSION = 1
EXIT_USAGE = 2
EXIT_IO = 3
EXIT_VALIDATION = 4
EXIT_INTEGRITY = 5
EXIT_GATE = 6
RUN_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}\Z")
ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")
TAG_RE = re.compile(r"[a-z0-9][a-z0-9-]{0,63}\Z")
SHA_RE = re.compile(r"[0-9a-f]{64}\Z")
UTC_RE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z\Z")
OUTCOMES = ("success", "blocked", "failed", "partial")
INITIAL_IDS = ("memory.screen-navigation", "memory.lessons")
LEDGER_NAME = "memory-ledger.json"
CORRECTIONS_NAME = "correction-observations.json"
SELF_PATCHES_NAME = "self-patch-commits.json"
MAX_FILE_BYTES = 2 * 1024 * 1024
LEDGER_EXPECTED = {}
GLOBAL_EXPECTED = {}


class MemoryError(Exception):
    def __init__(self, message, code=EXIT_VALIDATION):
        super().__init__(message)
        self.code = code


class JsonParser(argparse.ArgumentParser):
    def error(self, message):
        raise MemoryError(message, EXIT_USAGE)


def emit(value):
    sys.stdout.write(json.dumps(value, sort_keys=True, ensure_ascii=True) + "\n")


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def sha_bytes(value):
    return hashlib.sha256(value).hexdigest()


def sha_json(value):
    return sha_bytes(canonical(value))


def require_exact(value, fields, label, code=EXIT_INTEGRITY):
    if not isinstance(value, dict) or set(value) != set(fields):
        raise MemoryError("%s schema fields mismatch" % label, code)


def safe_id(value, label="identifier"):
    if not isinstance(value, str) or not ID_RE.fullmatch(value) or value in (".", ".."):
        raise MemoryError("%s must be a safe ASCII identifier" % label)
    return value


def safe_run_id(value):
    if not isinstance(value, str) or not RUN_RE.fullmatch(value) or value in (".", ".."):
        raise MemoryError("run-id must be a safe ASCII identifier")
    return value


def repo_root():
    result = subprocess.run(["git", "rev-parse", "--show-toplevel"], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, text=True, check=False)
    root = result.stdout.strip()
    if result.returncode or not root:
        raise MemoryError("not inside a Git repository", EXIT_IO)
    path = Path(root)
    if not path.is_absolute() or path.is_symlink() or not path.is_dir():
        raise MemoryError("repository root is unsafe", EXIT_INTEGRITY)
    return path.resolve()


def require_primitives():
    if (fcntl is None or not hasattr(fcntl, "flock") or
            any(not hasattr(os, item) for item in ("O_NOFOLLOW", "O_DIRECTORY", "O_EXCL")) or
            os.open not in getattr(os, "supports_dir_fd", set()) or
            os.rename not in getattr(os, "supports_dir_fd", set())):
        raise MemoryError("host lacks required no-follow, exclusive-create, rename, or locking primitives", EXIT_IO)


def checked_relative(value, label="path"):
    if not isinstance(value, str) or not value or value.startswith(("/", "\\")) or "\\" in value:
        raise MemoryError("%s must be a repository-relative POSIX path" % label)
    pure = Path(value)
    if any(part in ("", ".", "..") for part in pure.parts):
        raise MemoryError("%s traverses outside its root" % label)
    return pure


def safe_path(root, relative, allow_missing=False):
    rel = checked_relative(relative)
    current = root
    for index, part in enumerate(rel.parts):
        candidate = current / part
        if candidate.exists() or candidate.is_symlink():
            info = os.lstat(str(candidate))
            if stat.S_ISLNK(info.st_mode):
                raise MemoryError("%s crosses a symlink" % relative, EXIT_INTEGRITY)
            if index < len(rel.parts) - 1 and not stat.S_ISDIR(info.st_mode):
                raise MemoryError("%s has a non-directory ancestor" % relative, EXIT_INTEGRITY)
        elif not allow_missing:
            raise MemoryError("%s does not exist" % relative, EXIT_IO)
        current = candidate
    try:
        current.resolve().relative_to(root)
    except ValueError:
        raise MemoryError("%s escapes repository root" % relative, EXIT_INTEGRITY)
    return current


def open_directory(path):
    if not path.is_absolute():
        raise MemoryError("runtime directory path must be absolute", EXIT_INTEGRITY)
    fd = os.open(os.path.sep, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in path.parts[1:]:
            next_fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        info = os.fstat(fd)
        if not stat.S_ISDIR(info.st_mode):
            raise MemoryError("runtime directory is not regular", EXIT_INTEGRITY)
        return fd
    except MemoryError:
        with contextlib.suppress(OSError):
            os.close(fd)
        raise
    except OSError as exc:
        with contextlib.suppress(OSError):
            os.close(fd)
        raise MemoryError("cannot safely open runtime directory: %s" % exc, EXIT_IO)


def read_bytes_at(directory_fd, name, label, max_bytes=MAX_FILE_BYTES):
    try:
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=directory_fd)
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_size > max_bytes:
            raise MemoryError("%s is not a safe bounded regular file" % label, EXIT_INTEGRITY)
        value = b""
        while len(value) <= max_bytes:
            chunk = os.read(fd, min(1024 * 1024, max_bytes - len(value) + 1))
            if not chunk:
                break
            value += chunk
        final = os.fstat(fd)
        if (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns) != (
                final.st_dev, final.st_ino, final.st_size, final.st_mtime_ns, final.st_ctime_ns):
            raise MemoryError("%s changed while reading" % label, EXIT_INTEGRITY)
    except MemoryError:
        raise
    except OSError as exc:
        raise MemoryError("cannot safely read %s: %s" % (label, exc), EXIT_IO)
    finally:
        with contextlib.suppress(OSError, UnboundLocalError):
            os.close(fd)
    if len(value) > max_bytes:
        raise MemoryError("%s exceeds its byte budget" % label, EXIT_INTEGRITY)
    return value


def read_bytes(path, label, max_bytes=MAX_FILE_BYTES):
    directory_fd = open_directory(path.parent)
    try:
        return read_bytes_at(directory_fd, path.name, label, max_bytes)
    finally:
        os.close(directory_fd)


def read_json(path, label):
    try:
        value = json.loads(read_bytes(path, label).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MemoryError("%s is not valid JSON: %s" % (label, exc), EXIT_INTEGRITY)
    return value


def write_all(fd, data):
    offset = 0
    while offset < len(data):
        written = os.write(fd, data[offset:])
        if written <= 0:
            raise MemoryError("short filesystem write", EXIT_IO)
        offset += written


def atomic_bytes_at(directory_fd, name, encoded):
    temporary = ".%s.%s.tmp" % (name, hashlib.sha256(os.urandom(32)).hexdigest()[:24])
    fd = -1
    try:
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                     dir_fd=directory_fd)
        write_all(fd, encoded)
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


def skill_root(root):
    return safe_path(root, ".agents/skills/habit-lab-autodev")


def artifacts_dir(root, run_id=None):
    rel = ".autodev/artifacts" + (("/" + run_id) if run_id else "")
    path = safe_path(root, rel, allow_missing=True)
    if run_id:
        safe_run_id(run_id)
    return path


def ensure_artifacts_dir(root):
    """Create only the helper-owned `.autodev/artifacts` trailing directories."""
    repo_fd = open_directory(root)
    auto_fd = -1
    artifacts_fd = -1
    try:
        for parent_fd, name in ((repo_fd, ".autodev"),):
            try:
                os.mkdir(name, 0o700, dir_fd=parent_fd)
            except FileExistsError:
                pass
        auto_fd = os.open(".autodev", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=repo_fd)
        try:
            os.mkdir("artifacts", 0o700, dir_fd=auto_fd)
        except FileExistsError:
            pass
        artifacts_fd = os.open("artifacts", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=auto_fd)
        for info in (os.fstat(auto_fd), os.fstat(artifacts_fd)):
            if (not stat.S_ISDIR(info.st_mode) or
                    (hasattr(os, "getuid") and info.st_uid != os.getuid())):
                raise MemoryError("AutoDev artifacts directory identity is invalid", EXIT_INTEGRITY)
        verify_child_identity(repo_fd, ".autodev", auto_fd, "AutoDev runtime root")
        verify_child_identity(auto_fd, "artifacts", artifacts_fd, "AutoDev artifacts root")
    except OSError as exc:
        raise MemoryError("cannot safely create AutoDev artifacts directory: %s" % exc, EXIT_IO)
    finally:
        if artifacts_fd >= 0:
            os.close(artifacts_fd)
        if auto_fd >= 0:
            os.close(auto_fd)
        os.close(repo_fd)
    return artifacts_dir(root)


def ledger_path(root, run_id):
    safe_run_id(run_id)
    return artifacts_dir(root, run_id) / LEDGER_NAME


@contextlib.contextmanager
def locked_directory(path, lock_name=".memory.lock"):
    directory_fd = open_directory(path)
    lock_fd = -1
    try:
        lock_fd = os.open(lock_name, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600,
                          dir_fd=directory_fd)
        fcntl.flock(lock_fd, fcntl.LOCK_EX)
    except OSError as exc:
        os.close(directory_fd)
        raise MemoryError("cannot lock runtime directory: %s" % exc, EXIT_IO)
    try:
        before = os.fstat(directory_fd)
    except OSError as exc:
        with contextlib.suppress(OSError):
            fcntl.flock(lock_fd, fcntl.LOCK_UN)
        with contextlib.suppress(OSError):
            os.close(lock_fd)
        os.close(directory_fd)
        raise MemoryError("cannot inspect locked runtime directory: %s" % exc, EXIT_IO)
    try:
        try:
            yield directory_fd
        except BaseException:
            raise
        else:
            try:
                after = os.fstat(directory_fd)
                if (before.st_dev, before.st_ino, before.st_mode) != (after.st_dev, after.st_ino, after.st_mode):
                    raise MemoryError("runtime directory changed while locked", EXIT_INTEGRITY)
                reopened = open_directory(path)
                try:
                    linked = os.fstat(reopened)
                finally:
                    os.close(reopened)
                if (before.st_dev, before.st_ino, before.st_mode) != (linked.st_dev, linked.st_ino, linked.st_mode):
                    raise MemoryError("runtime directory path was replaced while locked", EXIT_INTEGRITY)
            except OSError as exc:
                raise MemoryError("cannot revalidate locked runtime directory: %s" % exc, EXIT_IO)
    finally:
        if lock_fd >= 0:
            with contextlib.suppress(OSError):
                fcntl.flock(lock_fd, fcntl.LOCK_UN)
            with contextlib.suppress(OSError):
                os.close(lock_fd)
        os.close(directory_fd)


@contextlib.contextmanager
def self_patch_lifecycle(root):
    """Serialize reserve, Git mutation, and rollback for one local checkout."""
    with locked_directory(ensure_artifacts_dir(root), ".self-patch.lifecycle.lock"):
        yield


def read_json_locked(directory_fd, name, label):
    raw = read_bytes_at(directory_fd, name, label)
    try:
        value = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MemoryError("%s is not valid JSON: %s" % (label, exc), EXIT_INTEGRITY)
    return value, sha_bytes(raw)


def write_json_locked(directory_fd, name, value, expected_digest=None, anchor_name=None):
    if expected_digest is not None:
        current = read_bytes_at(directory_fd, name, name)
        if sha_bytes(current) != expected_digest:
            raise MemoryError("concurrent or stale runtime write rejected", EXIT_INTEGRITY)
    encoded = canonical(value) + b"\n"
    atomic_bytes_at(directory_fd, name, encoded)
    digest = sha_bytes(encoded)
    if anchor_name:
        atomic_bytes_at(directory_fd, anchor_name, canonical({"sha256": digest}) + b"\n")
    return digest


def verify_anchor(directory_fd, name, digest_value, label):
    anchor, _ = read_json_locked(directory_fd, name + ".anchor", label + " anchor")
    require_exact(anchor, ("sha256",), label + " anchor")
    if anchor["sha256"] != digest_value:
        raise MemoryError("%s anchor does not match current bytes" % label, EXIT_INTEGRITY)


def catalog_path(root):
    return skill_root(root) / "memory/catalog.json"


def expected_memory_paths(root):
    base = skill_root(root) / "memory"
    paths = {"memory/screen-navigation.md", "memory/lessons.md"}
    nav = base / "nav"
    try:
        for node in sorted(nav.iterdir(), key=lambda item: item.name):
            if node.name == "_SCHEMA.md":
                continue
            if node.is_symlink() or not node.is_dir():
                raise MemoryError("navigation memory contains an unsafe node", EXIT_INTEGRITY)
            for name in ("screen.md", "logic.md"):
                candidate = node / name
                if candidate.is_file() and not candidate.is_symlink():
                    paths.add("memory/nav/%s/%s" % (node.name, name))
    except OSError as exc:
        raise MemoryError("cannot inspect navigation memory: %s" % exc, EXIT_IO)
    return paths


def load_catalog(root):
    data = read_json(catalog_path(root), "memory catalog")
    require_exact(data, ("schemaVersion", "entries"), "memory catalog")
    if data["schemaVersion"] != SCHEMA_VERSION or not isinstance(data["entries"], list):
        raise MemoryError("memory catalog has an invalid schema", EXIT_INTEGRITY)
    entries = []
    for item in data["entries"]:
        require_exact(item, ("id", "path", "tags", "kind", "wordBudget", "routeKey", "destination"),
                      "memory catalog entry")
        safe_id(item["id"], "catalog id")
        path = checked_relative(item["path"], "catalog path").as_posix()
        if not path.startswith("memory/") or path == "memory/nav/_SCHEMA.md":
            raise MemoryError("catalog path is outside reusable memory", EXIT_INTEGRITY)
        if (not isinstance(item["tags"], list) or not item["tags"] or
                len(item["tags"]) != len(set(item["tags"])) or
                any(not isinstance(tag, str) or not TAG_RE.fullmatch(tag) for tag in item["tags"])):
            raise MemoryError("catalog tags must be unique lowercase identifiers", EXIT_INTEGRITY)
        if item["kind"] not in ("navigation-index", "lesson-policy", "nav-screen", "supporting-logic"):
            raise MemoryError("catalog entry kind is unsupported", EXIT_INTEGRITY)
        if type(item["wordBudget"]) is not int or not 1 <= item["wordBudget"] <= 2000:
            raise MemoryError("catalog word budget is invalid", EXIT_INTEGRITY)
        paired = (item["routeKey"], item["destination"])
        if ((item["kind"] == "nav-screen") != (all(isinstance(value, str) and value for value in paired)) or
                (item["kind"] != "nav-screen" and paired != (None, None))):
            raise MemoryError("catalog route key and destination must be paired only for nav screens", EXIT_INTEGRITY)
        safe_path(skill_root(root), path)
        entries.append(item)
    initial_paths = {"memory.screen-navigation": "memory/screen-navigation.md",
                     "memory.lessons": "memory/lessons.md"}
    for entry_id, expected_path in initial_paths.items():
        matches = [item for item in entries if item["id"] == entry_id]
        if len(matches) != 1 or matches[0]["path"] != expected_path:
            raise MemoryError("memory catalog must pin the two initial entry paths", EXIT_INTEGRITY)
    return entries


def word_count(value):
    return len(re.findall(r"\b[\w'-]+\b", value, flags=re.UNICODE))


def read_gate_status(root, run_id):
    """Return the gate's already-validated status for this exact run identity."""
    safe_run_id(run_id)
    gate = skill_root(root) / "scripts/autodev_gate.py"
    command = [sys.executable, str(gate), "status", run_id]
    try:
        result = subprocess.run(command, cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                text=True, check=False, timeout=30)
        payload = json.loads(result.stdout.strip())
    except (OSError, subprocess.SubprocessError, json.JSONDecodeError) as exc:
        raise MemoryError("autodev gate status is unavailable: %s" % exc, EXIT_IO)
    if (result.returncode != 0 or not isinstance(payload, dict) or payload.get("ok") is not True or
            payload.get("runId") != run_id):
        raise MemoryError("autodev gate status did not validate the source run", EXIT_GATE)
    return payload


def active_gate_run(root, run_id):
    """Validate an unsealed matching gate run before its memory receipt exists."""
    payload = read_gate_status(root, run_id)
    if payload.get("terminal") is not None:
        raise MemoryError("matching gate run is no longer active", EXIT_GATE)
    return payload


def status_gate(root, run_id):
    payload = read_gate_status(root, run_id)
    terminal = payload.get("terminal")
    if not isinstance(terminal, dict) or terminal.get("outcome") != "success" or payload.get("passing") is not True:
        raise MemoryError("source run is not sealed terminal success", EXIT_GATE)
    return payload


def resolve_revision(root, value):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-fA-F]{7,64}", value):
        raise MemoryError("source revision must be a hexadecimal Git revision")
    result = subprocess.run(["git", "rev-parse", "--verify", value + "^{commit}"], cwd=str(root),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    revision = result.stdout.strip().lower()
    if result.returncode or not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise MemoryError("source revision does not resolve to a commit", EXIT_IO)
    return revision


def ledger_template(run_id, entries):
    initial = [entry for entry in entries if entry["id"] in INITIAL_IDS]
    if [entry["id"] for entry in initial] != list(INITIAL_IDS):
        raise MemoryError("catalog must retain the two initial memory entries", EXIT_INTEGRITY)
    return {
        "schemaVersion": SCHEMA_VERSION, "runId": run_id, "createdAt": utc_now(), "updatedAt": utc_now(),
        "finalizedAt": None, "initialEntryIds": list(INITIAL_IDS), "plannedEntryIds": [], "loadedEntries": [],
        "reads": [], "writes": [], "durationSeconds": None, "builds": [], "iterations": None,
        "attempts": None, "outcome": None, "platforms": [], "flakySteps": [], "gateRun": None,
        "gateStatusSha256": None,
    }


LEDGER_FIELDS = ("schemaVersion", "runId", "createdAt", "updatedAt", "finalizedAt", "initialEntryIds",
                 "plannedEntryIds", "loadedEntries", "reads", "writes", "durationSeconds", "builds",
                 "iterations", "attempts", "outcome", "platforms", "flakySteps", "gateRun", "gateStatusSha256")


def validate_ledger(value, run_id):
    require_exact(value, LEDGER_FIELDS, "memory ledger")
    if value["schemaVersion"] != SCHEMA_VERSION or value["runId"] != run_id:
        raise MemoryError("memory ledger identity is invalid", EXIT_INTEGRITY)
    if value["initialEntryIds"] != list(INITIAL_IDS):
        raise MemoryError("memory ledger initial load contract changed", EXIT_INTEGRITY)
    for key in ("createdAt", "updatedAt"):
        if not isinstance(value[key], str) or not UTC_RE.fullmatch(value[key]):
            raise MemoryError("memory ledger %s is not a strict UTC timestamp" % key, EXIT_INTEGRITY)
    for key in ("plannedEntryIds", "reads", "writes", "loadedEntries", "builds", "platforms", "flakySteps"):
        if not isinstance(value[key], list):
            raise MemoryError("memory ledger %s is invalid" % key, EXIT_INTEGRITY)
    if value["outcome"] not in (None,) + OUTCOMES:
        raise MemoryError("memory ledger outcome is invalid", EXIT_INTEGRITY)
    if value["outcome"] is None and value["finalizedAt"] is not None:
        raise MemoryError("unsealed memory ledger has a final timestamp", EXIT_INTEGRITY)
    if value["outcome"] is not None and not isinstance(value["finalizedAt"], str):
        raise MemoryError("terminal memory ledger lacks final timestamp", EXIT_INTEGRITY)
    if value["finalizedAt"] is not None and not UTC_RE.fullmatch(value["finalizedAt"]):
        raise MemoryError("memory ledger final timestamp is invalid", EXIT_INTEGRITY)
    loaded_ids = []
    for item in value["loadedEntries"]:
        require_exact(item, ("entryId", "path", "sha256", "loadedAt"), "loaded memory entry")
        safe_id(item["entryId"], "loaded entry id")
        checked_relative(item["path"], "loaded memory path")
        if (not isinstance(item["sha256"], str) or not SHA_RE.fullmatch(item["sha256"]) or
                not isinstance(item["loadedAt"], str) or not UTC_RE.fullmatch(item["loadedAt"])):
            raise MemoryError("loaded memory digest is invalid", EXIT_INTEGRITY)
        loaded_ids.append(item["entryId"])
    if len(loaded_ids) != len(set(loaded_ids)) or not set(INITIAL_IDS).issubset(loaded_ids):
        raise MemoryError("memory ledger has invalid loaded entries", EXIT_INTEGRITY)
    planned = value["plannedEntryIds"]
    if (len(planned) > 3 or len(planned) != len(set(planned)) or
            any(not isinstance(item, str) or not ID_RE.fullmatch(item) or item in INITIAL_IDS for item in planned)):
        raise MemoryError("memory ledger plan is invalid", EXIT_INTEGRITY)
    non_initial_loaded = set(loaded_ids) - set(INITIAL_IDS)
    if len(non_initial_loaded) > 3 or not non_initial_loaded.issubset(set(planned)):
        raise MemoryError("memory ledger loads escape its final plan", EXIT_INTEGRITY)
    for label in ("reads", "writes"):
        seen = set()
        for item in value[label]:
            require_exact(item, ("path", "sha256"), "memory ledger %s record" % label)
            relative = checked_relative(item["path"], "memory ledger %s path" % label).as_posix()
            if not isinstance(item["sha256"], str) or not SHA_RE.fullmatch(item["sha256"]):
                raise MemoryError("memory ledger %s digest is invalid" % label, EXIT_INTEGRITY)
            if relative in seen:
                raise MemoryError("memory ledger has duplicate %s records" % label, EXIT_INTEGRITY)
            seen.add(relative)
    builds = set()
    for item in value["builds"]:
        require_exact(item, ("name", "status"), "memory ledger build")
        parsed = parse_build("%s:%s" % (item["name"], item["status"]))
        if parsed["name"] in builds:
            raise MemoryError("memory ledger has duplicate build names", EXIT_INTEGRITY)
        builds.add(parsed["name"])
    if (type(value["durationSeconds"]) not in (int, type(None)) or
            type(value["iterations"]) not in (int, type(None)) or
            type(value["attempts"]) not in (int, type(None)) or
            any(item is not None and item < 0 for item in (value["durationSeconds"], value["iterations"], value["attempts"]))):
        raise MemoryError("memory ledger counters are invalid", EXIT_INTEGRITY)
    if (len(value["platforms"]) != len(set(value["platforms"])) or
            any(item not in ("android", "ios") for item in value["platforms"]) or
            len(value["flakySteps"]) != len(set(value["flakySteps"])) or
            any(not isinstance(item, str) or not item for item in value["flakySteps"])):
        raise MemoryError("memory ledger platforms or flaky steps are invalid", EXIT_INTEGRITY)
    if value["gateRun"] is not None:
        safe_run_id(value["gateRun"])
    if value["gateStatusSha256"] is not None and (not isinstance(value["gateStatusSha256"], str) or
                                                   not SHA_RE.fullmatch(value["gateStatusSha256"])):
        raise MemoryError("memory ledger gate status digest is invalid", EXIT_INTEGRITY)
    return value


def load_ledger(root, run_id):
    path = ledger_path(root, run_id)
    try:
        with locked_directory(path.parent) as directory_fd:
            value, digest_value = read_json_locked(directory_fd, LEDGER_NAME, "memory ledger")
            verify_anchor(directory_fd, LEDGER_NAME, digest_value, "memory ledger")
    except MemoryError as exc:
        if exc.code == EXIT_IO:
            raise MemoryError("memory run has not been started", EXIT_IO)
        raise
    ledger = validate_ledger(value, run_id)
    LEDGER_EXPECTED[id(ledger)] = digest_value
    return ledger


def save_ledger(root, ledger):
    ledger["updatedAt"] = utc_now()
    expected = LEDGER_EXPECTED.get(id(ledger))
    if expected is None:
        raise MemoryError("memory ledger save lacks a locked prior digest", EXIT_INTEGRITY)
    path = ledger_path(root, ledger["runId"])
    with locked_directory(path.parent) as directory_fd:
        verify_anchor(directory_fd, LEDGER_NAME, expected, "memory ledger")
        digest = write_json_locked(directory_fd, LEDGER_NAME, ledger, expected, LEDGER_NAME + ".anchor")
    LEDGER_EXPECTED[id(ledger)] = digest
    return digest


def verify_child_identity(parent_fd, name, child_fd, label):
    try:
        linked = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
    except OSError as exc:
        raise MemoryError("%s was removed or replaced: %s" % (label, exc), EXIT_INTEGRITY)
    opened = os.fstat(child_fd)
    if (linked.st_dev, linked.st_ino, linked.st_mode) != (opened.st_dev, opened.st_ino, opened.st_mode):
        raise MemoryError("%s was removed or replaced" % label, EXIT_INTEGRITY)


def validate_gate_manifest(manifest, run_id):
    legacy = ("schemaVersion", "runId", "taskId", "taskType", "sourceRevision",
              "requestedPlatforms", "blastRadius", "createdAt", "deviceLeases")
    current = legacy + ("memoryReceiptContract",)
    require_exact(manifest, current if "memoryReceiptContract" in manifest else legacy, "gate manifest")
    if (type(manifest["schemaVersion"]) is not int or manifest["schemaVersion"] != SCHEMA_VERSION or
            manifest["runId"] != run_id or manifest["taskType"] not in ("feature", "bug", "perf") or
            not isinstance(manifest["sourceRevision"], str) or not re.fullmatch(r"[0-9a-f]{40,64}", manifest["sourceRevision"]) or
            not isinstance(manifest["createdAt"], str) or not UTC_RE.fullmatch(manifest["createdAt"]) or
            manifest["deviceLeases"] != []):
        raise MemoryError("gate manifest identity is invalid", EXIT_INTEGRITY)
    safe_id(manifest["taskId"], "gate manifest task id")
    platforms = manifest["requestedPlatforms"]
    if (not isinstance(platforms, list) or not platforms or platforms != sorted(platforms) or
            len(platforms) != len(set(platforms)) or any(item not in ("android", "ios") for item in platforms)):
        raise MemoryError("gate manifest platforms are invalid", EXIT_INTEGRITY)
    if (not isinstance(manifest["blastRadius"], list) or not manifest["blastRadius"] or
            any(not isinstance(item, str) or not item.strip() for item in manifest["blastRadius"])):
        raise MemoryError("gate manifest blast radius is invalid", EXIT_INTEGRITY)
    if "memoryReceiptContract" in manifest and manifest["memoryReceiptContract"] != 2:
        raise MemoryError("gate manifest memory receipt contract is invalid", EXIT_INTEGRITY)


@contextlib.contextmanager
def verified_gate_attachment(root, run_id, artifact_fd):
    """Hold the gate's per-run lock while binding an existing artifact dir to its owner."""
    repo_fd = -1
    auto_fd = -1
    state_root_fd = -1
    state_fd = -1
    gate_lock_fd = -1
    try:
        repo_fd = open_directory(root)
        auto_fd = os.open(".autodev", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=repo_fd)
        state_root_fd = os.open("state", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=auto_fd)
        state_fd = os.open(run_id, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=state_root_fd)
        auto_info = os.fstat(auto_fd)
        state_root_info = os.fstat(state_root_fd)
        state_info = os.fstat(state_fd)
        artifact_info = os.fstat(artifact_fd)
        if (any(not stat.S_ISDIR(info.st_mode) for info in (auto_info, state_root_info, state_info, artifact_info)) or
                (hasattr(os, "getuid") and any(info.st_uid != os.getuid()
                                                for info in (auto_info, state_root_info, state_info, artifact_info)))):
            raise MemoryError("gate state or artifact directory identity is invalid", EXIT_INTEGRITY)
        gate_lock_fd = os.open(".lock", os.O_RDONLY | os.O_NOFOLLOW, dir_fd=state_fd)
        lock_info = os.fstat(gate_lock_fd)
        if not stat.S_ISREG(lock_info.st_mode) or (hasattr(os, "getuid") and lock_info.st_uid != os.getuid()):
            raise MemoryError("gate run lock identity is invalid", EXIT_INTEGRITY)
        fcntl.flock(gate_lock_fd, fcntl.LOCK_SH)
        def revalidate_attachment():
            verify_child_identity(repo_fd, ".autodev", auto_fd, "AutoDev runtime root")
            verify_child_identity(auto_fd, "state", state_root_fd, "gate state root")
            verify_child_identity(state_root_fd, run_id, state_fd, "gate state run")

        revalidate_attachment()
        owner, _ = read_json_locked(state_fd, "owner.json", "gate run owner")
        require_exact(owner, ("schemaVersion", "runId", "stateDevice", "stateInode", "artifactDevice", "artifactInode"),
                      "gate run owner")
        if (owner.get("schemaVersion") != SCHEMA_VERSION or owner.get("runId") != run_id or
                any(type(owner.get(key)) is not int for key in ("stateDevice", "stateInode", "artifactDevice", "artifactInode")) or
                owner["stateDevice"] != state_info.st_dev or owner["stateInode"] != state_info.st_ino or
                owner["artifactDevice"] != artifact_info.st_dev or owner["artifactInode"] != artifact_info.st_ino):
            raise MemoryError("gate owner does not bind this artifact run", EXIT_INTEGRITY)
        manifest, _ = read_json_locked(state_fd, "manifest.json", "gate manifest")
        manifest_anchor, _ = read_json_locked(state_fd, "manifest.anchor", "gate manifest anchor")
        require_exact(manifest_anchor, ("manifestDigest",), "gate manifest anchor")
        if manifest_anchor["manifestDigest"] != sha_json(manifest):
            raise MemoryError("gate manifest anchor does not match", EXIT_INTEGRITY)
        validate_gate_manifest(manifest, run_id)
        revalidate_attachment.source_revision = manifest["sourceRevision"]
        yield revalidate_attachment
        revalidate_attachment()
    except OSError as exc:
        raise MemoryError("cannot safely attach to gate run: %s" % exc, EXIT_IO)
    finally:
        if gate_lock_fd >= 0:
            with contextlib.suppress(OSError):
                fcntl.flock(gate_lock_fd, fcntl.LOCK_UN)
            with contextlib.suppress(OSError):
                os.close(gate_lock_fd)
        if state_fd >= 0:
            os.close(state_fd)
        if state_root_fd >= 0:
            os.close(state_root_fd)
        if auto_fd >= 0:
            os.close(auto_fd)
        if repo_fd >= 0:
            os.close(repo_fd)


def gate_manifest_source_revision(root, run_id):
    artifact_fd = open_directory(artifacts_dir(root, run_id))
    try:
        with verified_gate_attachment(root, run_id, artifact_fd) as revalidate_attachment:
            revalidate_attachment()
            return revalidate_attachment.source_revision
    finally:
        os.close(artifact_fd)


def create_initial_ledger_at(base_fd, run_id, run_fd, ledger, revalidate_attachment=None):
    for name in (LEDGER_NAME, LEDGER_NAME + ".anchor", ".memory.lock"):
        try:
            os.stat(name, dir_fd=run_fd, follow_symlinks=False)
        except FileNotFoundError:
            continue
        except OSError as exc:
            raise MemoryError("cannot inspect existing memory run artifact: %s" % exc, EXIT_IO)
        raise MemoryError("memory run has a ledger, anchor, or lock conflict", EXIT_GATE)
    verify_child_identity(base_fd, run_id, run_fd, "memory artifact run")
    if revalidate_attachment is not None:
        revalidate_attachment()
    try:
        memory_lock_fd = os.open(".memory.lock", os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                                 0o600, dir_fd=run_fd)
    except OSError as exc:
        raise MemoryError("cannot exclusively acquire memory run lock: %s" % exc, EXIT_IO)
    try:
        fcntl.flock(memory_lock_fd, fcntl.LOCK_EX)
        verify_child_identity(base_fd, run_id, run_fd, "memory artifact run")
        if revalidate_attachment is not None:
            revalidate_attachment()
        encoded = canonical(ledger) + b"\n"
        fd = os.open(LEDGER_NAME, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                     dir_fd=run_fd)
        try:
            write_all(fd, encoded)
            os.fsync(fd)
        finally:
            os.close(fd)
        digest = sha_bytes(encoded)
        fd = os.open(LEDGER_NAME + ".anchor", os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                     0o600, dir_fd=run_fd)
        try:
            write_all(fd, canonical({"sha256": digest}) + b"\n")
            os.fsync(fd)
        finally:
            os.close(fd)
        verify_child_identity(base_fd, run_id, run_fd, "memory artifact run")
        if revalidate_attachment is not None:
            revalidate_attachment()
        os.fsync(run_fd)
        return digest
    finally:
        with contextlib.suppress(OSError):
            fcntl.flock(memory_lock_fd, fcntl.LOCK_UN)
        with contextlib.suppress(OSError):
            os.close(memory_lock_fd)


def create_ledger(root, ledger):
    run_id = ledger["runId"]
    base = ensure_artifacts_dir(root)
    with locked_directory(base) as base_fd:
        attached = False
        try:
            os.mkdir(run_id, 0o700, dir_fd=base_fd)
        except FileExistsError:
            attached = True
        try:
            run_fd = os.open(run_id, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=base_fd)
        except OSError as exc:
            raise MemoryError("cannot safely create memory run: %s" % exc, EXIT_IO)
        try:
            if attached:
                with verified_gate_attachment(root, run_id, run_fd) as revalidate_attachment:
                    digest = create_initial_ledger_at(base_fd, run_id, run_fd, ledger, revalidate_attachment)
            else:
                digest = create_initial_ledger_at(base_fd, run_id, run_fd, ledger)
        finally:
            os.close(run_fd)
    LEDGER_EXPECTED[id(ledger)] = digest
    return digest


def create_run_artifact(root, run_id, name, value):
    with locked_directory(artifacts_dir(root, run_id)) as directory_fd:
        encoded = canonical(value) + b"\n"
        try:
            fd = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                         dir_fd=directory_fd)
        except FileExistsError:
            raise MemoryError("run artifact already exists and cannot be overwritten", EXIT_GATE)
        except OSError as exc:
            raise MemoryError("cannot exclusively create run artifact: %s" % exc, EXIT_IO)
        try:
            write_all(fd, encoded)
            os.fsync(fd)
        finally:
            os.close(fd)
        os.fsync(directory_fd)
    return sha_bytes(encoded)


def create_json_file(path, value, label):
    directory_fd = open_directory(path.parent)
    encoded = canonical(value) + b"\n"
    try:
        fd = os.open(path.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                     dir_fd=directory_fd)
    except FileExistsError:
        os.close(directory_fd)
        raise MemoryError("%s already exists" % label, EXIT_GATE)
    except OSError as exc:
        os.close(directory_fd)
        raise MemoryError("cannot exclusively create %s: %s" % (label, exc), EXIT_IO)
    try:
        write_all(fd, encoded)
        os.fsync(fd)
    finally:
        os.close(fd)
        os.close(directory_fd)
    return sha_bytes(encoded)


def entry_by_id(entries, entry_id):
    matches = [item for item in entries if item["id"] == entry_id]
    if len(matches) != 1:
        raise MemoryError("catalog entry is unavailable", EXIT_INTEGRITY)
    return matches[0]


def record_load(root, ledger, entry):
    source = safe_path(skill_root(root), entry["path"])
    content = read_bytes(source, entry["path"])
    digest = sha_bytes(content)
    if word_count(content.decode("utf-8")) > entry["wordBudget"]:
        raise MemoryError("memory entry exceeds its catalog word budget", EXIT_INTEGRITY)
    record = {"entryId": entry["id"], "path": entry["path"], "sha256": digest, "loadedAt": utc_now()}
    existing = {item["entryId"]: item for item in ledger["loadedEntries"]}
    if entry["id"] in existing:
        if existing[entry["id"]]["sha256"] != digest:
            raise MemoryError("previously loaded memory content changed during this run", EXIT_INTEGRITY)
    else:
        ledger["loadedEntries"].append(record)
        ledger["reads"].append({"path": entry["path"], "sha256": digest})
    return content.decode("utf-8"), digest


def successful_history(root, current_run):
    base = artifacts_dir(root)
    if not base.exists():
        return []
    if base.is_symlink() or not base.is_dir():
        raise MemoryError("artifact history directory is unsafe", EXIT_INTEGRITY)
    result = []
    for child in sorted(base.iterdir(), key=lambda item: item.name):
        if child.name == current_run or child.is_symlink() or not child.is_dir() or not RUN_RE.fullmatch(child.name):
            continue
        file = child / LEDGER_NAME
        if not file.exists():
            continue
        ledger = load_ledger(root, child.name)
        if ledger["outcome"] == "success":
            result.append(ledger)
    return result


def classifications(entries, ledger, history):
    loaded = {item["entryId"] for item in ledger["loadedEntries"]}
    counts = {}
    for previous in history:
        for item in previous["loadedEntries"]:
            counts[item["entryId"]] = counts.get(item["entryId"], 0) + 1
    values = {}
    for entry in entries:
        if entry["id"] in loaded:
            values[entry["id"]] = "loaded"
        elif not history:
            values[entry["id"]] = "unknown"
        elif counts.get(entry["id"], 0) >= 2:
            values[entry["id"]] = "hot"
        elif counts.get(entry["id"], 0) == 1:
            values[entry["id"]] = "stale"
        else:
            values[entry["id"]] = "never"
    return values, counts


def lint_catalog(root, entries):
    errors = []
    ids = [item["id"] for item in entries]
    paths = [item["path"] for item in entries]
    if len(ids) != len(set(ids)):
        errors.append("catalog ids must be unique")
    if len(paths) != len(set(paths)):
        errors.append("catalog paths must be unique")
    expected = expected_memory_paths(root)
    if set(paths) != expected:
        errors.append("catalog paths must exactly cover reusable memory nodes")
    toc = read_bytes(safe_path(skill_root(root), "memory/screen-navigation.md"), "screen navigation index").decode("utf-8")
    for entry in entries:
        toc_path = entry["path"].removeprefix("memory/")
        if entry["kind"] in ("nav-screen", "supporting-logic") and toc_path not in toc:
            errors.append("screen navigation index omits catalog path %s" % entry["path"])
    route_contract = read_bytes(safe_path(root, "shared/src/commonMain/kotlin/com/denis/habitlab/shared/app/Navigation3AppHost.kt"),
                                "navigation route contract").decode("utf-8")
    route_destinations = {}
    destinations = {}
    for entry in entries:
        text = read_bytes(safe_path(skill_root(root), entry["path"]), entry["path"]).decode("utf-8")
        if word_count(text) > entry["wordBudget"]:
            errors.append("%s exceeds word budget" % entry["path"])
        if re.search(r"\b(?:diary|journal|today i|yesterday i|worked on|my machine)\b", text, re.I):
            errors.append("%s has diary or journal wording" % entry["path"])
        if entry["kind"] == "nav-screen":
            key, destination = entry["routeKey"], entry["destination"]
            if route_destinations.setdefault(key, destination) != destination:
                errors.append("contradictory route key destination for %s" % key)
            if destinations.setdefault(destination, key) != key:
                errors.append("contradictory destination route key for %s" % destination)
            declaration = r"data\s+(?:object|class)\s+%s\b" % re.escape(key)
            if destination != "AppDestination.%s" % key or not re.search(declaration, route_contract):
                errors.append("%s conflicts with the structured route contract" % entry["path"])
    return errors


def lesson_records(text):
    sections = re.split(r"(?m)^##\s+Lesson(?:\s*:\s*|\s+).*$", text)
    return sections[1:]


def lint_lessons(root):
    text = read_bytes(safe_path(skill_root(root), "memory/lessons.md"), "memory/lessons.md").decode("utf-8")
    errors, facts, claims = [], [], {}
    for number, record in enumerate(lesson_records(text), 1):
        fields = {match.group(1).strip().lower(): match.group(2).strip()
                  for match in re.finditer(r"(?m)^[-*]?\s*([A-Za-z][A-Za-z -]+):\s*(.+)$", record)}
        required = ("trigger", "scope", "fact", "evidence", "next time", "invalidation")
        missing = [field for field in required if not fields.get(field)]
        if not (fields.get("verification date") or fields.get("date")):
            missing.append("verification date")
        if missing:
            errors.append("lesson %d lacks %s" % (number, ", ".join(missing)))
        if fields.get("fact"):
            facts.append(re.sub(r"\s+", " ", fields["fact"].strip().lower()))
        if fields.get("trigger") and fields.get("scope") and fields.get("fact"):
            key = (re.sub(r"\s+", " ", fields["trigger"].strip().lower()),
                   re.sub(r"\s+", " ", fields["scope"].strip().lower()))
            fact = re.sub(r"\s+", " ", fields["fact"].strip().lower())
            if key in claims and claims[key] != fact:
                errors.append("lesson %d contradicts a fact for the same trigger and scope" % number)
            claims[key] = fact
        if re.search(r"\b(?:diary|journal|today i|yesterday i|worked on|my machine)\b", record, re.I):
            errors.append("lesson %d has diary or journal wording" % number)
    if len(facts) != len(set(facts)):
        errors.append("lesson facts contain duplicate claims")
    return errors


def lint_all(root, entries=None):
    entries = entries if entries is not None else load_catalog(root)
    errors = lint_catalog(root, entries) + lint_lessons(root)
    return sorted(set(errors))


def parse_build(value):
    if not isinstance(value, str) or value.count(":") != 1:
        raise MemoryError("build must use name:pass|fail|skipped")
    name, status = value.split(":", 1)
    if not ID_RE.fullmatch(name) or status not in ("pass", "fail", "skipped"):
        raise MemoryError("build must use safe-name:pass|fail|skipped")
    return {"name": name, "status": status}


def capture_paths(root, values, label):
    if len(values) != len(set(values)):
        raise MemoryError("duplicate %s values are not allowed" % label)
    records = []
    paths = set()
    for value in sorted(values):
        relative = checked_relative(value, label).as_posix()
        if relative in paths:
            raise MemoryError("duplicate %s values are not allowed" % label)
        paths.add(relative)
        path = safe_path(root, relative)
        records.append({"path": relative, "sha256": sha_bytes(read_bytes(path, relative))})
    return records


def terminal_ledgers(root):
    base = artifacts_dir(root)
    items = []
    if not base.exists():
        return items
    for child in sorted(base.iterdir(), key=lambda item: item.name):
        if child.is_symlink() or not child.is_dir() or not RUN_RE.fullmatch(child.name):
            continue
        file = child / LEDGER_NAME
        if file.exists():
            ledger = load_ledger(root, child.name)
            if ledger["outcome"] is not None:
                items.append(ledger)
    return sorted(items, key=lambda item: (item["finalizedAt"], item["runId"]))


def consolidation_advisory(root, lint_errors):
    runs = terminal_ledgers(root)
    reason = None
    if lint_errors:
        reason = "lint-pressure"
    elif runs and len(runs) % 5 == 0:
        reason = "terminal-run-milestone"
    if reason is None:
        return None
    return {"kind": "consolidation-advisory", "reason": reason, "terminalRuns": len(runs),
            "action": "Review recurring memory use and propose curated records; no automatic rewrite occurred."}


PROPOSAL_FIELDS = ("schemaVersion", "proposalId", "kind", "claim", "trigger", "nextTime", "invalidation")


def parse_proposal(value):
    try:
        proposal = json.loads(value)
    except json.JSONDecodeError as exc:
        raise MemoryError("correction proposal is not valid JSON: %s" % exc)
    require_exact(proposal, PROPOSAL_FIELDS, "correction proposal")
    if (proposal["schemaVersion"] != SCHEMA_VERSION or proposal["kind"] != "code-correction" or
            not all(isinstance(proposal[key], str) and proposal[key].strip()
                    for key in ("proposalId", "claim", "trigger", "nextTime", "invalidation"))):
        raise MemoryError("correction proposal is incomplete")
    safe_id(proposal["proposalId"], "proposal id")
    return proposal


CORRECTION_FIELDS = ("schemaVersion", "proposal", "runId", "gateStatusSha256", "recordedAt", "digest")


def correction_store_path(root):
    return artifacts_dir(root) / CORRECTIONS_NAME


def load_global_json(root, name, label, fallback):
    with locked_directory(ensure_artifacts_dir(root)) as directory_fd:
        try:
            value, digest_value = read_json_locked(directory_fd, name, label)
        except MemoryError as exc:
            if exc.code != EXIT_IO:
                raise
            GLOBAL_EXPECTED[name] = None
            return fallback
        verify_anchor(directory_fd, name, digest_value, label)
    GLOBAL_EXPECTED[name] = digest_value
    return value


def save_global_json(root, name, value, label):
    expected = GLOBAL_EXPECTED.get(name)
    with locked_directory(ensure_artifacts_dir(root)) as directory_fd:
        if expected is None:
            try:
                read_bytes_at(directory_fd, name, label)
            except MemoryError as exc:
                if exc.code != EXIT_IO:
                    raise
            else:
                raise MemoryError("concurrent global runtime write rejected", EXIT_INTEGRITY)
            encoded = canonical(value) + b"\n"
            fd = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600,
                         dir_fd=directory_fd)
            try:
                write_all(fd, encoded)
                os.fsync(fd)
            finally:
                os.close(fd)
            digest_value = sha_bytes(encoded)
            atomic_bytes_at(directory_fd, name + ".anchor", canonical({"sha256": digest_value}) + b"\n")
        else:
            verify_anchor(directory_fd, name, expected, label)
            digest_value = write_json_locked(directory_fd, name, value, expected, name + ".anchor")
    GLOBAL_EXPECTED[name] = digest_value
    return digest_value


def validate_proposal_value(proposal):
    require_exact(proposal, PROPOSAL_FIELDS, "correction proposal")
    if (proposal["schemaVersion"] != SCHEMA_VERSION or proposal["kind"] != "code-correction" or
            not all(isinstance(proposal[key], str) and proposal[key].strip()
                    for key in ("proposalId", "claim", "trigger", "nextTime", "invalidation"))):
        raise MemoryError("correction proposal is incomplete", EXIT_INTEGRITY)
    safe_id(proposal["proposalId"], "proposal id")
    return proposal


def load_corrections(root):
    data = load_global_json(root, CORRECTIONS_NAME, "correction observations", [])
    if not isinstance(data, list):
        raise MemoryError("correction observations are not a JSON list", EXIT_INTEGRITY)
    records = []
    gate_cache = {}
    pairs, proposals = set(), {}
    for item in data:
        require_exact(item, CORRECTION_FIELDS, "correction observation")
        proposal = validate_proposal_value(item["proposal"])
        if item["digest"] != sha_json({key: item[key] for key in CORRECTION_FIELDS if key != "digest"}):
            raise MemoryError("correction observation digest is invalid", EXIT_INTEGRITY)
        safe_run_id(item["runId"])
        if not isinstance(item["recordedAt"], str) or not UTC_RE.fullmatch(item["recordedAt"]):
            raise MemoryError("correction observation timestamp is invalid", EXIT_INTEGRITY)
        if not isinstance(item["gateStatusSha256"], str) or not SHA_RE.fullmatch(item["gateStatusSha256"]):
            raise MemoryError("correction observation gate digest is invalid", EXIT_INTEGRITY)
        pair = (proposal["proposalId"], item["runId"])
        if pair in pairs:
            raise MemoryError("correction observations duplicate one proposal/run pair", EXIT_INTEGRITY)
        pairs.add(pair)
        prior = proposals.setdefault(proposal["proposalId"], canonical(proposal))
        if prior != canonical(proposal):
            raise MemoryError("matching correction proposal IDs diverge", EXIT_INTEGRITY)
        gate, gate_digest = source_run_success(root, item["runId"], gate_cache)
        if item["gateStatusSha256"] != gate_digest:
            raise MemoryError("correction observation gate status was tampered or became invalid", EXIT_INTEGRITY)
        records.append(item)
    return records


def source_run_success(root, run_id, gate_cache=None):
    ledger = load_ledger(root, run_id)
    if ledger["outcome"] != "success":
        raise MemoryError("correction source is not this run's finalized success", EXIT_GATE)
    if gate_cache is not None and run_id in gate_cache:
        gate, digest = gate_cache[run_id]
    else:
        gate = status_gate(root, run_id)
        digest = sha_json(gate)
        if gate_cache is not None:
            gate_cache[run_id] = (gate, digest)
    if ledger["gateStatusSha256"] is not None and ledger["gateStatusSha256"] != digest:
        raise MemoryError("source gate evidence no longer matches its terminal memory ledger", EXIT_INTEGRITY)
    return gate, digest


def structural_instruction_contract(root, source_revision, checked_revision):
    result = subprocess.run(["git", "diff", "--name-only", source_revision, checked_revision, "--"],
                            cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, check=False)
    if result.returncode:
        raise MemoryError("cannot determine structural instruction changes", EXIT_IO)
    prefix = ".agents/skills/habit-lab-autodev/memory/instructions/"
    paths = sorted(line for line in result.stdout.splitlines() if line.startswith(prefix))
    if len(paths) > 1:
        raise MemoryError("at most one structural instruction change may be sealed", EXIT_GATE)
    if paths:
        isolated = subprocess.run(["git", "diff-tree", "--no-commit-id", "--name-only", "-r", checked_revision],
                                  cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                  text=True, check=False)
        isolated_paths = sorted(line for line in isolated.stdout.splitlines() if line)
        if isolated.returncode or isolated_paths != paths:
            raise MemoryError("structural instruction change is not an isolated commit", EXIT_GATE)
    records = [{"path": path, "sha256": sha_bytes(commit_blob(root, checked_revision, path,
                                                                 "structural instruction record"))}
               for path in paths]
    return paths, sha_json({"sourceRevision": checked_revision, "paths": records})


def catalog_paths_at_revision(root, revision):
    try:
        catalog = json.loads(commit_blob(root, revision,
                                         ".agents/skills/habit-lab-autodev/memory/catalog.json",
                                         "memory catalog").decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise MemoryError("checked memory catalog is invalid: %s" % exc, EXIT_GATE)
    require_exact(catalog, ("schemaVersion", "entries"), "checked memory catalog")
    if catalog["schemaVersion"] != SCHEMA_VERSION or not isinstance(catalog["entries"], list):
        raise MemoryError("checked memory catalog schema is invalid", EXIT_GATE)
    mapped = {}
    for item in catalog["entries"]:
        require_exact(item, ("id", "path", "tags", "kind", "wordBudget", "routeKey", "destination"),
                      "checked memory catalog entry")
        if (not isinstance(item["id"], str) or not ID_RE.fullmatch(item["id"]) or
                not isinstance(item["path"], str) or not item["path"].startswith("memory/") or
                item["id"] in mapped):
            raise MemoryError("checked memory catalog entry is invalid", EXIT_GATE)
        mapped[item["id"]] = item["path"]
    if (mapped.get("memory.screen-navigation") != "memory/screen-navigation.md" or
            mapped.get("memory.lessons") != "memory/lessons.md"):
        raise MemoryError("checked memory catalog does not pin initial entry paths", EXIT_GATE)
    return mapped


def preseal_memory_records(root, run_id, ledger, revision):
    if any(type(ledger[field]) is not int or ledger[field] < 0
           for field in ("durationSeconds", "iterations", "attempts")):
        raise MemoryError("successful memory receipt needs finalized counters", EXIT_GATE)
    catalog = catalog_paths_at_revision(root, revision)
    for item in ledger["loadedEntries"]:
        if catalog.get(item["entryId"]) != item["path"]:
            raise MemoryError("loaded memory entry does not match checked catalog", EXIT_GATE)
        source = ".agents/skills/habit-lab-autodev/" + item["path"]
        if sha_bytes(commit_blob(root, revision, source, "loaded memory entry")) != item["sha256"]:
            raise MemoryError("loaded memory entry does not match checked bytes", EXIT_GATE)
    for label in ("reads", "writes"):
        for item in ledger[label]:
            path = item["path"]
            artifact_prefixes = (".autodev/artifacts/%s/" % run_id, "build/maestro/%s/" % run_id)
            if path.startswith(artifact_prefixes):
                actual = sha_bytes(read_bytes(safe_path(root, path), "memory %s artifact" % label))
            else:
                source = ".agents/skills/habit-lab-autodev/" + path if path.startswith("memory/") else path
                actual = sha_bytes(commit_blob(root, revision, source, "memory %s source" % label))
            if actual != item["sha256"]:
                raise MemoryError("memory %s does not match checked bytes" % label, EXIT_GATE)


def memory_receipt(root, run_id, revision, eval_receipt):
    ledger = load_ledger(root, run_id)
    if ledger["outcome"] != "success":
        raise MemoryError("memory receipt requires a finalized successful memory run", EXIT_GATE)
    if revision != head_revision(root):
        raise MemoryError("memory receipt source revision must be the current HEAD", EXIT_GATE)
    preseal_memory_records(root, run_id, ledger, revision)
    source_revision = gate_manifest_source_revision(root, run_id)
    instruction_paths, structural_digest = structural_instruction_contract(root, source_revision, revision)
    structure_changed = bool(instruction_paths)
    instruction_patch_count = len(instruction_paths)
    if structure_changed:
        if not eval_receipt:
            raise MemoryError("structure-changing memory receipt requires a passing evaluation receipt", EXIT_GATE)
        eval_digest = validate_eval_receipt(root, run_id, eval_receipt, revision, structural_digest)
        evaluation = {"path": eval_receipt, "sha256": eval_digest, "status": "pass"}
    elif eval_receipt:
        raise MemoryError("non-structural memory receipt cannot include an evaluation receipt", EXIT_GATE)
    else:
        evaluation = None
    lint_errors = lint_all(root)
    if lint_errors:
        raise MemoryError("memory lint must pass before writing a receipt", EXIT_GATE)
    loaded = [{"entryId": item["entryId"], "path": item["path"], "sha256": item["sha256"]}
              for item in sorted(ledger["loadedEntries"], key=lambda item: item["entryId"])]
    ledger_relative = ".autodev/artifacts/%s/%s" % (run_id, LEDGER_NAME)
    ledger_digest = sha_bytes(read_bytes(ledger_path(root, run_id), "memory ledger"))
    return {
        "schemaVersion": SCHEMA_VERSION, "kind": "memory", "sourceRevision": revision,
        "timestamp": utc_now(), "status": "pass", "runId": run_id, "ledger": ledger_relative,
        "ledgerSha256": ledger_digest, "loaded": loaded,
        "lint": {"command": "python3 .agents/skills/habit-lab-autodev/scripts/autodev_memory.py lint",
                 "status": "pass", "exitCode": 0},
        "structureChanged": structure_changed, "evalReceipt": evaluation,
        "instructionPatchCount": instruction_patch_count,
    }


def correction_advisory(count):
    if count < 3:
        return None
    return {"kind": "correction-recurrence-advisory", "occurrences": count,
            "lint": "Run autodev_memory.py lint before curating the correction.",
            "test": "Add or run a narrow owner-bound regression check before applying it.",
            "helper": "Propose a reviewed helper only; do not self-patch frozen core paths."}


INSTRUCTION_FIELDS = ("schemaVersion", "kind", "runId", "instructionId", "instruction", "structureChange", "evalReceipt")


def instruction_record(root, relative):
    rel = checked_relative(relative, "instruction record").as_posix()
    prefix = ".agents/skills/habit-lab-autodev/memory/instructions/"
    if not rel.startswith(prefix) or not rel.endswith(".json") or "/../" in rel:
        raise MemoryError("self-patch record must be under memory/instructions/", EXIT_GATE)
    path = safe_path(root, rel)
    record = read_json(path, "instruction record")
    require_exact(record, INSTRUCTION_FIELDS, "instruction record")
    if (record["schemaVersion"] != SCHEMA_VERSION or record["kind"] != "autodev-instruction-record" or
            not isinstance(record["instruction"], str) or not record["instruction"].strip() or
            type(record["structureChange"]) is not bool or
            (record["evalReceipt"] is not None and not isinstance(record["evalReceipt"], str))):
        raise MemoryError("instruction record is invalid", EXIT_INTEGRITY)
    safe_run_id(record["runId"])
    safe_id(record["instructionId"], "instruction id")
    return rel, record


def validate_eval_receipt(root, run_id, relative, source_revision=None, change_digest=None):
    path = safe_path(root, relative)
    expected_prefix = ".autodev/artifacts/%s/" % run_id
    if not relative.startswith(expected_prefix):
        raise MemoryError("evaluation receipt is outside the successful run artifacts", EXIT_GATE)
    value = read_json(path, "evaluation receipt")
    require_exact(value, ("schemaVersion", "kind", "runId", "sourceRevision", "status", "command",
                          "exitCode", "checkedChangeDigest", "regressionResult"), "evaluation receipt")
    if (value["schemaVersion"] != SCHEMA_VERSION or value["kind"] != "autodev-memory-eval" or
            value["runId"] != run_id or not isinstance(value["sourceRevision"], str) or
            not re.fullmatch(r"[0-9a-f]{40,64}", value["sourceRevision"]) or value["status"] != "pass" or
            not isinstance(value["command"], str) or not value["command"] or value["exitCode"] != 0 or
            not isinstance(value["checkedChangeDigest"], str) or not SHA_RE.fullmatch(value["checkedChangeDigest"]) or
            value["regressionResult"] != "pass" or
            (source_revision is not None and value["sourceRevision"] != source_revision) or
            (change_digest is not None and value["checkedChangeDigest"] != change_digest)):
        raise MemoryError("evaluation receipt did not pass", EXIT_GATE)
    return sha_bytes(read_bytes(path, "evaluation receipt"))


def changed_paths(root):
    result = subprocess.run(["git", "diff", "--name-only", "HEAD"], cwd=str(root), text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    cached = subprocess.run(["git", "diff", "--cached", "--name-only"], cwd=str(root), text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard"], cwd=str(root), text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode or cached.returncode or untracked.returncode:
        raise MemoryError("cannot inspect Git changes", EXIT_IO)
    return sorted(set(line for line in (result.stdout + "\n" + cached.stdout + "\n" + untracked.stdout).splitlines() if line))


SELF_PATCH_FIELDS = ("schemaVersion", "runId", "record", "state", "baseRevision", "commit",
                     "committedAt", "changeDigest")


def head_revision(root):
    result = subprocess.run(["git", "rev-parse", "--verify", "HEAD^{commit}"], cwd=str(root),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    revision = result.stdout.strip().lower()
    if result.returncode or not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise MemoryError("current HEAD cannot be resolved to a commit", EXIT_IO)
    return revision


def commit_parent(root, revision):
    result = subprocess.run(["git", "rev-parse", "--verify", revision + "^"], cwd=str(root),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, check=False)
    parent = result.stdout.strip().lower()
    if result.returncode or not re.fullmatch(r"[0-9a-f]{40,64}", parent):
        raise MemoryError("self-patch commit must have one resolvable parent", EXIT_INTEGRITY)
    return parent


def commit_blob(root, revision, relative, label):
    rel = checked_relative(relative, label).as_posix()
    if not re.fullmatch(r"[0-9a-f]{40,64}", revision) or ":" in rel:
        raise MemoryError("%s commit reference is unsafe" % label, EXIT_INTEGRITY)
    tree_ref = revision + ":" + rel
    exists = subprocess.run(["git", "cat-file", "-e", tree_ref], cwd=str(root),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if exists.returncode:
        raise MemoryError("%s is absent from the committed tree" % label, EXIT_INTEGRITY)
    result = subprocess.run(["git", "show", tree_ref], cwd=str(root), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode or len(result.stdout) > MAX_FILE_BYTES:
        raise MemoryError("cannot safely read %s from the committed tree" % label, EXIT_IO)
    return result.stdout


def change_digest(revision, record, record_bytes):
    if not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise MemoryError("change revision is invalid", EXIT_INTEGRITY)
    return sha_json({"sourceRevision": revision,
                     "changes": [{"path": record, "sha256": sha_bytes(record_bytes)}]})


def working_change_binding(root, record):
    if changed_paths(root) != [record]:
        raise MemoryError("self-patch must be an isolated change to one allowlisted instruction record", EXIT_GATE)
    revision = head_revision(root)
    raw = read_bytes(safe_path(root, record), "instruction record")
    return revision, change_digest(revision, record, raw)


def committed_change_binding(root, revision, record, base_revision, require_current=True):
    if require_current and head_revision(root) != revision:
        raise MemoryError("self-patch commit is no longer current HEAD", EXIT_INTEGRITY)
    if commit_parent(root, revision) != base_revision:
        raise MemoryError("self-patch commit parent does not match the validated HEAD", EXIT_INTEGRITY)
    result = subprocess.run(["git", "diff-tree", "--no-commit-id", "--name-only", "-r", revision],
                            cwd=str(root), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, check=False)
    paths = sorted(line for line in result.stdout.splitlines() if line)
    if result.returncode or paths != [record]:
        raise MemoryError("self-patch commit is not isolated to its allowlisted record", EXIT_GATE)
    raw = commit_blob(root, revision, record, "instruction record")
    return change_digest(revision, record, raw)


def load_self_patches(root):
    value = load_global_json(root, SELF_PATCHES_NAME, "self-patch commit history", [])
    if not isinstance(value, list):
        raise MemoryError("self-patch commit history is invalid", EXIT_INTEGRITY)
    runs = set()
    gate_cache = {}
    for item in value:
        require_exact(item, SELF_PATCH_FIELDS, "self-patch commit history entry")
        if (item["schemaVersion"] != SCHEMA_VERSION or not isinstance(item["record"], str) or
                item["state"] not in ("reserved", "committed") or
                not isinstance(item["baseRevision"], str) or not re.fullmatch(r"[0-9a-f]{40,64}", item["baseRevision"]) or
                not isinstance(item["changeDigest"], str) or not SHA_RE.fullmatch(item["changeDigest"])):
            raise MemoryError("self-patch commit history entry is invalid", EXIT_INTEGRITY)
        safe_run_id(item["runId"])
        checked_relative(item["record"], "self-patch history record")
        if item["runId"] in runs:
            raise MemoryError("self-patch commit history entry is duplicated or invalid", EXIT_INTEGRITY)
        if item["state"] == "reserved":
            if item["commit"] is not None or item["committedAt"] is not None:
                raise MemoryError("reserved self-patch history is invalid", EXIT_INTEGRITY)
        elif (not isinstance(item["commit"], str) or not re.fullmatch(r"[0-9a-f]{40,64}", item["commit"]) or
              not isinstance(item["committedAt"], str) or not UTC_RE.fullmatch(item["committedAt"])):
            raise MemoryError("committed self-patch history is invalid", EXIT_INTEGRITY)
        if item["state"] == "committed":
            source_run_success(root, item["runId"], gate_cache)
            actual = committed_change_binding(root, item["commit"], item["record"],
                                              item["baseRevision"], require_current=False)
            if actual != item["changeDigest"]:
                raise MemoryError("committed self-patch history no longer matches its isolated commit", EXIT_INTEGRITY)
        runs.add(item["runId"])
    return value


def release_self_patch_reservation(root, run_id, record, base_revision):
    history = load_self_patches(root)
    matches = [item for item in history if item["runId"] == run_id]
    if (len(matches) != 1 or matches[0]["state"] != "reserved" or
            matches[0]["record"] != record or matches[0]["baseRevision"] != base_revision):
        raise MemoryError("self-patch reservation changed before safe rollback", EXIT_INTEGRITY)
    history.remove(matches[0])
    save_global_json(root, SELF_PATCHES_NAME, history, "self-patch commit history")


def assert_reservation_compatible(history, run_id, eligibility):
    matches = [item for item in history if item["runId"] == run_id]
    if not matches:
        return False
    if len(matches) != 1:
        raise MemoryError("self-patch commit history changed concurrently", EXIT_INTEGRITY)
    reserved = matches[0]
    if reserved["state"] == "committed":
        raise MemoryError("this successful run already consumed its one self-patch commit", EXIT_GATE)
    if (reserved["state"] != "reserved" or reserved["commit"] is not None or
            reserved["committedAt"] is not None or
            any(reserved[field] != eligibility[key] for field, key in (
                ("record", "record"), ("baseRevision", "baseRevision"),
                ("changeDigest", "changeDigest")))):
        raise MemoryError("self-patch reservation does not match the current isolated change", EXIT_INTEGRITY)
    return True


def self_patch_eligibility(root, run_id, record_path):
    history = load_self_patches(root)
    _, gate_digest = source_run_success(root, run_id)
    rel, record = instruction_record(root, record_path)
    if record["runId"] != run_id:
        raise MemoryError("instruction record is not attributable to this successful run", EXIT_GATE)
    revision, actual_change_digest = working_change_binding(root, rel)
    receipt_digest = None
    if record["structureChange"]:
        if not record["evalReceipt"]:
            raise MemoryError("structure-changing instruction record requires a passing evaluation receipt", EXIT_GATE)
        receipt_digest = validate_eval_receipt(root, run_id, record["evalReceipt"], revision, actual_change_digest)
    elif record["evalReceipt"] is not None:
        raise MemoryError("non-structural instruction record cannot carry an evaluation receipt", EXIT_INTEGRITY)
    eligibility = {"record": rel, "gateStatusSha256": gate_digest, "evalReceiptSha256": receipt_digest,
                   "structureChange": record["structureChange"], "baseRevision": revision,
                   "changeDigest": actual_change_digest}
    assert_reservation_compatible(history, run_id, eligibility)
    return eligibility


def reserve_or_resume_self_patch(root, run_id, eligibility):
    """Reserve one eligible change, or recover the identical ordinary failed attempt."""
    history = load_self_patches(root)
    if not assert_reservation_compatible(history, run_id, eligibility):
        history.append({"schemaVersion": SCHEMA_VERSION, "runId": run_id,
                        "record": eligibility["record"], "state": "reserved",
                        "baseRevision": eligibility["baseRevision"], "commit": None,
                        "committedAt": None, "changeDigest": eligibility["changeDigest"]})
        save_global_json(root, SELF_PATCHES_NAME, history, "self-patch commit history")
        return False
    return True


def record_self_patch(root, run_id, record_path):
    history = load_self_patches(root)
    matches = [item for item in history if item["runId"] == run_id]
    if len(matches) != 1 or matches[0]["state"] != "reserved":
        raise MemoryError("self-patch needs exactly one reserved commit validation", EXIT_GATE)
    item = matches[0]
    rel, record = instruction_record(root, record_path)
    if rel != item["record"] or record["runId"] != run_id:
        raise MemoryError("self-patch record does not match its reserved run", EXIT_INTEGRITY)
    source_run_success(root, run_id)
    commit = head_revision(root)
    actual_change_digest = committed_change_binding(root, commit, rel, item["baseRevision"])
    receipt_digest = None
    if record["structureChange"]:
        if not record["evalReceipt"]:
            raise MemoryError("structure-changing instruction record requires a fresh evaluation receipt", EXIT_GATE)
        receipt_digest = validate_eval_receipt(root, run_id, record["evalReceipt"], commit, actual_change_digest)
    elif record["evalReceipt"] is not None:
        raise MemoryError("non-structural instruction record cannot carry an evaluation receipt", EXIT_INTEGRITY)
    item.update({"state": "committed", "commit": commit, "committedAt": utc_now(),
                 "changeDigest": actual_change_digest})
    save_global_json(root, SELF_PATCHES_NAME, history, "self-patch commit history")
    return {"record": rel, "commit": commit, "changeDigest": actual_change_digest,
            "evalReceiptSha256": receipt_digest}


def parser():
    value = JsonParser(prog="autodev_memory.py", add_help=False)
    sub = value.add_subparsers(dest="command", required=True)
    start = sub.add_parser("start", add_help=False); start.add_argument("run_id")
    plan = sub.add_parser("plan", add_help=False); plan.add_argument("run_id"); plan.add_argument("--tag", action="append", default=[]); plan.add_argument("--tags")
    load = sub.add_parser("load", add_help=False); load.add_argument("run_id"); load.add_argument("entry_id")
    lint = sub.add_parser("lint", add_help=False); lint.add_argument("--run-id")
    status = sub.add_parser("status", add_help=False); status.add_argument("run_id")
    finalize = sub.add_parser("finalize", add_help=False); finalize.add_argument("run_id"); finalize.add_argument("--outcome", choices=OUTCOMES, required=True); finalize.add_argument("--duration-seconds", type=int, default=0); finalize.add_argument("--build", action="append", default=[]); finalize.add_argument("--iterations", type=int, default=0); finalize.add_argument("--attempts", type=int, default=0); finalize.add_argument("--platform", action="append", choices=("android", "ios"), default=[]); finalize.add_argument("--flaky-step", action="append", default=[]); finalize.add_argument("--read", action="append", default=[]); finalize.add_argument("--write", action="append", default=[]); finalize.add_argument("--gate-run")
    receipt = sub.add_parser("receipt", add_help=False); receipt.add_argument("run_id"); receipt.add_argument("--source-revision", required=True); receipt.add_argument("--eval-receipt")
    consolidate = sub.add_parser("consolidate", add_help=False); consolidate.add_argument("--run-id")
    observe = sub.add_parser("observe-correction", add_help=False); observe.add_argument("run_id"); observe.add_argument("--proposal-json", required=True)
    store = sub.add_parser("store-correction", add_help=False); store.add_argument("proposal_id"); store.add_argument("--run-id", required=True)
    validate = sub.add_parser("self-patch-validate", add_help=False); validate.add_argument("run_id"); validate.add_argument("--record", required=True)
    commit = sub.add_parser("self-patch-commit", add_help=False); commit.add_argument("run_id"); commit.add_argument("--record", required=True); commit.add_argument("--message", required=True); commit.add_argument("--confirm-commit", action="store_true")
    record = sub.add_parser("self-patch-record", add_help=False); record.add_argument("run_id"); record.add_argument("--record", required=True)
    return value


HELP = {"commands": ["start", "plan", "load", "lint", "status", "finalize", "receipt", "consolidate",
                     "observe-correction", "store-correction", "self-patch-validate", "self-patch-commit",
                     "self-patch-record"],
        "jsonOnly": True, "version": SCHEMA_VERSION}


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv or argv == ["--help"] or argv == ["-h"] or argv == ["help"]:
        emit({"ok": True, "command": "help", "help": HELP})
        return 0
    try:
        require_primitives()
        args = parser().parse_args(argv)
        root = repo_root()
        entries = load_catalog(root)
        if args.command == "start":
            run_id = safe_run_id(args.run_id)
            ledger = ledger_template(run_id, entries)
            loaded = []
            for entry_id in INITIAL_IDS:
                content, digest = record_load(root, ledger, entry_by_id(entries, entry_id))
                loaded.append({"entryId": entry_id, "sha256": digest, "content": content})
            ledger_digest = create_ledger(root, ledger)
            payload = {"ok": True, "command": "start", "runId": run_id, "loaded": loaded,
                       "ledger": ".autodev/artifacts/%s/%s" % (run_id, LEDGER_NAME), "ledgerSha256": ledger_digest}
        elif args.command == "plan":
            run_id = safe_run_id(args.run_id); ledger = load_ledger(root, run_id)
            if ledger["outcome"] is not None:
                raise MemoryError("terminal memory ledger cannot be planned", EXIT_GATE)
            if any(item["entryId"] not in INITIAL_IDS for item in ledger["loadedEntries"]):
                raise MemoryError("memory plan cannot change after a non-initial entry was loaded", EXIT_GATE)
            tags = list(args.tag)
            if args.tags:
                tags.extend(part for part in args.tags.split(",") if part)
            if not tags or any(not TAG_RE.fullmatch(tag) for tag in tags):
                raise MemoryError("plan requires one or more lowercase --tag values")
            tags = sorted(set(tags)); history = successful_history(root, run_id)
            classifications_map, counts = classifications(entries, ledger, history)
            selectable = [entry for entry in entries if entry["id"] not in INITIAL_IDS]
            scored = []
            for entry in selectable:
                matches = len(set(tags).intersection(entry["tags"]))
                if matches:
                    scored.append((-matches, -counts.get(entry["id"], 0), entry["id"], entry))
            selected = [item[3] for item in sorted(scored)[:3]]
            ledger["plannedEntryIds"] = [entry["id"] for entry in selected]
            ledger_digest = save_ledger(root, ledger)
            payload = {"ok": True, "command": "plan", "runId": run_id, "tags": tags,
                       "selected": [{"id": entry["id"], "path": entry["path"], "classification": classifications_map[entry["id"]]} for entry in selected],
                       "classification": classifications_map, "historyRuns": len(history), "ledgerSha256": ledger_digest}
        elif args.command == "load":
            run_id = safe_run_id(args.run_id); ledger = load_ledger(root, run_id)
            if ledger["outcome"] is not None:
                raise MemoryError("terminal memory ledger cannot load new entries", EXIT_GATE)
            entry = entry_by_id(entries, safe_id(args.entry_id, "entry id"))
            if entry["id"] not in INITIAL_IDS and entry["id"] not in ledger["plannedEntryIds"]:
                raise MemoryError("entry must be selected by this run's plan", EXIT_GATE)
            content, digest = record_load(root, ledger, entry)
            ledger_digest = save_ledger(root, ledger)
            payload = {"ok": True, "command": "load", "runId": run_id, "entryId": entry["id"],
                       "path": entry["path"], "sha256": digest, "content": content, "ledgerSha256": ledger_digest}
        elif args.command == "lint":
            errors = lint_all(root, entries)
            payload = {"ok": not errors, "command": "lint", "errors": errors, "warnings": []}
            if args.run_id:
                ledger = load_ledger(root, safe_run_id(args.run_id))
                payload["runId"] = args.run_id
                payload["ledgerSha256"] = sha_bytes(read_bytes(ledger_path(root, args.run_id), "memory ledger"))
            if errors:
                emit(payload); return EXIT_VALIDATION
        elif args.command == "status":
            run_id = safe_run_id(args.run_id); ledger = load_ledger(root, run_id)
            history = successful_history(root, run_id)
            classes, _ = classifications(entries, ledger, history)
            payload = {"ok": True, "command": "status", "runId": run_id, "ledger": ledger,
                       "classification": classes, "exactReads": ledger["reads"], "exactWrites": ledger["writes"],
                       "ledgerSha256": sha_bytes(read_bytes(ledger_path(root, run_id), "memory ledger"))}
        elif args.command == "finalize":
            run_id = safe_run_id(args.run_id); ledger = load_ledger(root, run_id)
            if ledger["outcome"] is not None:
                raise MemoryError("memory ledger is terminal", EXIT_GATE)
            if args.duration_seconds < 0 or args.iterations < 0 or args.attempts < 0:
                raise MemoryError("duration, iterations, and attempts cannot be negative")
            if len(args.platform) != len(set(args.platform)):
                raise MemoryError("platforms must be unique")
            if any(not isinstance(step, str) or not step.strip() for step in args.flaky_step):
                raise MemoryError("flaky steps must be nonempty")
            gate_digest = None
            if args.gate_run is not None:
                if args.outcome != "success" or args.gate_run != run_id:
                    raise MemoryError("only matching successful memory finalize may bind a gate run", EXIT_GATE)
                # The gate is still unsealed: persisting its current status digest would
                # necessarily become stale when the receipt allows terminal finish.
                active_gate_run(root, run_id)
            ledger.update({"finalizedAt": utc_now(), "durationSeconds": args.duration_seconds,
                           "builds": [parse_build(item) for item in args.build], "iterations": args.iterations,
                           "attempts": args.attempts, "outcome": args.outcome,
                           "platforms": sorted(args.platform), "flakySteps": sorted(set(args.flaky_step)),
                           "gateRun": args.gate_run, "gateStatusSha256": gate_digest})
            read_records = {item["path"]: item for item in ledger["reads"]}
            for item in capture_paths(root, args.read, "read path"):
                prior = read_records.get(item["path"])
                if prior is not None and prior["sha256"] != item["sha256"]:
                    raise MemoryError("a previously recorded read changed before finalization", EXIT_INTEGRITY)
                read_records[item["path"]] = item
            ledger["reads"] = [read_records[path] for path in sorted(read_records)]
            ledger["writes"] = capture_paths(root, args.write, "write path")
            lint_errors = lint_all(root, entries)
            ledger_digest = save_ledger(root, ledger)
            payload = {"ok": True, "command": "finalize", "runId": run_id, "outcome": args.outcome,
                       "ledgerSha256": ledger_digest, "lint": {"ok": not lint_errors, "errors": lint_errors},
                       "consolidation": consolidation_advisory(root, lint_errors)}
        elif args.command == "receipt":
            run_id = safe_run_id(args.run_id)
            receipt_value = memory_receipt(root, run_id, resolve_revision(root, args.source_revision),
                                           args.eval_receipt)
            digest = create_run_artifact(root, run_id, "memory-receipt.json", receipt_value)
            payload = {"ok": True, "command": "receipt", "runId": run_id,
                       "receipt": ".autodev/artifacts/%s/memory-receipt.json" % run_id,
                       "receiptSha256": digest, "value": receipt_value}
        elif args.command == "consolidate":
            if args.run_id:
                safe_run_id(args.run_id)
            lint_errors = lint_all(root, entries)
            payload = {"ok": True, "command": "consolidate", "runId": args.run_id,
                       "advisory": consolidation_advisory(root, lint_errors), "rewritten": False}
        elif args.command == "observe-correction":
            run_id = safe_run_id(args.run_id); _, gate_digest = source_run_success(root, run_id)
            proposal = parse_proposal(args.proposal_json); records = load_corrections(root)
            if any(item["runId"] == run_id and item["proposal"]["proposalId"] == proposal["proposalId"] for item in records):
                raise MemoryError("same run cannot observe the same correction proposal twice", EXIT_GATE)
            if any(item["proposal"]["proposalId"] == proposal["proposalId"] and
                   canonical(item["proposal"]) != canonical(proposal) for item in records):
                raise MemoryError("matching correction proposal ID diverges from sealed history", EXIT_INTEGRITY)
            item = {"schemaVersion": SCHEMA_VERSION, "proposal": proposal, "runId": run_id,
                    "gateStatusSha256": gate_digest, "recordedAt": utc_now()}
            item["digest"] = sha_json(item)
            records.append(item); save_global_json(root, CORRECTIONS_NAME, records, "correction observations")
            matching = [record for record in records if record["proposal"]["proposalId"] == proposal["proposalId"]]
            count = len(matching)
            lifecycle = "candidate" if count == 1 else "confirmed" if count == 2 else "recurrence"
            payload = {"ok": True, "command": "observe-correction", "proposalId": proposal["proposalId"],
                       "runId": run_id, "lifecycle": lifecycle, "distinctSuccessfulRuns": count,
                       "advisory": correction_advisory(count)}
        elif args.command == "store-correction":
            run_id = safe_run_id(args.run_id); proposal_id = safe_id(args.proposal_id, "proposal id")
            records = load_corrections(root)
            matched = [item for item in records if item["proposal"]["proposalId"] == proposal_id]
            if len({item["runId"] for item in matched}) < 2:
                raise MemoryError("only a confirmed correction may be stored", EXIT_GATE)
            source_run_success(root, run_id)
            if run_id not in {item["runId"] for item in matched}:
                raise MemoryError("store command must name a successful observing run", EXIT_GATE)
            destination = skill_root(root) / "memory/code-corrections" / (proposal_id + ".json")
            proposal = matched[0]["proposal"]
            record = {"schemaVersion": SCHEMA_VERSION, "kind": "tracked-code-correction", "proposal": proposal,
                      "successfulRuns": sorted({item["runId"] for item in matched}), "storedAt": utc_now()}
            create_json_file(destination, record, "tracked correction record")
            payload = {"ok": True, "command": "store-correction", "path": "memory/code-corrections/%s.json" % proposal_id,
                       "stored": True}
        elif args.command == "self-patch-validate":
            run_id = safe_run_id(args.run_id)
            eligibility = self_patch_eligibility(root, run_id, args.record)
            payload = {"ok": True, "command": "self-patch-validate", "runId": run_id,
                       "eligible": True, **eligibility}
        elif args.command == "self-patch-commit":
            run_id = safe_run_id(args.run_id)
            if not args.confirm_commit:
                raise MemoryError("self-patch commit requires --confirm-commit", EXIT_USAGE)
            if not isinstance(args.message, str) or not args.message.strip() or "\n" in args.message or len(args.message) > 120:
                raise MemoryError("self-patch commit message must be one nonempty line")
            with self_patch_lifecycle(root):
                eligibility = self_patch_eligibility(root, run_id, args.record)
                reserve_or_resume_self_patch(root, run_id, eligibility)
                pre_commit_head = head_revision(root)
                result = subprocess.run(["git", "add", "--", eligibility["record"]], cwd=str(root), stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, text=True, check=False)
                if result.returncode:
                    release_self_patch_reservation(root, run_id, eligibility["record"], eligibility["baseRevision"])
                    raise MemoryError("could not stage isolated instruction record", EXIT_IO)
                result = subprocess.run(["git", "commit", "-m", args.message, "--", eligibility["record"]], cwd=str(root), stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, text=True, check=False)
                if result.returncode:
                    if head_revision(root) == pre_commit_head:
                        release_self_patch_reservation(root, run_id, eligibility["record"], eligibility["baseRevision"])
                    else:
                        raise MemoryError("self-patch commit result is uncertain; retain reservation and run self-patch-record", EXIT_INTEGRITY)
                    raise MemoryError("normal isolated self-patch commit was rejected", EXIT_GATE)
                commit = head_revision(root)
                payload = {"ok": True, "command": "self-patch-commit", "runId": run_id,
                           "committed": eligibility["record"], "commit": commit,
                           "postCommitValidationRequired": True}
        elif args.command == "self-patch-record":
            run_id = safe_run_id(args.run_id)
            with self_patch_lifecycle(root):
                completed = record_self_patch(root, run_id, args.record)
            payload = {"ok": True, "command": "self-patch-record", "runId": run_id,
                       "recorded": True, **completed}
        else:
            raise MemoryError("unsupported command", EXIT_USAGE)
        emit(payload)
        return 0
    except MemoryError as exc:
        emit({"ok": False, "error": str(exc), "exitCode": exc.code})
        return exc.code
    except (OSError, subprocess.SubprocessError):
        emit({"ok": False, "error": "filesystem or fixed-command I/O failed", "exitCode": EXIT_IO})
        return EXIT_IO
    except Exception:
        emit({"ok": False, "error": "unexpected state or internal integrity failure", "exitCode": EXIT_INTEGRITY})
        return EXIT_INTEGRITY


if __name__ == "__main__":
    sys.exit(main())
