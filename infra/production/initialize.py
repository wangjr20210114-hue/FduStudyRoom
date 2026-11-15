#!/usr/bin/env python3
"""Provision a dedicated restricted SSH receiver for the authorized experience site."""
import argparse,os,pwd,re,secrets,shutil,subprocess
from pathlib import Path

def initialize(source,public_key,images):
    if os.geteuid()!=0:raise SystemExit('Run provisioning as root')
    root=Path('/opt/fdu-study-room');source=Path(source)
    try:account=pwd.getpwnam('fdu-deploy')
    except KeyError:
        subprocess.run(['useradd','--create-home','--shell','/bin/bash','--groups','docker','fdu-deploy'],check=True)
        account=pwd.getpwnam('fdu-deploy')
    for folder in ['bin','config','private','private/backups','releases']:
        target=root/folder;target.mkdir(parents=True,exist_ok=True)
        if folder in ['private','private/backups','releases']:os.chown(target,account.pw_uid,account.pw_gid)
    (root/'private').chmod(0o700)
    for name in ['compose.yaml','Caddyfile','initialize-database.sh']:shutil.copyfile(source/name,root/'config'/name)
    shutil.copyfile(source/'deploy.py',root/'bin/receive.py');(root/'bin/receive.py').chmod(0o644)
    config=root/'private/runtime.env'
    if not config.exists():
        data={key:secrets.token_hex(32) for key in ['POSTGRES_PASSWORD','RESOURCE_DB_PASSWORD','WORKFLOW_DB_PASSWORD','AUTH_SIGNING_KEY','AUTH_SERVICE_KEY']}
        data.update({'SITE_ADDRESS':'studyroom.seatraces.com','PUBLIC_BIND':'0.0.0.0',**images})
        config.write_text(''.join(k+'='+v+'\n' for k,v in data.items()),encoding='utf-8')
        config.chmod(0o600);os.chown(config,account.pw_uid,account.pw_gid)
    key=Path(public_key).read_text(encoding='utf-8').strip()
    if not re.fullmatch(r'ssh-ed25519 [A-Za-z0-9+/=]+(?: [A-Za-z0-9_.-]+)?',key):raise ValueError('Invalid public key')
    home=Path(account.pw_dir);os.chown(home,0,0);home.chmod(0o755)
    folder=home/'.ssh';folder.mkdir(mode=0o700,exist_ok=True)
    auth=folder/'authorized_keys';old=auth.read_text() if auth.exists() else ''
    line='restrict,command="/usr/bin/python3 /opt/fdu-study-room/bin/receive.py" '+key
    if key.split()[1] not in old:
        with auth.open('a') as output:output.write(('\n' if old and not old.endswith('\n') else '')+line+'\n')
    # The deployment account cannot replace its forced-command SSH entry or server executable.
    os.chown(folder,0,account.pw_gid);folder.chmod(0o750)
    os.chown(auth,0,account.pw_gid);auth.chmod(0o640)
    print('Dedicated receiver configured; credentials retained only in private server configuration.')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--public-key',required=True);p.add_argument('--java-image',required=True);p.add_argument('--postgres-image',required=True);p.add_argument('--caddy-image',required=True);a=p.parse_args()
    images={'JAVA_IMAGE':a.java_image,'POSTGRES_IMAGE':a.postgres_image,'CADDY_IMAGE':a.caddy_image}
    if not all(re.fullmatch(r'sha256:[0-9a-f]{64}',v) for v in images.values()):raise ValueError('Pinned local image IDs required')
    initialize(Path(__file__).parent,a.public_key,images)
