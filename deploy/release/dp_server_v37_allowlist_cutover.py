#!/usr/bin/env python3
"""Safe v37 ranked-queue allowlist correction. Read-only preflight by default.

Recreates only domino-server, with exactly the same immutable image and persisted
/data bind. No secrets or full docker inspect are printed. The old container
remains parked for rollback. The original authoritative state is never restored
automatically over live data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

NAME = "domino-server"
PREPARED = "domino-server-v37-allowlist-prepared"
PARKED = "domino-server-rollback-v37-allowlist-20261114"
FAILED = "domino-server-v37-allowlist-failed"
IMAGE = "sha256:6f5a54b7f6f5077f21706f0a8de797feae8a3f249c6500847d3efb32dab25b4b"
OLD_ALLOWED = "32,33,34,35,36"
NEW_ALLOWED = "32,33,34,35,36,37"
ENV_KEY = "DOMINO_PRODUCTION_ALLOWED_CLIENT_VERSION_CODES"
BACKUP_ROOT = Path("/var/backups/domino-pe/server")
APPROVAL = "AUTORIZO-CORRECAO-ALLOWLIST-V37"


class GateError(RuntimeError):
    pass


def demand(ok: bool, code: str) -> None:
    if not ok:
        raise GateError(code)


def log(message: str) -> None:
    print(message, flush=True)


class Runner:
    def docker(self, *args: str) -> str:
        result = subprocess.run(
            ["docker", *args], capture_output=True, text=True, check=False
        )
        if result.returncode:
            # Docker errors can contain environment values or secrets.
            raise GateError("DOCKER_COMMAND_FAILED")
        return result.stdout

    def inspect(self, name: str, kind: str = "container") -> dict:
        if kind == "container":
            result = self.docker("inspect", "--type", "container", name)
        elif kind == "image":
            result = self.docker("image", "inspect", name)
        else:
            raise GateError("INSPECT_KIND_INVALID")
        return json.loads(result)[0]

    def names(self) -> set[str]:
        return set(self.docker("ps", "-a", "--format", "{{.Names}}").splitlines())


def parse_env(entries: list[str]) -> dict[str, str]:
    result = {}
    for entry in entries:
        key, separator, value = entry.partition("=")
        demand(separator == "=" and
               re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", key) is not None
               and key not in result, "ENV_FORMAT_DRIFT")
        demand("\n" not in value and "\r" not in value, "ENV_MULTILINE_UNSUPPORTED")
        result[key] = value
    return result


def check_log_options(host: dict) -> dict[str, str]:
    options = (host.get("LogConfig") or {}).get("Config") or {}
    demand(host.get("LogConfig", {}).get("Type") == "local", "LOG_DRIVER_DRIFT")
    demand(set(options) == {"max-file", "max-size"}, "LOG_OPTIONS_DRIFT")
    demand(re.fullmatch(r"[1-9][0-9]*", options["max-file"]) is not None,
           "LOG_FILE_OPTION_INVALID")
    demand(re.fullmatch(r"[1-9][0-9]*[kKmMgG]", options["max-size"]) is not None,
           "LOG_SIZE_OPTION_INVALID")
    return options


def api_health() -> None:
    for path in ("health", "ready"):
        for base in ("http://127.0.0.1:8080/", "https://api.dominope.com.br/"):
            try:
                with urllib.request.urlopen(base + path, timeout=10) as result:
                    demand(result.status == 200, "API_UNHEALTHY")
            except Exception:
                raise GateError("API_UNHEALTHY") from None


def validate_contract(container: dict, image: dict) -> tuple[dict, Path, Path, dict]:
    cfg, host = container["Config"], container["HostConfig"]
    demand(container["Image"] == IMAGE and image["Id"] == IMAGE, "IMAGE_DRIFT")
    demand(container["State"]["Status"] == "running", "SERVER_NOT_RUNNING")
    demand(cfg["Image"] == "domino-server:v35-854871d", "TAG_DRIFT")
    demand(cfg.get("User") == "10001:10001", "USER_DRIFT")
    demand(cfg.get("StopSignal") == "SIGTERM", "STOP_SIGNAL_DRIFT")
    demand(cfg.get("Healthcheck") in (None, {}), "HEALTHCHECK_DRIFT")
    demand(cfg.get("Tty") is False and cfg.get("OpenStdin") is False, "TTY_DRIFT")
    demand(host.get("ReadonlyRootfs") is False and
           host.get("Privileged") is False, "PRIVILEGE_DRIFT")
    demand(host.get("Init") in (None, False) and
           host.get("AutoRemove") is False, "INIT_AUTOREMOVE_DRIFT")
    demand(not host.get("CapAdd") and not host.get("CapDrop") and
           not host.get("SecurityOpt"), "CAP_SECURITY_DRIFT")
    demand(not host.get("Tmpfs") and not host.get("Devices") and
           not host.get("Ulimits"), "RUNTIME_EXTRAS_DRIFT")
    demand(host.get("NetworkMode") == "bridge", "NETWORK_DRIFT")
    demand(set((container["NetworkSettings"].get("Networks") or {}).keys()) ==
           {"bridge"}, "NETWORK_ATTACHMENT_DRIFT")
    demand(host.get("IpcMode") == "private" and
           not host.get("PidMode"), "NAMESPACE_DRIFT")
    demand(host.get("RestartPolicy", {}).get("Name") == "unless-stopped",
           "RESTART_POLICY_DRIFT")
    demand(host.get("PortBindings") == {
        "8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8080"}]
    }, "PORT_BINDINGS_DRIFT")
    demand(all(host.get(key) in (None, 0, []) for key in (
        "Memory", "CpuShares", "NanoCpus", "CpuQuota", "CpuPeriod", "PidsLimit",
        "MemorySwap", "MemoryReservation",
    )), "RESOURCE_LIMIT_DRIFT")
    demand(host.get("StopTimeout") in (None, 0), "STOP_TIMEOUT_DRIFT")
    demand(not host.get("ExtraHosts") and not host.get("Dns") and
           not host.get("Links") and not host.get("VolumesFrom") and
           not host.get("PublishAllPorts"), "NETWORK_EXTRAS_DRIFT")
    options = check_log_options(host)
    mounts = container.get("Mounts") or []
    demand(len(mounts) == 1, "MOUNT_COUNT_DRIFT")
    mount = mounts[0]
    demand(mount.get("Type") == "bind" and mount.get("Destination") == "/data"
           and mount.get("RW") is True, "MOUNT_DRIFT")
    source = Path(mount["Source"])
    demand(source.is_dir() and not source.is_symlink() and
           "," not in str(source) and "\n" not in str(source), "STATE_DIR_DRIFT")
    env = parse_env(cfg.get("Env") or [])
    demand(len(env) == 24, "ENV_COUNT_DRIFT")
    demand(env.get("DOMINO_SERVER_ENVIRONMENT") == "production", "SERVER_ENV_DRIFT")
    demand(env.get("DOMINO_PRODUCTION_RANKED_ACCESS_MODE") == "versioned",
           "RANKED_MODE_DRIFT")
    demand(env.get("DOMINO_PRODUCTION_REQUIRED_CLIENT_VERSION_CODE") == "32",
           "RANKED_REQUIRED_VERSION_DRIFT")
    demand(env.get(ENV_KEY) == OLD_ALLOWED, "OLD_ALLOWLIST_DRIFT")
    demand(bool(env.get("DOMINO_SESSION_SIGNING_SECRET")), "SESSION_SECRET_MISSING")
    state_name = env.get("DOMINO_SERVER_STATE_FILE", "/data/authoritative-state.json")
    demand(state_name == "/data/authoritative-state.json", "STATE_PATH_DRIFT")
    state_file = source / "authoritative-state.json"
    demand(state_file.is_file() and not state_file.is_symlink() and
           state_file.stat().st_size > 0, "STATE_FILE_DRIFT")
    demand(shutil.disk_usage(source).free > max(64 * 1024 * 1024,
           4 * state_file.stat().st_size), "INSUFFICIENT_BACKUP_SPACE")
    demand(image.get("Os") == "linux" and image.get("Architecture") == "amd64",
           "IMAGE_PLATFORM_DRIFT")
    return env, source, state_file, options


def preflight(r: Runner) -> tuple[dict, dict, Path, Path, dict]:
    demand(os.geteuid() == 0, "ROOT_REQUIRED")
    container = r.inspect(NAME)
    image = r.inspect(IMAGE, "image")
    env, source, state_file, options = validate_contract(container, image)
    demand(not ({PREPARED, PARKED, FAILED} & r.names()),
           "PREVIOUS_SERVER_CUTOVER_PRESENT")
    api_health()
    log("SERVER_IMAGE_UNCHANGED=PASS")
    log("SERVER_RUNTIME_CONTRACT=PASS")
    log("SERVER_PERSISTENCE=VERIFIED")
    log("CURRENT_ALLOWED_VERSIONS=" + OLD_ALLOWED)
    log("TARGET_ALLOWED_VERSIONS=" + NEW_ALLOWED)
    log("API_HEALTH_READY=PASS")
    return container, env, source, state_file, options


def create_argv(source: Path, env_file: Path, options: dict[str, str]) -> list[str]:
    args = [
        "create", "--name", PREPARED, "--network", "bridge",
        "--publish", "127.0.0.1:8080:8080", "--restart", "unless-stopped",
        "--user", "10001:10001", "--stop-signal", "SIGTERM",
        "--mount", "type=bind,source=" + str(source) + ",target=/data",
        "--env-file", str(env_file), "--log-driver", "local",
    ]
    for key in sorted(options):
        args.extend(["--log-opt", key + "=" + options[key]])
    return args + [IMAGE]


def assert_prepared(r: Runner, original: dict, expected_env: dict,
                    options: dict) -> None:
    obj = r.inspect(PREPARED)
    cfg, host = obj["Config"], obj["HostConfig"]
    demand(obj["Image"] == IMAGE and obj["State"]["Status"] == "created",
           "PREPARED_IMAGE_OR_STATE_DRIFT")
    demand(parse_env(cfg.get("Env") or []) == expected_env, "PREPARED_ENV_DRIFT")
    demand(cfg.get("User") == original["Config"].get("User") and
           cfg.get("Entrypoint") == original["Config"].get("Entrypoint") and
           cfg.get("Cmd") == original["Config"].get("Cmd") and
           cfg.get("WorkingDir") == original["Config"].get("WorkingDir") and
           cfg.get("StopSignal") == original["Config"].get("StopSignal") and
           cfg.get("Labels") == original["Config"].get("Labels"),
           "PREPARED_CONFIG_DRIFT")
    demand(host.get("PortBindings") == original["HostConfig"].get("PortBindings")
           and host.get("NetworkMode") == "bridge"
           and host.get("RestartPolicy", {}).get("Name") == "unless-stopped",
           "PREPARED_HOST_DRIFT")
    demand(host.get("LogConfig", {}).get("Type") == "local" and
           (host.get("LogConfig", {}).get("Config") or {}) == options,
           "PREPARED_LOG_DRIFT")
    demand(host.get("ReadonlyRootfs") is False and
           host.get("Privileged") is False and
           not host.get("CapAdd") and not host.get("CapDrop"),
           "PREPARED_SECURITY_DRIFT")
    demand(len(obj["Mounts"]) == 1 and
           obj["Mounts"][0]["Destination"] == "/data" and
           obj["Mounts"][0]["Source"] == original["Mounts"][0]["Source"] and
           obj["Mounts"][0]["RW"], "PREPARED_DATA_DRIFT")


def backup_state(path: Path) -> tuple[Path, str]:
    BACKUP_ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    demand(BACKUP_ROOT.stat().st_uid == 0, "BACKUP_DIRECTORY_OWNER_DRIFT")
    os.chmod(BACKUP_ROOT, 0o700)
    handle, name = tempfile.mkstemp(prefix="state-v37-allowlist-", suffix=".json", dir=BACKUP_ROOT)
    output = Path(name)
    try:
        os.fchmod(handle, 0o600)
        with os.fdopen(handle, "wb") as target, path.open("rb") as original:
            shutil.copyfileobj(original, target)
            target.flush()
            os.fsync(target.fileno())
        demand(output.stat().st_size == path.stat().st_size, "BACKUP_SIZE_MISMATCH")
        digest = hashlib.sha256(output.read_bytes()).hexdigest()
        demand(digest == hashlib.sha256(path.read_bytes()).hexdigest(), "BACKUP_HASH_MISMATCH")
        return output, digest
    except BaseException:
        output.unlink(missing_ok=True)
        raise


def rollback(r: Runner, stopped: bool, renamed: bool,
             new_named: bool, prepared_exists: bool) -> None:
    log("SERVER_ROLLBACK_TRIGGERED=YES")
    if new_named:
        if r.inspect(NAME)["State"]["Status"] == "running":
            r.docker("stop", "--time", "60", NAME)
        demand(r.inspect(NAME)["State"]["Status"] != "running",
               "FAILED_SERVER_NOT_STOPPED")
        r.docker("rename", NAME, FAILED)
    elif prepared_exists:
        demand(r.inspect(PREPARED)["State"]["Status"] != "running",
               "PREPARED_UNEXPECTEDLY_RUNNING")
        r.docker("rm", PREPARED)
    if renamed:
        r.docker("rename", PARKED, NAME)
    if stopped:
        r.docker("update", "--restart", "unless-stopped", NAME)
        r.docker("start", NAME)
        demand(r.inspect(NAME)["State"]["Status"] == "running",
               "ROLLBACK_SERVER_NOT_RUNNING")
        api_health()
        log("SERVER_ROLLBACK_HEALTH=PASS")
    else:
        log("ORIGINAL_SERVER_UNCHANGED=YES")


def execute(r: Runner, original: dict, env: dict, source: Path, state_file: Path,
            options: dict) -> None:
    prepared_exists = False
    old_stopped = False
    old_renamed = False
    new_named = False
    try:
        expected_env = dict(env)
        expected_env[ENV_KEY] = NEW_ALLOWED
        handle, name = tempfile.mkstemp(prefix="dp-server-v37-env-", dir="/run")
        try:
            os.fchmod(handle, 0o600)
            with os.fdopen(handle, "w", encoding="utf-8") as out:
                for key, value in sorted(expected_env.items()):
                    out.write(key + "=" + value + "\n")
            r.docker(*create_argv(source, Path(name), options))
            prepared_exists = True
        finally:
            Path(name).unlink(missing_ok=True)
        assert_prepared(r, original, expected_env, options)
        log("SERVER_PREPARED_UNSTARTED=PASS")

        old_stopped = True  # rollback even if docker stop partially succeeds
        r.docker("stop", "--time", "60", NAME)
        demand(r.inspect(NAME)["State"]["Status"] != "running",
               "ORIGINAL_SERVER_STILL_RUNNING")
        log("ORIGINAL_SERVER_STOPPED=PASS")
        backup, sha = backup_state(state_file)
        log("SERVER_BACKUP_CREATED=PASS")
        log("SERVER_BACKUP_SHA256=" + sha)
        log("SERVER_BACKUP_FILE=" + str(backup))

        r.docker("rename", NAME, PARKED)
        old_renamed = True
        r.docker("rename", PREPARED, NAME)
        prepared_exists = False
        new_named = True
        r.docker("start", NAME)
        demand(r.inspect(NAME)["State"]["Status"] == "running",
               "NEW_SERVER_NOT_RUNNING")
        api_health()
        demand(parse_env(r.inspect(NAME)["Config"]["Env"])[ENV_KEY] == NEW_ALLOWED,
               "NEW_SERVER_ALLOWLIST_DRIFT")
        r.docker("update", "--restart", "no", PARKED)
        log("SERVER_NEW_HEALTH_READY=PASS")
        log("SERVER_OLD_CONTAINER_PRESERVED=YES")
        log("SERVER_ALLOWLIST_CUTOVER=PASS")
    except BaseException:
        try:
            if prepared_exists or old_stopped:
                rollback(r, old_stopped, old_renamed, new_named, prepared_exists)
                log("SERVER_ROLLBACK_RESULT=PASS")
        except BaseException:
            log("SERVER_ROLLBACK_RESULT=FAILED_MANUAL_INTERVENTION_REQUIRED")
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--approval")
    args = parser.parse_args()
    demand(not args.execute or args.approval == APPROVAL, "APPROVAL_REQUIRED")
    demand(args.execute or args.approval is None, "APPROVAL_WITHOUT_EXECUTE")
    r = Runner()
    original, env, source, state_file, options = preflight(r)
    if args.execute:
        execute(r, original, env, source, state_file, options)
    else:
        log("SERVER_ALLOWLIST_PREFLIGHT=PASS")
        log("PRODUCTION_CHANGES=NONE")
    return 0


def cli() -> int:
    try:
        return main()
    except GateError as error:
        log("SERVER_ALLOWLIST_GATE_FAILED=" + str(error))
        return 1
    except Exception:
        log("SERVER_ALLOWLIST_FAILED=CHECK_REMOTE_TRANSACTION_LOG")
        return 1


if __name__ == "__main__":
    raise SystemExit(cli())
