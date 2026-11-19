"""Provision only the private forward to this project's PostgreSQL database."""
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import tempfile

ROOT=Path("/opt/fdu-gitbuffer-preview-20261113")
USER="fdu-buffer-forward"
DB_ROLE="fdu_buffer_worker"
PORT=25534
SRC=Path(__file__).resolve().parent

def run(args,**kw):
    return subprocess.run(args,check=True,cwd=ROOT,**kw)
def install(path,data,mode):
    with tempfile.NamedTemporaryFile(dir=path.parent,delete=False) as f:
        f.write(data)
        os.fchmod(f.fileno(),mode)
    os.replace(f.name,path)
def main():
    assert os.geteuid()==0
    data=json.loads((SRC/"forward.json").read_text())
    fingerprint=subprocess.check_output(["ssh-keygen","-lf","/etc/ssh/ssh_host_ed25519_key.pub","-E","sha256"],text=True).split()[1]
    assert fingerprint==data["server_fingerprint"],"Existing server host key differs"
    key=data["public_key"].strip()
    assert re.fullmatch(r"ssh-ed25519 [A-Za-z0-9+/=]+ fdu-buffer-forward-20261119",key)
    verifier=data["verifier"]
    assert re.fullmatch(r"SCRAM-SHA-256\$[0-9]+:[a-zA-Z0-9+/=]+\$[a-zA-Z0-9+/=]+:[a-zA-Z0-9+/=]+",verifier)
    cfg=json.loads((ROOT/"state/.campus/buffer/config.local").read_text())
    assert cfg["baseline"]=="2142af7a714377764fa32118fa99a92a8551a4d4"
    override=ROOT/"compose.override.yaml"
    old=override.read_bytes()
    marker,body=old.decode().split("\n",1)
    assert marker=="# Managed by FduStudyRoom buffer web updater"
    obj=json.loads(body)
    # The database only joins an internal Docker network. A dedicated relay
    # publishes one host-loopback socket without adding an egress network to PostgreSQL.
    prior=obj["services"].get("database")
    assert prior is None or prior=={"ports":["127.0.0.1:25534:5432"]}
    if prior is not None:
        del obj["services"]["database"]
    relay="""import select,socket,socketserver
class Bridge(socketserver.BaseRequestHandler):
 def handle(self):
  with socket.create_connection(('fdu-gitbuffer-database',5432),timeout=10) as db:
   db.settimeout(None)
   peers={db:self.request,self.request:db}
   while peers:
    for src in select.select(list(peers),[],[])[0]:
     data=src.recv(65536)
     if data: peers[src].sendall(data)
     else:
      peers.pop(src).shutdown(socket.SHUT_WR)
class Server(socketserver.ThreadingTCPServer):
 allow_reuse_address=True
 daemon_threads=True
Server(('0.0.0.0',25534),Bridge).serve_forever()
"""
    forward={"image":"fdu-gitbuffer:local","entrypoint":["python3","-c",relay],"restart":"unless-stopped",
             "read_only":True,"mem_limit":"48m","pids_limit":32,"cap_drop":["ALL"],
             "security_opt":["no-new-privileges:true"],"ports":["127.0.0.1:25534:25534"],"networks":["database","worker_ingress"]}
    assert obj["services"].get("worker-forward",forward)==forward
    obj["services"]["worker-forward"]=forward
    obj.setdefault("networks",{})["worker_ingress"]={}
    backup=ROOT/"private/compose-before-worker-relay-20261119.yaml"
    if not backup.exists():backup.open("xb").write(old)
    install(override,(marker+"\n"+json.dumps(obj,indent=2)+"\n").encode(),0o644)
    try:
        run(["docker","compose","config","--quiet"])
        run(["docker","compose","up","-d","--no-deps","--wait","--wait-timeout","90","database","worker-forward"])
        endpoint=subprocess.check_output(["docker","compose","port","worker-forward","25534"],cwd=ROOT,text=True).strip()
        assert endpoint=="127.0.0.1:25534",endpoint
    except BaseException:
        install(override,old,0o644)
        run(["docker","compose","up","-d","--no-deps","--wait","database"])
        raise

    # The worker may advance reviewed records. Reviewer, evidence and patch columns remain read-only.
    psql=["docker","exec","-i","fdu-gitbuffer-database-1","psql","-Xq","-v","ON_ERROR_STOP=1","-U","campus_buffer","-d","campus_buffer"]
    sql=f"""BEGIN;
DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='{DB_ROLE}') THEN CREATE ROLE {DB_ROLE} LOGIN; END IF; END $$;
ALTER ROLE {DB_ROLE} NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS CONNECTION LIMIT 6 PASSWORD '{verifier}';
GRANT CONNECT ON DATABASE campus_buffer TO {DB_ROLE};
GRANT USAGE ON SCHEMA buffer TO {DB_ROLE};
GRANT SELECT ON buffer.submissions,buffer.sync_state TO {DB_ROLE};
GRANT UPDATE(state,result,updated_at) ON buffer.submissions TO {DB_ROLE};
GRANT UPDATE ON buffer.sync_state TO {DB_ROLE};
GRANT INSERT ON buffer.events TO {DB_ROLE};
GRANT USAGE ON SEQUENCE buffer.events_id_seq TO {DB_ROLE};
CREATE OR REPLACE FUNCTION buffer.require_review_for_worker() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF current_user='{DB_ROLE}' AND (OLD.reviewer IS NULL OR OLD.reviewer=OLD.member_id OR NOT (
   (OLD.state IN ('REVIEWED','VERIFY_FAILED') AND NEW.state='VERIFYING') OR
   (OLD.state='VERIFYING' AND NEW.state IN ('REVIEWED','VERIFIED','VERIFY_FAILED','CONFLICT')) OR
   (OLD.state IN ('VERIFIED','PUSH_FAILED') AND NEW.state IN ('PUBLISHED','PUSH_FAILED'))))
 THEN RAISE EXCEPTION 'worker must follow reviewed state transitions'; END IF;
 RETURN NEW;
END $$;
CREATE OR REPLACE TRIGGER worker_review_gate BEFORE UPDATE ON buffer.submissions FOR EACH ROW EXECUTE FUNCTION buffer.require_review_for_worker();
COMMIT;"""
    run(psql,input=sql.encode(),stdout=subprocess.DEVNULL)
    account_home=Path("/var/lib/fdu-buffer-forward")
    try: account=pwd.getpwnam(USER)
    except KeyError:
        run(["useradd","--system","--create-home","--home-dir",str(account_home),"--shell","/usr/sbin/nologin",USER])
        account=pwd.getpwnam(USER)
    assert Path(account.pw_dir)==account_home and account.pw_shell=="/usr/sbin/nologin"
    sshdir=account_home/".ssh"
    sshdir.mkdir(exist_ok=True,mode=0o700)
    auth=sshdir/"authorized_keys"
    entry=f'restrict,port-forwarding,permitopen="127.0.0.1:{PORT}" {key}\n'
    assert not auth.exists() or auth.read_text()==entry
    snippet=Path("/etc/ssh/sshd_config.d/75-fdu-buffer-forward.conf")
    policy=f"""Match User {USER}
    AuthenticationMethods publickey
    AllowTcpForwarding local
    PermitOpen 127.0.0.1:{PORT}
    PermitListen none
    AllowStreamLocalForwarding no
    AllowAgentForwarding no
    X11Forwarding no
    PermitTTY no
    MaxSessions 0
Match all
"""
    assert not snippet.exists() or snippet.read_text()==policy
    created=not snippet.exists()
    install(snippet,policy.encode(),0o644)
    try:
        run(["/usr/sbin/sshd","-t"])
        eff=subprocess.check_output(["/usr/sbin/sshd","-T","-C","user="+USER+",host=localhost,addr=127.0.0.1"],text=True).splitlines()
        assert all(v in eff for v in ("allowtcpforwarding local","permitopen 127.0.0.1:25534","maxsessions 0","permitlisten none","allowstreamlocalforwarding no"))
        run(["systemctl","reload","ssh"])
    except BaseException:
        if created:snippet.unlink()
        raise
    install(auth,entry.encode(),0o600)
    for path in (account_home,sshdir,auth):os.chown(path,account.pw_uid,account.pw_gid)
    print("TEAM_FORWARD_READY "+fingerprint)

if __name__=="__main__":
    main()
