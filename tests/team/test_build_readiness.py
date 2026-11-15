"""A local build verifies against its own ready services and preserves application data."""
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/team'))
import environment

class BuildReadinessTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        (self.root/'frontend/node_modules').mkdir(parents=True)
        (self.root/'.campus/app').mkdir(parents=True)
        self.saved=b'{"resource_password":"existing-app-secret","note":"preserve existing application settings"}'
        (self.root/'.campus/app/runtime.local').write_bytes(self.saved)
        self.ready=False;self.checked=[];self.commands=[]

    def invoke(self,args,**kwargs):
        command=[str(x) for x in args];self.commands.append(command)
        if 'up' in command and '--wait' in command:self.ready=True
        if 'port' in command:
            return '127.0.0.1:'+{'5432':'18432','1025':'18025','8025':'18026'}[command[-1]]
        if any(x.endswith(('mvnw','mvnw.cmd')) for x in command) and 'verify' in command:
            self.assertTrue(self.ready,'Maven verify ran before isolated PostgreSQL and SMTP became ready')
            actual=kwargs['env']
            for key in ['CAMPUS_DB_URL','CAMPUS_TEST_DB_URL','CAMPUS_TEST_DB_USER','CAMPUS_TEST_DB_PASSWORD','CAMPUS_TEST_SMTP_HOST','CAMPUS_TEST_SMTP_PORT','CAMPUS_TEST_MAILPIT_URL']:
                self.assertTrue(actual.get(key),'Missing isolated verification environment: '+key)
            self.assertTrue(actual['CAMPUS_DB_URL'].endswith('/module_test'))
            self.assertTrue(actual['CAMPUS_TEST_DB_URL'].endswith('/campus_migration_probe'))
            self.assertNotIn('do-not-touch',actual['CAMPUS_DB_URL'])
            self.assertNotEqual('existing-app-secret',actual['CAMPUS_TEST_DB_PASSWORD'])
            self.checked.append(actual.copy())
        return ''

    def build(self):
        picked={'node':'/tools/node','java':'/tools/jdk/bin/java'}
        incoming={'PATH':os.environ.get('PATH',''),'CAMPUS_DB_URL':'jdbc:postgresql://do-not-touch/production'}
        with patch.object(environment,'tools',return_value=(picked,incoming,[])),patch.object(environment,'docker',return_value=['docker']),patch.object(environment,'host_path',side_effect=lambda p:Path(p)),patch.object(environment,'run',side_effect=self.invoke):
            environment.build(self.root,SimpleNamespace(install=False,buffer=False,start=False))

    def test_verify_waits_for_own_services_and_does_not_use_an_application_database(self):
        self.build()
        self.assertEqual(1,len(self.checked))
        self.assertEqual(self.saved,(self.root/'.campus/app/runtime.local').read_bytes())
        shutdown=[call for call in self.commands if 'down' in call]
        self.assertTrue(shutdown,'Only-build must release the temporary verification services')
        self.assertTrue(all('-v' not in call and '--volumes' not in call for call in shutdown),'Keep private test volumes available for diagnosis')

    @unittest.skipIf(os.name=='nt','POSIX wrapper invocation')
    def test_unix_build_runs_wrapper_through_shell_without_requiring_an_executable_bit(self):
        self.build()
        command=next(c for c in self.commands if 'verify' in c)
        self.assertEqual('sh',command[0],'A fresh checkout must build even when mvnw has mode 100644')

    def test_failed_backend_verification_releases_services_and_preserves_original_error(self):
        original=self.invoke
        def failing(command,**kwargs):
            result=original(command,**kwargs)
            if 'verify' in command:raise RuntimeError('expected-verification-failure')
            return result
        self.invoke=failing
        with self.assertRaisesRegex(RuntimeError,'expected-verification-failure'):self.build()
        self.assertTrue(any('down' in c for c in self.commands))
        self.assertEqual(self.saved,(self.root/'.campus/app/runtime.local').read_bytes())

    def test_retry_reuses_test_configuration_and_keeps_application_configuration(self):
        self.build();self.ready=False;self.build()
        self.assertEqual(2,len(self.checked))
        for key in ['CAMPUS_DB_URL','CAMPUS_TEST_DB_URL','CAMPUS_TEST_DB_USER','CAMPUS_TEST_DB_PASSWORD']:
            self.assertEqual(self.checked[0][key],self.checked[1][key])
        self.assertEqual(self.saved,(self.root/'.campus/app/runtime.local').read_bytes())

if __name__=='__main__':unittest.main()
