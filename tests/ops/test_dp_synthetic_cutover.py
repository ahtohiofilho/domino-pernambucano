import json
import sys
import tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "deploy" / "release"))
import unittest
from unittest import mock

import dp_synthetic_cutover as c


def identity_state(n=60):
    return {'schemaVersion': 1, 'baseUrl': 'https://api.dominope.com.br', 'accounts': [
        {'profileIndex': i, 'accountId': f'account-{i}', 'playerId': f'player-{i}',
         'accessToken': f'secret-{i}', 'expiresAtEpochMillis': 1770000000000} for i in range(1, n+1)
    ]}


def baseline(secret_path):
    env = {
        'PATH': '/usr/bin', 'HOME': '/opt/domino-synthetic', 'LANG': 'C.UTF-8',
        'JAVA_OPTS': '-XX:MaxRAMPercentage=50.0',
        'HOSTNAME': 'aab', 'TERM': 'xterm', 'JAVA_HOME': '/opt/java',
        'DOMINO_SYNTHETIC_RUNTIME_TARGET': 'REMOTE_PRODUCTION',
        'DOMINO_SYNTHETIC_BASE_URL': 'https://api.dominope.com.br',
        'DOMINO_SYNTHETIC_CLIENT_STATE_DIR': '/state',
        'DOMINO_SYNTHETIC_POPULATION_SIZE': '60',
        'DOMINO_SYNTHETIC_STANDBY_POOL_SIZE': '6',
        'DOMINO_SYNTHETIC_STANDBY_POLL_INTERVAL_MILLIS': '5000',
        'DOMINO_SYNTHETIC_POLL_INTERVAL_MILLIS': '750',
        'DOMINO_SYNTHETIC_HEARTBEAT_FILE': '/state/runtime-heartbeat',
        'DOMINO_SYNTHETIC_MAX_SILENCE_MILLIS': '180000',
        'DOMINO_SYNTHETIC_PROVISIONING_SECRET_FILE': '/run/secrets/synthetic_provisioning_secret',
    }
    check = {'Test': ['CMD-SHELL', 'true'], 'Interval': 30000000000}
    cfg = {'User':'10002:10002','WorkingDir':'/opt/domino-synthetic','StopSignal':'SIGTERM',
           'Entrypoint':['/opt/domino-synthetic/bin/miniproduction-client'],
           'Healthcheck':check,'Env':[k+'='+v for k,v in env.items()]}
    host = {'ReadonlyRootfs': True, 'Privileged':False, 'Init':True,'AutoRemove':False,
            'CapDrop':['ALL'], 'CapAdd':None, 'SecurityOpt':['no-new-privileges:true'],
            'NetworkMode':'bridge','RestartPolicy':{'Name':'unless-stopped'},
            'LogConfig':{'Type':'local'}, 'PortBindings':{},'Devices':None,'Ulimits':None,
            'IpcMode':'private','PidMode':'','Memory':0,'CpuShares':0,'NanoCpus':0,
            'PidsLimit':None,'Tmpfs':{'/tmp':'size=16777216,mode=1777'}}
    obj = {'Image':c.OLD_IMAGE,'Config':cfg,'HostConfig':host,
           'State':{'Status':'running','Health':{'Status':'healthy'}},
           'NetworkSettings':{'Networks':{'bridge':{}}},
           'Mounts':[
               {'Destination':'/state','Type':'volume','Name':c.STATE_VOLUME,'RW':True},
               {'Destination':'/run/secrets/synthetic_provisioning_secret',
                'Type':'bind','RW':False,'Source':str(secret_path)}]}
    old={'Id':c.OLD_IMAGE,'Config':{'Healthcheck':check}}
    new={'Id':c.NEW_IMAGE,'Config':{'Healthcheck':check,'User':'10002:10002',
                                 'Entrypoint':cfg['Entrypoint']},'Os':'linux','Architecture':'amd64'}
    return obj,old,new,env


