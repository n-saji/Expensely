"""Exercise release, rollback, and failed-health transitions without AWS or Docker."""
import os,pathlib,subprocess,tempfile
source=pathlib.Path(__file__).with_name('expensely-deploy').read_text()
with tempfile.TemporaryDirectory() as d:
 root=pathlib.Path(d); bins=root/'bin';bins.mkdir();state=root/'state';state.mkdir()
 envfile=root/'app.env';envfile.write_text('VALUE=original\n');upstream=root/'upstream';upstream.write_text('initial upstream\n')
 script=source.replace('[[ $EUID == 0 ]]','[[ true ]]').replace('ROOT=/var/lib/expensely-deploy',f'ROOT={state}').replace('ENV=/etc/expensely/app.env',f'ENV={envfile}').replace('UPSTREAM=/etc/nginx/conf.d/expensely-upstream.conf',f'UPSTREAM={upstream}')
 script=script.replace('/run/expensely-docker.',str(root/'docker.'))
 runner=root/'deploy';runner.write_text(script)
 mocks={'aws':'echo token','docker':'if [[ ${1:-} == login ]]; then cat >/dev/null; elif [[ ${1:-} == inspect ]]; then echo true; fi','curl':'[[ ${FAIL_HEALTH:-0} != 1 ]]','nginx':'exit 0','systemctl':'exit 0','sleep':'exit 0','flock':'exit 0'}
 for name,body in mocks.items():
  p=bins/name;p.write_text('#!/usr/bin/env bash\n'+body+'\n');p.chmod(0o700)
 env=os.environ|{'PATH':str(bins)+':'+os.environ['PATH']}
 image=lambda x:'993371733162.dkr.ecr.us-east-2.amazonaws.com/expensely@sha256:'+x*64
 def run(action,img='',fail=False):
  return subprocess.run(['bash',str(runner),action,img],env=env|{'FAIL_HEALTH':str(int(fail))},capture_output=True,text=True)
 assert run('deploy',image('a')).returncode==0
 assert (state/'active').read_text().strip()=='blue '+image('a')
 envfile.write_text('VALUE=second\n')
 assert run('deploy',image('b')).returncode==0
 before=(state/'active').read_text();target=upstream.read_text()
 assert run('deploy',image('c'),True).returncode!=0
 assert (state/'active').read_text()==before and upstream.read_text()==target
 assert run('rollback').returncode==0
 assert (state/'active').read_text().strip()=='blue '+image('a')
 assert run('deploy','untrusted/image:latest').returncode!=0
 print('PASS: first release, second release, failed health preserves active state, rollback, untrusted-image rejection')
