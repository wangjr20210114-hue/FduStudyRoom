"""Create the coordinator configuration and a separate upload containing only a verifier and public key."""
import base64
import hashlib
import hmac
import json
from pathlib import Path
import secrets
import subprocess
import sys
import tarfile
sys.path.insert(0,str(Path(__file__).resolve().parent))
from join import BASE, read, save

def prepare(directory, previous, owner_file):
    folder=directory/"worker"
    config=read(previous)
    secret=folder/"db.local"
    if not secret.exists():
        save(secret,{"password":secrets.token_urlsafe(40),"salt":secrets.token_hex(16)})
    db=read(secret)
    salt=bytes.fromhex(db["salt"])
    salted=hashlib.pbkdf2_hmac("sha256",db["password"].encode(),salt,16384)
    b64=lambda v:base64.b64encode(v).decode()
    client=hmac.digest(salted,b"Client Key","sha256")
    verifier="SCRAM-SHA-256$16384:"+b64(salt)+"$"+b64(hashlib.sha256(client).digest())+":"+b64(hmac.digest(salted,b"Server Key","sha256"))
    key=folder/"worker_ed25519.pub"
    fingerprint=subprocess.check_output(["ssh-keygen","-lf",str(folder/"known_hosts"),"-E","sha256"],text=True).split()[1]
    save(folder/"forward.json",{"verifier":verifier,"public_key":key.read_text().strip(),"server_fingerprint":fingerprint})
    script=Path(__file__).with_name("connect_worker.py")
    upload=folder/"fdu-team-forward-20261119.tar.gz"
    with tarfile.open(upload,"w:gz") as archive:
        archive.add(script,arcname=script.name,recursive=False)
        archive.add(folder/"forward.json",arcname="forward.json",recursive=False)
    admin=read(owner_file)
    members=read(directory/"profiles.json")["members"]+[{"id":"coordinator","admin":True,"layer":"coordinator","modules":[],"tokenHash":hashlib.sha256(admin["code"].encode()).hexdigest()}]
    history=sorted((set(config.get("approvedBaselines",[]))|{config["baseline"]})-{BASE})
    config.update(baseline=BASE,members=members,approvedBaselines=history,enabled=True,verificationEnabled=True,
                  pressureInfoPath=str(folder/"server-memory.local"),memoryInfoPath="",publicationMode="branch-only")
    save(folder/"worker-config.local",config)
    print(upload,hashlib.sha256(upload.read_bytes()).hexdigest())
if __name__=="__main__":
    prepare(*(Path(p) for p in sys.argv[1:]))
