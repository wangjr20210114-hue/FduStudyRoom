"""Run once on the existing receive-only Ubuntu instance using the packaged files."""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

ROOT = Path("/opt/fdu-gitbuffer-preview-20261113")
SOURCE = Path(__file__).resolve().parent
DEST = ROOT / "team-releases/20261119"
BASE = "2142af7a714377764fa32118fa99a92a8551a4d4"
OLD = "e05eb9ad234e9ce16f5605b22c0247356749b9d8"
config = ROOT/"state/.campus/buffer/config.local"
override = ROOT/"compose.override.yaml"


def cmd(*args, capture=False):
    return subprocess.run(args, check=True,capture_output=capture, cwd=ROOT)


def atomic(path, data):
    with tempfile.NamedTemporaryFile(dir=path.parent,delete=False) as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
        if path.exists():
            st=path.stat()
            os.fchown(f.fileno(),st.st_uid,st.st_gid)
            os.fchmod(f.fileno(),st.st_mode & 0o777)
    os.replace(f.name,path)


def me_api(path):
    credential=json.loads((ROOT/"private/member-credentials.local").read_text())["coordinator"]["token"]
    req=urllib.request.Request("http://127.0.0.1:7780"+path,headers={"Authorization":"Bearer "+credential})
    with urllib.request.urlopen(req,timeout=20) as response:
        return json.load(response)


def main():
    if os.geteuid()!=0:
        sys.exit("Use sudo for the existing buffer installation.")
    files=("app.jar","modules.json","requirements.json","profiles.json")
    expected=json.loads((SOURCE/"checksums.json").read_text())
    for name in files:
        if hashlib.sha256((SOURCE/name).read_bytes()).hexdigest()!=expected[name]:
            raise ValueError("Incorrect package digest: "+name)
    profiles=json.loads((SOURCE/"profiles.json").read_text())
    assert profiles["baseline"]==BASE
    assert [(p["id"],p["layer"],p["modules"]) for p in profiles["members"]] == [(s,"fullstack",[f"M0{i}"]) for i,s in enumerate("abcdef",1)]
    before=config.read_bytes()
    data=json.loads(before)
    admin=[m for m in data["members"] if m.get("admin")]
    old=[m for m in data["members"] if not m.get("admin")]
    if data["baseline"]==BASE and old==profiles["members"] and me_api("/api/me")["baseline"]==BASE:
        me_api("/api/team/github")
        print("TEAM_BUFFER_READY")
        return
    assert data["baseline"]==OLD and len(admin)==1 and admin[0]["id"]=="coordinator"
    assert all(p.get("example") for p in old) and {p["id"] for p in old}=={"backend-a","backend-b","frontend-a","frontend-b"}
    assert data.get("enabled") is False and data.get("verificationEnabled") is False
    assert not me_api("/api/submissions"),"Existing submissions require a coordinated migration."
    original=override.read_bytes()
    marker, body=original.decode().split("\n",1)
    assert marker=="# Managed by FduStudyRoom buffer web updater"
    patch=json.loads(body)
    mounts=patch["services"]["api"]["volumes"]
    assert [v["target"] for v in mounts]==["/workspace/tools/buffer/frontend/dist"]
    active=cmd("docker","inspect","fdu-gitbuffer-api-1","--format",'{{index .Config.Labels "com.docker.compose.project.config_files"}}',capture=True).stdout.decode().strip().split(",")
    assert list(map(Path,active))==[ROOT/"compose.yaml",override]
    DEST.mkdir(parents=True,exist_ok=True)
    backup=ROOT/"private"/("before-team-"+str(time.time_ns()))
    backup.mkdir(mode=0o700)
    (backup/"config.local").write_bytes(before)
    (backup/"compose.override.yaml").write_bytes(original)
    with (backup/"buffer.dump").open("wb") as db:
        subprocess.run(["docker","compose","exec","-T","database","pg_dump","-Fc","-U","campus_buffer","campus_buffer"],cwd=ROOT,stdout=db,check=True)
    for name,target in (("app.jar","/opt/buffer/app.jar"),("modules.json","/workspace/spec/modules.json"),("requirements.json","/workspace/spec/requirements.json")):
        dst=DEST/name
        shutil.copyfile(SOURCE/name,dst)
        dst.chmod(0o644)
        mounts.append({"type":"bind","source":str(dst),"target":target,"read_only":True})
    data.update(baseline=BASE,members=profiles["members"]+admin)
    docker=["docker","compose","up","-d","--no-deps","--no-build","--wait","--wait-timeout","120","api"]
    try:
        atomic(config,json.dumps(data).encode())
        atomic(override,(marker+"\n"+json.dumps(patch,indent=2)+"\n").encode())
        cmd("docker","compose","config","--quiet")
        cmd(*docker)
        assert me_api("/api/me")["baseline"]==BASE and not me_api("/api/team/github")
    except BaseException:
        atomic(config,before)
        atomic(override,original)
        cmd(*docker)
        raise
    print("TEAM_BUFFER_READY backup="+str(backup))


if __name__=="__main__":
    main()