class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.secret = self.root/'secret'
        self.secret.write_text('private')
        self.obj,self.old,self.new,self.env = baseline(self.secret)

    def test_valid_contract(self):
        env,secret,tmpfs = c.validate_contract(self.obj,self.old,self.new)
        self.assertEqual(env,self.env)
        self.assertEqual(secret,str(self.secret))
        self.assertEqual(tmpfs['/tmp'],'size=16777216,mode=1777')

    def test_old_image_drift_rejected(self):
        self.obj['Image']='sha256:other'
        with self.assertRaisesRegex(c.GateError,'OLD_IMAGE_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_extra_mount_rejected(self):
        self.obj['Mounts'].append({'Destination':'/etc','Type':'bind'})
        with self.assertRaisesRegex(c.GateError,'MOUNT_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_secret_rw_rejected(self):
        self.obj['Mounts'][1]['RW']=True
        with self.assertRaisesRegex(c.GateError,'SECRET_MOUNT_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_caps_rejected(self):
        self.obj['HostConfig']['CapDrop']=None
        with self.assertRaisesRegex(c.GateError,'CAP_DROP_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_unexpected_credential_environment_rejected(self):
        self.obj['Config']['Env'].append('TOKEN=secret')
        with self.assertRaisesRegex(c.GateError,'ENV_CONTRACT_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_new_healthcheck_drift_rejected(self):
        self.new['Config']['Healthcheck']={'Test':['CMD-SHELL','false']}
        with self.assertRaisesRegex(c.GateError,'NEW_HEALTHCHECK_DRIFT'):
            c.validate_contract(self.obj,self.old,self.new)

    def test_valid_sixty_accounts(self):
        file = self.root/'state.json';file.write_text(json.dumps(identity_state()))
        self.assertEqual(len(c.identities(file)),60)

    def test_missing_account_rejected(self):
        file = self.root/'state.json';file.write_text(json.dumps(identity_state(59)))
        with self.assertRaisesRegex(c.GateError,'STATE_ACCOUNT_COUNT_INVALID'):
            c.identities(file)

    def test_duplicate_player_id_rejected(self):
        state=identity_state();state['accounts'][1]['playerId']=state['accounts'][0]['playerId']
        file=self.root/'state.json';file.write_text(json.dumps(state))
        with self.assertRaisesRegex(c.GateError,'STATE_DUPLICATE'):
            c.identities(file)

    def test_env_file_avoids_command_line_secrets(self):
        args=c.create_argv(Path('/run/secret.env'), str(self.secret), {'/tmp':'size=16777216,mode=1777'})
        self.assertIn('--env-file',args)
        self.assertNotIn('secret-1', ' '.join(args))
        self.assertIn('--read-only',args)
        self.assertIn('--cap-drop',args)
        self.assertIn(c.STATE_VOLUME+':/state:rw',args)
        self.assertNotIn('start',args)

    def test_approval_explicit(self):
        with self.assertRaisesRegex(c.GateError,'HUMAN_APPROVAL_REQUIRED'):
            c.main(['--execute'])
        with self.assertRaisesRegex(c.GateError,'APPROVAL_WITHOUT_EXECUTE'):
            c.main(['--approval','AUTORIZO-CUTOVER-SINTETICOS-V37'])

    def test_consistent_backup_copy(self):
        state_dir=self.root/'state';state_dir.mkdir()
        state_dir.joinpath('synthetic-identities.json').write_text(json.dumps(identity_state()))
        with mock.patch.object(c,'BACKUP_ROOT',self.root/'backups'):
            path,digest=c.consistent_backup(state_dir)
            self.assertEqual(len(c.identities(path)),60)
            self.assertEqual(c.hash_file(path),digest)
            self.assertEqual(path.stat().st_mode&0o777,0o600)

    def test_old_running_until_prepared_and_verified(self):
        runner = SyntheticFakeRunner(self.obj)
        with mock.patch.object(c, 'ENV_TMP_ROOT', self.root), \
             mock.patch.object(c, 'consistent_backup', return_value=(self.root/'consistent.json', 'a'*64)), \
             mock.patch.object(c, 'assert_new_container') as check_new, \
             mock.patch.object(c, 'health_smoke') as health:
            c.cutover(runner, self.env, str(self.secret), {'/tmp':'size=16777216,mode=1777'}, self.root)
        self.assertTrue(health.called)
        self.assertTrue(check_new.called)
        self.assertEqual(runner.names[c.NAME], 'new')
        self.assertEqual(runner.names[c.OLD_PARKED], 'old')
        self.assertEqual(runner.states['old'], 'exited')
        self.assertEqual(runner.states['new'], 'running')
        self.assertLess(runner.ops.index('create'), runner.ops.index('stop'))
        self.assertLess(runner.ops.index('inspect-prepared'), runner.ops.index('stop'))

    def test_rollback_if_new_start_fails(self):
        runner = SyntheticFakeRunner(self.obj, fail_new_start=True)
        with mock.patch.object(c, 'ENV_TMP_ROOT', self.root), \
             mock.patch.object(c, 'consistent_backup', return_value=(self.root/'consistent.json', 'a'*64)), \
             mock.patch.object(c, 'assert_new_container'), \
             mock.patch.object(c, 'health_smoke'):
            with self.assertRaises(c.GateError):
                c.cutover(runner, self.env, str(self.secret), {'/tmp':'size=16777216,mode=1777'}, self.root)
        self.assertEqual(runner.names[c.NAME], 'old')
        self.assertEqual(runner.states['old'], 'running')
        self.assertEqual(runner.names[c.FAILED_PARKED], 'new')
        self.assertEqual(runner.states['new'], 'created')

    def test_rollback_if_new_smoke_fails(self):
        runner = SyntheticFakeRunner(self.obj)
        with mock.patch.object(c, 'ENV_TMP_ROOT', self.root), \
             mock.patch.object(c, 'consistent_backup', return_value=(self.root/'consistent.json', 'a'*64)), \
             mock.patch.object(c, 'assert_new_container'), \
             mock.patch.object(c, 'health_smoke', side_effect=c.GateError('SMOKE_FAILED')):
            with self.assertRaises(c.GateError):
                c.cutover(runner, self.env, str(self.secret), {'/tmp':'size=16777216,mode=1777'}, self.root)
        self.assertEqual(runner.names[c.NAME], 'old')
        self.assertEqual(runner.states['old'], 'running')
        self.assertEqual(runner.names[c.FAILED_PARKED], 'new')
        self.assertEqual(runner.states['new'], 'exited')
        self.assertLess(runner.ops.index('stop-new'), runner.ops.index('start-old'))

    def test_rollback_if_final_backup_fails(self):
        runner = SyntheticFakeRunner(self.obj)
        with mock.patch.object(c, 'ENV_TMP_ROOT', self.root), \
             mock.patch.object(c, 'consistent_backup', side_effect=c.GateError('BACKUP_FAILED')), \
             mock.patch.object(c, 'assert_new_container'):
            with self.assertRaises(c.GateError):
                c.cutover(runner, self.env, str(self.secret), {'/tmp':'size=16777216,mode=1777'}, self.root)
        self.assertEqual(runner.names[c.NAME], 'old')
        self.assertEqual(runner.states['old'], 'running')
        self.assertEqual(runner.names[c.FAILED_PARKED], 'new')
        self.assertEqual(runner.states['new'], 'created')


class SyntheticFakeRunner:
    def __init__(self, old_config, fail_new_start=False):
        self.names={c.NAME:'old'}
        self.states={'old':'running'}
        self.ops=[]
        self.fail_new_start=fail_new_start

    def inspect(self,name,kind='container'):
        self.ops.append('inspect-prepared' if name == c.NEW_PREPARED else 'inspect-other')
        token=self.names[name]
        return {'State':{'Status':self.states[token],
                         'Health':{'Status':'healthy' if token=='old' else 'starting'}}}

    def docker(self,*args):
        if args[0]=='create':
            name=args[args.index('--name')+1]
            assert name not in self.names
            self.names[name]='new'
            self.states['new']='created'
            self.ops.append('create')
        elif args[0]=='stop':
            target=args[-1]
            token=self.names[target]
            self.states[token]='exited'
            self.ops.append('stop-new' if token=='new' else 'stop')
        elif args[0]=='start':
            target=args[-1]
            token=self.names[target]
            if token == 'new' and self.fail_new_start:
                raise c.GateError('FAKE_START_NEW_FAILED')
            self.states[token]='running'
            self.ops.append('start-new' if token=='new' else 'start-old')
        elif args[0]=='rename':
            _,src,dst=args
            self.names[dst]=self.names.pop(src)
            self.ops.append('rename-'+src)
        else:
            raise AssertionError(args)
        return ''


if __name__=='__main__':
    unittest.main()
