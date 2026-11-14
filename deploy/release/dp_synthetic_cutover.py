#!/usr/bin/env python3
"""DP synthetic population cutover; defaults to preflight only.

No account identifiers, tokens, environment values or secret paths are logged.
A production cutover requires an explicit human approval phrase and a healthy
baseline. No server container, ranking data, or Google Play content is changed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

OLD_IMAGE = "sha256:d25988e867448a5eda0878bead1acae7794ac3ca82bc6e9691d56026c0dc1827"
NEW_IMAGE = "sha256:17b6ade718176f87aa94979a100907eb423f9c6320c2c13d41b934a698cc5e28"
NEW_TAG = "dp-synthetic:sha-acb9c6783ef7"
NAME = "domino-synthetic-population"
OLD_PARKED = NAME + "-rollback-pre-v37"
FAILED_PARKED = NAME + "-failed-v37"
NEW_PREPARED = NAME + "-v37-prepared"
STATE_VOLUME = "synthetic-participant-state"
BACKUP_ROOT = Path("/var/backups/domino-pe/synthetic")
ENV_TMP_ROOT = Path("/run")
PRELIMINARY_BACKUP = BACKUP_ROOT / "identities-20261113T224716Z-JXrSKd.json"
PRELIMINARY_SHA256 = "07ddaff06ccf595d9aaebf6faa8c364fc37fe315efbe54b9ae3bf3489948b9bf"
SERVER_IMAGE = "sha256:6f5a54b7f6f5077f21706f0a8de797feae8a3f249c6500847d3efb32dab25b4b"
REQUIRED_ENV_KEYS = {
    "DOMINO_SYNTHETIC_RUNTIME_TARGET",
    "DOMINO_SYNTHETIC_BASE_URL",
    "DOMINO_SYNTHETIC_CLIENT_STATE_DIR",
    "DOMINO_SYNTHETIC_POPULATION_SIZE",
    "DOMINO_SYNTHETIC_STANDBY_POOL_SIZE",
    "DOMINO_SYNTHETIC_STANDBY_POLL_INTERVAL_MILLIS",
    "DOMINO_SYNTHETIC_POLL_INTERVAL_MILLIS",
    "DOMINO_SYNTHETIC_HEARTBEAT_FILE",
    "DOMINO_SYNTHETIC_MAX_SILENCE_MILLIS",
    "DOMINO_SYNTHETIC_PROVISIONING_SECRET_FILE",
}


def demand(condition: bool, code: str) -> None:
    if not condition:
        raise GateError(code)


class GateError(RuntimeError):
    pass


class Runner:
    def call(self, *args: str) -> str:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if result.returncode:
            # Don't print command, stdout, or stderr; an inspect can contain secrets.
            raise GateError("DOCKER_COMMAND_FAILED")
        return result.stdout

    def docker(self, *args: str) -> str:
        return self.call("docker", *args)

    def inspect(self, name: str, kind: str = "container") -> dict:
        if kind == "container":
            data = self.docker("inspect", "--type", "container", name)
        elif kind == "image":
            data = self.docker("image", "inspect", name)
        elif kind == "volume":
            data = self.docker("volume", "inspect", name)
        else:
            raise GateError("INVALID_INSPECT_KIND")
        return json.loads(data)[0]


def stdout(s: str) -> None:
    print(s, flush=True)


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def identities(path: Path) -> dict[int, tuple[str, str]]:
    with path.open(encoding="utf-8") as stream:
        data = json.load(stream)
    demand(data.get("schemaVersion") == 1, "STATE_SCHEMA_INVALID")
    accounts = data.get("accounts")
    demand(isinstance(accounts, list) and len(accounts) == 60, "STATE_ACCOUNT_COUNT_INVALID")
    result = {}
    account_ids, player_ids = set(), set()
    for acc in accounts:
        index, aid, pid = acc.get("profileIndex"), acc.get("accountId"), acc.get("playerId")
        demand(isinstance(index, int) and index > 0, "STATE_INDEX_INVALID")
        demand(isinstance(aid, str) and bool(aid), "STATE_ACCOUNT_INVALID")
        demand(isinstance(pid, str) and bool(pid), "STATE_PLAYER_INVALID")
        demand(index not in result and aid not in account_ids and pid not in player_ids, "STATE_DUPLICATE")
        demand(isinstance(acc.get("accessToken"), str) and bool(acc["accessToken"]), "STATE_TOKEN_MISSING")
        result[index] = (aid, pid)
        account_ids.add(aid)
        player_ids.add(pid)
    demand(len(result) == 60, "STATE_IDENTITY_COUNT_INVALID")
    return result


def assert_public_api() -> None:
    for path in ("health", "ready"):
        try:
            with urllib.request.urlopen("https://api.dominope.com.br/" + path, timeout=12) as response:
                demand(response.status == 200, "API_NOT_READY")
        except Exception as exc:
            raise GateError("API_NOT_READY") from None


def validate_contract(container: dict, old_image: dict, new_image: dict) -> tuple[dict, str, dict]:
    cfg = container["Config"]
    host = container["HostConfig"]
    state = container["State"]
    demand(container["Image"] == OLD_IMAGE, "OLD_IMAGE_DRIFT")
    demand(state["Status"] == "running", "OLD_NOT_RUNNING")
    demand((state.get("Health") or {}).get("Status") == "healthy", "OLD_UNHEALTHY")
    demand(cfg.get("User") == "10002:10002", "USER_DRIFT")
    demand(cfg.get("WorkingDir") == "/opt/domino-synthetic", "WORKDIR_DRIFT")
    demand(cfg.get("StopSignal") == "SIGTERM", "STOPSIGNAL_DRIFT")
    demand(cfg.get("Entrypoint") == ["/opt/domino-synthetic/bin/miniproduction-client"], "ENTRYPOINT_DRIFT")
    demand(bool(host.get("ReadonlyRootfs")), "ROOTFS_DRIFT")
    demand(host.get("Privileged") is False, "PRIVILEGE_DRIFT")
    demand(host.get("Init") is True, "INIT_DRIFT")
    demand(host.get("AutoRemove") is False, "AUTO_REMOVE_DRIFT")
    demand([str(x).upper() for x in (host.get("CapDrop") or [])] == ["ALL"], "CAP_DROP_DRIFT")
    demand(not host.get("CapAdd"), "CAP_ADD_DRIFT")
    demand(host.get("SecurityOpt") == ["no-new-privileges:true"], "SECURITY_OPT_DRIFT")
    demand(host.get("NetworkMode") == "bridge", "NETWORK_DRIFT")
    demand(set((container["NetworkSettings"].get("Networks") or {}).keys()) == {"bridge"}, "NETWORKS_DRIFT")
    demand((host.get("RestartPolicy") or {}).get("Name") == "unless-stopped", "RESTART_POLICY_DRIFT")
    demand((host.get("LogConfig") or {}).get("Type") == "local", "LOG_DRIVER_DRIFT")
    demand(not ((host.get("LogConfig") or {}).get("Config") or {}), "LOG_OPTIONS_UNSUPPORTED")
    demand(host.get("PortBindings") in (None, {}), "PORT_BINDINGS_DRIFT")
    demand(not host.get("Devices"), "DEVICES_DRIFT")
    demand(not host.get("Ulimits"), "ULIMITS_DRIFT")
    demand(host.get("IpcMode") == "private", "IPC_DRIFT")
    demand(not host.get("PidMode"), "PID_DRIFT")
    demand(host.get("Memory") in (None, 0), "MEMORY_DRIFT")
    demand(host.get("CpuShares") in (None, 0), "CPU_SHARES_DRIFT")
    demand(host.get("NanoCpus") in (None, 0), "NANO_CPUS_DRIFT")
    demand(host.get("PidsLimit") in (None, 0), "PIDS_LIMIT_DRIFT")
    tmpfs = host.get("Tmpfs") or {}
    demand(set(tmpfs) == {"/tmp"}, "TMPFS_DRIFT")
    tmpfs_spec = str(tmpfs["/tmp"])
    demand(re.fullmatch(r"[\w\d=,]+", tmpfs_spec) is not None, "TMPFS_INVALID")
    mounts = {m["Destination"]: m for m in container["Mounts"]}
    demand(set(mounts) == {"/state", "/run/secrets/synthetic_provisioning_secret"}, "MOUNT_DRIFT")
    demand(mounts["/state"]["Type"] == "volume" and mounts["/state"]["Name"] == STATE_VOLUME and mounts["/state"]["RW"] is True, "STATE_MOUNT_DRIFT")
    secret = mounts["/run/secrets/synthetic_provisioning_secret"]
    demand(secret["Type"] == "bind" and secret["RW"] is False, "SECRET_MOUNT_DRIFT")
    secret_source = Path(secret["Source"])
    demand(secret_source.is_file(), "SECRET_SOURCE_MISSING")
    demand("," not in str(secret_source) and "\n" not in str(secret_source), "SECRET_SOURCE_UNSUPPORTED")
    entries = cfg.get("Env") or []
    env = {}
    for item in entries:
        demand("=" in item, "ENV_FORMAT_INVALID")
        key, value = item.split("=", 1)
        demand(re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", key) is not None and key not in env, "ENV_KEYS_INVALID")
        demand("\n" not in value and "\r" not in value, "ENV_MULTILINE_UNSUPPORTED")
        env[key] = value
    demand(len(env) == 17 and REQUIRED_ENV_KEYS.issubset(env), "ENV_CONTRACT_DRIFT")
    demand(env["DOMINO_SYNTHETIC_POPULATION_SIZE"] == "60", "POPULATION_SIZE_DRIFT")
    demand(env["DOMINO_SYNTHETIC_STANDBY_POOL_SIZE"] == "6", "STANDBY_POOL_DRIFT")
    demand(env["DOMINO_SYNTHETIC_CLIENT_STATE_DIR"] == "/state", "STATE_DIR_DRIFT")
    demand(env["DOMINO_SYNTHETIC_PROVISIONING_SECRET_FILE"] == "/run/secrets/synthetic_provisioning_secret", "SECRET_PATH_DRIFT")
    demand(env["DOMINO_SYNTHETIC_RUNTIME_TARGET"] == "REMOTE_PRODUCTION", "TARGET_DRIFT")
    demand(old_image["Id"] == OLD_IMAGE and new_image["Id"] == NEW_IMAGE, "IMAGE_ID_MISMATCH")
    demand(new_image["Config"]["User"] == "10002:10002", "NEW_USER_DRIFT")
    demand(new_image["Config"]["Entrypoint"] == cfg["Entrypoint"], "NEW_ENTRYPOINT_DRIFT")
    demand(new_image["Os"] == "linux" and new_image["Architecture"] == "amd64", "IMAGE_PLATFORM_DRIFT")
    demand(new_image["Config"].get("Healthcheck") == old_image["Config"].get("Healthcheck"), "NEW_HEALTHCHECK_DRIFT")
    demand(cfg.get("Healthcheck") == old_image["Config"].get("Healthcheck"), "HEALTHCHECK_OVERRIDE_DRIFT")
    return env, str(secret_source), tmpfs


def preflight(r: Runner) -> tuple[dict, dict, str, dict, Path]:
    demand(os.geteuid() == 0, "ROOT_REQUIRED")
    container = r.inspect(NAME)
    old_image = r.inspect(OLD_IMAGE, "image")
    new_image = r.inspect(NEW_TAG, "image")
    env, secret, tmpfs = validate_contract(container, old_image, new_image)
    # The root-owned preliminary backup was generated by the operator in a prior gate.
    demand(PRELIMINARY_BACKUP.is_file(), "PRELIMINARY_BACKUP_MISSING")
    backup_stat = PRELIMINARY_BACKUP.stat()
    demand(backup_stat.st_uid == 0 and backup_stat.st_mode & 0o777 == 0o600,
           "PRELIMINARY_BACKUP_PERMISSIONS_UNSAFE")
    demand(hash_file(PRELIMINARY_BACKUP) == PRELIMINARY_SHA256, "PRELIMINARY_BACKUP_HASH_MISMATCH")
    server = r.inspect("domino-server")
    demand(server["Image"] == SERVER_IMAGE and server["State"]["Status"] == "running",
           "SERVER_BASELINE_DRIFT")
    baseline = identities(PRELIMINARY_BACKUP)
    volume = r.inspect(STATE_VOLUME, "volume")
    demand(volume["Driver"] == "local", "VOLUME_DRIVER_DRIFT")
    state_dir = Path(volume["Mountpoint"])
    demand(state_dir.is_dir(), "VOLUME_NOT_MOUNTED")
    live = identities(state_dir / "synthetic-identities.json")
    demand(live == baseline, "IDENTITY_DRIFT_VS_PRELIMINARY_BACKUP")
    for parked in (OLD_PARKED, FAILED_PARKED, NEW_PREPARED):
        # Docker does not report 'missing' to inspect; query list with name filter.
        matches = r.docker("ps", "-a", "--filter", "name=^/" + parked + "$", "--format", "{{.Names}}")
        demand(not matches.strip(), "PRIOR_CUTOVER_CONTAINER_PRESENT")
    assert_public_api()
    stdout("IMAGE_AND_RUNTIME_CONTRACT=PASS")
    stdout("PRELIMINARY_BACKUP=VERIFIED")
    stdout("IDENTITIES=60_UNCHANGED")
    stdout("API_HEALTH_READY=PASS")
    return container, env, secret, tmpfs, state_dir


def consistent_backup(state_dir: Path) -> tuple[Path, str]:
    BACKUP_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(BACKUP_ROOT, 0o700)
    source = state_dir / "synthetic-identities.json"
    baseline_identities = identities(source)
    fd, name = tempfile.mkstemp(prefix="identities-cutover-v37-", suffix=".json", dir=BACKUP_ROOT)
    path = Path(name)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "wb") as out, source.open("rb") as source_file:
            shutil.copyfileobj(source_file, out)
            out.flush()
            os.fsync(out.fileno())
        demand(identities(path) == baseline_identities, "BACKUP_IDENTITY_MISMATCH")
        digest = hash_file(path)
        demand(digest == hash_file(source), "BACKUP_SOURCE_CHANGED")
        return path, digest
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def create_argv(env_file: Path, secret_source: str, tmpfs: dict, container_name: str = NEW_PREPARED) -> list[str]:
    # Never include environment values in command-line args; env_file is root-only.
    return [
        "create", "--name", container_name,
        "--network", "bridge", "--init", "--read-only",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
        "--restart", "unless-stopped", "--log-driver", "local",
        "--user", "10002:10002", "--workdir", "/opt/domino-synthetic",
        "--stop-signal", "SIGTERM", "--stop-timeout", "60",
        "--tmpfs", "/tmp:" + tmpfs["/tmp"],
        "--volume", STATE_VOLUME + ":/state:rw",
        "--mount", "type=bind,src=" + secret_source + ",dst=/run/secrets/synthetic_provisioning_secret,readonly",
        "--env-file", str(env_file),
        NEW_TAG,
    ]


def assert_new_container(r: Runner, old_env: dict, name: str = NEW_PREPARED) -> None:
    new = r.inspect(name)
    demand(new["Image"] == NEW_IMAGE, "NEW_CONTAINER_IMAGE_MISMATCH")
    keys = {}
    for entry in new["Config"]["Env"]:
        k, v = entry.split("=", 1)
        keys[k] = v
    demand(keys == old_env, "NEW_ENVIRONMENT_DIFFERENT")
    mounts = {m["Destination"]: m for m in new["Mounts"]}
    demand(mounts["/state"]["Name"] == STATE_VOLUME, "NEW_VOLUME_DIFFERENT")
    demand(mounts["/run/secrets/synthetic_provisioning_secret"]["RW"] is False, "NEW_SECRET_RW_INVALID")
    demand(new["HostConfig"]["ReadonlyRootfs"] is True, "NEW_ROOTFS_INVALID")
    demand(new["HostConfig"]["Init"] is True, "NEW_INIT_INVALID")
    demand(new["HostConfig"]["NetworkMode"] == "bridge", "NEW_NETWORK_INVALID")
    demand(new["HostConfig"]["RestartPolicy"]["Name"] == "unless-stopped", "NEW_RESTART_INVALID")
    demand(new["HostConfig"]["LogConfig"]["Type"] == "local", "NEW_LOG_DRIVER_INVALID")
    demand(new["HostConfig"].get("CapDrop") == ["ALL"], "NEW_CAP_DROP_INVALID")
    demand(new["HostConfig"].get("SecurityOpt") == ["no-new-privileges:true"], "NEW_SECURITY_INVALID")
    demand(set((new["HostConfig"].get("Tmpfs") or {}).keys()) == {"/tmp"}, "NEW_TMPFS_INVALID")
    demand(new["Config"]["User"] == "10002:10002", "NEW_USER_INVALID")


def health_smoke(r: Runner, until: int = 180) -> None:
    started = time.monotonic()
    healthy = False
    while time.monotonic() - started < until:
        state = r.inspect(NAME)["State"]
        demand(state["Status"] == "running", "NEW_CONTAINER_STOPPED")
        if (state.get("Health") or {}).get("Status") == "healthy":
            healthy = True
            break
        time.sleep(5)
    demand(healthy, "NEW_HEALTH_TIMEOUT")
    # Do not print log contents: logs may contain sensitive operational data.
    logs = r.docker("logs", "--since", "5m", NAME)
    demand("POPULATION_STARTED accounts=60" in logs and "standbyPool=6" in logs, "NEW_POPULATION_START_MISSING")
    demand("POPULATION_FAILED" not in logs and "POPULATION_ACCOUNT_AUTH_BLOCKED" not in logs,
           "NEW_POPULATION_FAILURE_SIGNAL")
    assert_public_api()
    stdout("NEW_CONTAINER_HEALTHY=PASS")
    stdout("POPULATION_STARTED_60_STANDBY_6=PASS")
    stdout("API_HEALTH_READY=PASS")


def rollback(r: Runner, old_stopped: bool, old_renamed: bool,
             new_named: bool, prepared_exists: bool) -> None:
    stdout("ROLLBACK_TRIGGERED=YES")
    # The new population MUST be stopped before the previous one restarts.
    if new_named:
        status = r.inspect(NAME)["State"]["Status"]
        if status == "running":
            r.docker("stop", "--time", "60", NAME)
        demand(r.inspect(NAME)["State"]["Status"] != "running", "NEW_FAILED_TO_STOP")
        r.docker("rename", NAME, FAILED_PARKED)
    elif prepared_exists:
        # Not started, but keep it for inspection if a transaction failed.
        demand(r.inspect(NEW_PREPARED)["State"]["Status"] != "running", "PREPARED_UNEXPECTEDLY_RUNNING")
        r.docker("rename", NEW_PREPARED, FAILED_PARKED)
    if old_renamed:
        r.docker("rename", OLD_PARKED, NAME)
    if old_stopped:
        r.docker("start", NAME)
        stdout("ROLLBACK_OLD_RESTARTED=YES")
        started = time.monotonic()
        while time.monotonic() - started < 180:
            state = r.inspect(NAME)["State"]
            demand(state["Status"] == "running", "ROLLBACK_OLD_NOT_RUNNING")
            if (state.get("Health") or {}).get("Status") == "healthy":
                stdout("ROLLBACK_OLD_HEALTHY=PASS")
                return
            time.sleep(5)
        raise GateError("ROLLBACK_OLD_HEALTH_TIMEOUT")
    else:
        stdout("ORIGINAL_CONTAINER_WAS_NOT_STOPPED=YES")


def cutover(r: Runner, env: dict, secret: str, tmpfs: dict, state_dir: Path) -> None:
    old_stopped = False
    old_renamed = False
    prepared_exists = False
    new_named = False
    try:
        # Create and inspect the new configuration BEFORE touching active actors.
        fd, name = tempfile.mkstemp(prefix="dp-synthetic-env-", dir=ENV_TMP_ROOT)
        try:
            os.fchmod(fd, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as out:
                for key, value in sorted(env.items()):
                    out.write(key + "=" + value + "\n")
            r.docker(*create_argv(Path(name), secret, tmpfs))
            prepared_exists = True
        finally:
            Path(name).unlink(missing_ok=True)
        assert_new_container(r, env)
        demand(r.inspect(NEW_PREPARED)["State"]["Status"] == "created", "NEW_STARTED_UNEXPECTEDLY")
        stdout("NEW_CONTAINER_PREPARED_NOT_STARTED=PASS")

        old_stopped = True
        r.docker("stop", "--time", "60", NAME)
        demand(r.inspect(NAME)["State"]["Status"] != "running", "OLD_STILL_RUNNING")
        stdout("OLD_POPULATION_STOPPED=PASS")
        backup_file, digest = consistent_backup(state_dir)
        stdout("CONSISTENT_BACKUP_CREATED=PASS")
        stdout("CONSISTENT_BACKUP_SHA256=" + digest)
        stdout("CONSISTENT_BACKUP_FILE=" + str(backup_file))
        r.docker("rename", NAME, OLD_PARKED)
        old_renamed = True
        r.docker("rename", NEW_PREPARED, NAME)
        prepared_exists = False
        new_named = True
        r.docker("start", NAME)
        health_smoke(r)
        stdout("CUTOVER_RESULT=PASS")
        stdout("ROLLBACK_CONTAINER_PRESERVED=YES")
    except BaseException:
        if prepared_exists or old_stopped:
            try:
                rollback(r, old_stopped, old_renamed, new_named, prepared_exists)
                stdout("ROLLBACK_RESULT=PASS")
            except BaseException:
                stdout("ROLLBACK_RESULT=FAILED_MANUAL_INTERVENTION_REQUIRED")
        raise


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Cutover audit: default read-only")
    parser.add_argument("--execute", action="store_true", help="perform cutover after explicit approval")
    parser.add_argument("--approval", default="", help="operator approval phrase")
    args = parser.parse_args(argv)
    if args.execute:
        demand(args.approval == "AUTORIZO-CUTOVER-SINTETICOS-V37", "HUMAN_APPROVAL_REQUIRED")
    else:
        demand(not args.approval, "APPROVAL_WITHOUT_EXECUTE")
    r = Runner()
    _, env, secret, tmpfs, state_dir = preflight(r)
    if not args.execute:
        stdout("CUTOVER_PREFLIGHT=PASS")
        stdout("PRODUCTION_CHANGES=NONE")
        return 0
    def abort_signal(_signum, _frame):
        raise GateError("TRANSACTION_INTERRUPTED_SIGNAL")
    signal.signal(signal.SIGTERM, abort_signal)
    signal.signal(signal.SIGHUP, abort_signal)
    cutover(r, env, secret, tmpfs, state_dir)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GateError as exc:
        print("CUTOVER_GATE_FAILED=" + str(exc), file=sys.stderr)
        raise SystemExit(1)
    except BaseException:
        print("CUTOVER_FAILED=CHECK_REMOTE_TRANSACTION_LOG", file=sys.stderr)
        raise SystemExit(1)
