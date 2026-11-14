import copy
import hashlib
import json
import subprocess
import sys
import tempfile
from pathlib import Path
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "deploy" / "release"))
import dp_server_v37_allowlist_cutover as c


def fixture(source):
    state = source / "authoritative-state.json"
    state.write_text('{"schemaVersion":13,"status":"ok"}', encoding="utf-8")
    env = {
        "DOMINO_SERVER_ENVIRONMENT": "production",
        "DOMINO_SERVER_STATE_FILE": "/data/authoritative-state.json",
        "DOMINO_SESSION_SIGNING_SECRET": "THIS_IS_A_TEST_SECRET_DO_NOT_LOG",
        "DOMINO_PRODUCTION_RANKED_ACCESS_MODE": "versioned",
        "DOMINO_PRODUCTION_REQUIRED_CLIENT_VERSION_CODE": "32",
        c.ENV_KEY: c.OLD_ALLOWED,
    }
    for i in range(18):
        env[f"OTHER_{i}"] = f"value_{i}"
    assert len(env) == 24
    cfg = {
        "Image": "domino-server:v35-854871d", "User": "10001:10001",
        "StopSignal": "SIGTERM", "Healthcheck": None,
        "Tty": False, "OpenStdin": False,
        "Entrypoint": ["/app/bin/server"], "Cmd": None,
        "WorkingDir": "/opt", "Labels": None,
        "Env": [f"{key}={value}" for key, value in env.items()]
    }
    host = {
        "ReadonlyRootfs": False, "Privileged": False, "Init": None,
        "AutoRemove": False, "CapAdd": None, "CapDrop": None,
        "SecurityOpt": None, "Tmpfs": None, "Devices": None, "Ulimits": None,
        "NetworkMode": "bridge", "IpcMode": "private", "PidMode": "",
        "RestartPolicy": {"Name": "unless-stopped"},
        "PortBindings": {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8080"}]},
        "Memory": 0, "CpuShares": 0, "NanoCpus": 0, "CpuQuota": 0,
        "CpuPeriod": 0, "PidsLimit": None, "MemorySwap": 0,
        "MemoryReservation": 0, "StopTimeout": None,
        "ExtraHosts": None, "Dns": None, "Links": None,
        "VolumesFrom": None, "PublishAllPorts": False,
        "LogConfig": {"Type": "local", "Config": {
            "max-size": "10m", "max-file": "5"
        }},
    }
    obj = {
        "Image": c.IMAGE, "Config": cfg, "HostConfig": host,
        "State": {"Status": "running"},
        "NetworkSettings": {"Networks": {"bridge": {}}},
        "Mounts": [{"Type": "bind", "Destination": "/data",
                    "Source": str(source), "RW": True}]
    }
    image = {"Id": c.IMAGE, "Os": "linux", "Architecture": "amd64"}
    return obj, image, env, state


def prepared_from(old, env):
    obj = copy.deepcopy(old)
    obj["Config"]["Image"] = c.IMAGE
    obj["Config"]["Env"] = [f"{key}={value}" for key, value in env.items()]
    obj["State"]["Status"] = "created"
    return obj


class FakeRunner:
    def __init__(self, original, env):
        self.containers = {c.NAME: copy.deepcopy(original)}
        self.new_env = dict(env)
        self.new_env[c.ENV_KEY] = c.NEW_ALLOWED
        self.events = []
        self.fail_new_start = False

    def inspect(self, name, kind="container"):
        if kind == "image":
            return {"Id": c.IMAGE, "Os": "linux", "Architecture": "amd64"}
        return self.containers[name]

    def names(self):
        return set(self.containers)

    def docker(self, *args):
        self.events.append(tuple(args))
        op = args[0]
        if op == "create":
            if c.PREPARED in self.containers:
                raise c.GateError("PREPARED_EXISTS")
            self.containers[c.PREPARED] = prepared_from(
                self.containers[c.NAME], self.new_env
            )
        elif op == "stop":
            self.containers[args[-1]]["State"]["Status"] = "exited"
        elif op == "rename":
            _, previous, next_name = args
            self.containers[next_name] = self.containers.pop(previous)
        elif op == "start":
            name = args[1]
            if self.fail_new_start and self.containers[name]["Config"]["Image"] == c.IMAGE:
                raise c.GateError("INJECTED_NEW_START_FAILURE")
            self.containers[name]["State"]["Status"] = "running"
        elif op == "update":
            self.containers[args[-1]]["HostConfig"]["RestartPolicy"]["Name"] = args[2]
        elif op == "rm":
            self.containers.pop(args[1])
        else:
            raise AssertionError(args)
        return ""


class ServerAllowlistTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.state_dir = self.root / "state"
        self.state_dir.mkdir()
        self.obj, self.image, self.env, self.state = fixture(self.state_dir)
        self.backups = self.root / "backups"
        self.run = self.root / "run"
        self.run.mkdir()

    def test_valid_contract(self):
        env, source, state, options = c.validate_contract(self.obj, self.image)
        self.assertEqual(env, self.env)
        self.assertEqual(source, self.state_dir)
        self.assertEqual(state, self.state)
        self.assertEqual(options, {"max-file": "5", "max-size": "10m"})

    def test_rejects_wrong_old_allowlist_without_changes(self):
        self.obj["Config"]["Env"] = [
            value.replace(c.OLD_ALLOWED, c.NEW_ALLOWED)
            for value in self.obj["Config"]["Env"]
        ]
        with self.assertRaisesRegex(c.GateError, "OLD_ALLOWLIST_DRIFT"):
            c.validate_contract(self.obj, self.image)

    def test_rejects_runtime_drift(self):
        mutations = [
            ("IMAGE_DRIFT", lambda x: x.update(Image="sha256:unknown")),
            ("PORT_BINDINGS_DRIFT", lambda x: x["HostConfig"].update(PortBindings={})),
            ("CAP_SECURITY_DRIFT", lambda x: x["HostConfig"].update(CapDrop=["ALL"])),
            ("MOUNT_DRIFT", lambda x: x["Mounts"][0].update(RW=False)),
            ("ENV_COUNT_DRIFT", lambda x: x["Config"]["Env"].append("EXTRA=1")),
            ("LOG_OPTIONS_DRIFT", lambda x: x["HostConfig"]["LogConfig"]["Config"].update(extra="1")),
        ]
        for expected, mutation in mutations:
            with self.subTest(expected=expected):
                changed = copy.deepcopy(self.obj)
                mutation(changed)
                with self.assertRaisesRegex(c.GateError, expected):
                    c.validate_contract(changed, self.image)

    def test_no_secrets_in_docker_create_argv(self):
        args = c.create_argv(self.state_dir, self.root / "private.env",
                             {"max-file": "5", "max-size": "10m"})
        self.assertNotIn(self.env["DOMINO_SESSION_SIGNING_SECRET"], " ".join(args))
        self.assertEqual(args.count("--env-file"), 1)
        self.assertIn("127.0.0.1:8080:8080", args)
        self.assertEqual(args[-1], c.IMAGE)
        self.assertNotIn("--replace", args)

    def test_prepared_requires_exact_environment(self):
        r = FakeRunner(self.obj, self.env)
        correct = dict(self.env)
        correct[c.ENV_KEY] = c.NEW_ALLOWED
        r.containers[c.PREPARED] = prepared_from(self.obj, correct)
        c.assert_prepared(r, self.obj, correct,
                          {"max-file": "5", "max-size": "10m"})
        r.containers[c.PREPARED]["Config"]["Env"].append("EXTRA=SHOULD_FAIL")
        with self.assertRaisesRegex(c.GateError, "PREPARED_ENV_DRIFT"):
            c.assert_prepared(r, self.obj, correct,
                              {"max-file": "5", "max-size": "10m"})

    def test_preflight_is_read_only_and_blocks_existing_cutover(self):
        r = FakeRunner(self.obj, self.env)
        with mock.patch.object(c.os, "geteuid", return_value=0), \
             mock.patch.object(c, "api_health"):
            original, env, source, state, options = c.preflight(r)
            self.assertEqual(original["Image"], c.IMAGE)
            self.assertEqual(env[c.ENV_KEY], c.OLD_ALLOWED)
            self.assertEqual(r.events, [])
            r.containers[c.PARKED] = {}
            with self.assertRaisesRegex(c.GateError, "PREVIOUS_SERVER_CUTOVER_PRESENT"):
                c.preflight(r)

    def test_backup_consistent_and_private(self):
        with mock.patch.object(c, "BACKUP_ROOT", self.backups):
            path, digest = c.backup_state(self.state)
        self.assertTrue(path.is_file())
        self.assertEqual(digest, hashlib.sha256(self.state.read_bytes()).hexdigest())
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(path.read_bytes(), self.state.read_bytes())

    def test_transaction_success_preserves_old_container_and_state(self):
        r = FakeRunner(self.obj, self.env)
        data_before = self.state.read_bytes()
        with mock.patch.object(c, "ENV_TMP_ROOT", self.run), \
             mock.patch.object(c, "BACKUP_ROOT", self.backups), \
             mock.patch.object(c, "wait_healthy"):
            c.execute(r, self.obj, self.env, self.state_dir, self.state,
                      {"max-file": "5", "max-size": "10m"})
        self.assertEqual(r.containers[c.NAME]["Config"]["Image"], c.IMAGE)
        self.assertEqual(
            c.parse_env(r.containers[c.NAME]["Config"]["Env"])[c.ENV_KEY], c.NEW_ALLOWED
        )
        self.assertEqual(r.containers[c.PARKED]["State"]["Status"], "exited")
        self.assertEqual(self.state.read_bytes(), data_before)
        self.assertEqual(len(list(self.backups.iterdir())), 1)
        self.assertEqual(r.containers[c.PARKED]["HostConfig"]["RestartPolicy"]["Name"], "no")
        names = [event[0] for event in r.events]
        self.assertLess(names.index("create"), names.index("stop"))
        self.assertLess(names.index("stop"), names.index("start"))

    def test_failed_new_start_rolls_back_without_reverting_state(self):
        r = FakeRunner(self.obj, self.env)
        r.fail_new_start = True
        before = self.state.read_bytes()
        with mock.patch.object(c, "ENV_TMP_ROOT", self.run), \
             mock.patch.object(c, "BACKUP_ROOT", self.backups), \
             mock.patch.object(c, "wait_healthy"):
            with self.assertRaisesRegex(c.GateError, "INJECTED_NEW_START_FAILURE"):
                c.execute(r, self.obj, self.env, self.state_dir, self.state,
                          {"max-file": "5", "max-size": "10m"})
        self.assertEqual(r.containers[c.NAME]["State"]["Status"], "running")
        self.assertEqual(c.parse_env(r.containers[c.NAME]["Config"]["Env"])[c.ENV_KEY],
                         c.OLD_ALLOWED)
        self.assertIn(c.FAILED, r.containers)
        self.assertEqual(self.state.read_bytes(), before)

    def test_cli_process_success_does_not_become_error(self):
        with mock.patch.object(c, "main", return_value=0):
            self.assertEqual(c.cli(), 0)
        code = (
            "import sys; sys.path.insert(0, " +
            repr(str(Path(c.__file__).parent)) + "); " +
            "import dp_server_v37_allowlist_cutover as c; " +
            "c.main=lambda: 0; raise SystemExit(c.cli())"
        )
        result = subprocess.run([sys.executable, "-c", code],
                                capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_approval_required_before_docker(self):
        result = subprocess.run(
            [sys.executable, str(Path(c.__file__)), "--execute"],
            capture_output=True, text=True, check=False
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("APPROVAL_REQUIRED", result.stdout)
        self.assertNotIn("Traceback", result.stderr)


if __name__ == "__main__":
    unittest.main()
