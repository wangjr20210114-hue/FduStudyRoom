"""Install the worker-aware GUI over the registered six-slot buffer, keeping previous mounts."""
import json
import os
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

root=Path("/opt/fdu-gitbuffer-preview-20261113")
src=Path(__file__).resolve().parent
stage=root/"team-releases/worker-dashboard-20261119"
override=root/"compose.override.yaml"
data=json.loads((root/"state/.campus/buffer/config.local").read_text())
assert data["baseline"]=="2142af7a714377764fa32118fa99a92a8551a4d4" and os.geteuid()==0
before=override.read_bytes()
marker,raw=before.decode().split("\n",1)
assert marker=="# Managed by FduStudyRoom buffer web updater"
obj=json.loads(raw)
mounts={m["target"]:m for m in obj["services"]["api"]["volumes"]}
assert all(m["read_only"] for m in mounts.values())
stage.mkdir(parents=True,exist_ok=True)
shutil.copy2(src/"app.jar",stage/"app.jar")
shutil.copytree(src/"web",stage/"web",dirs_exist_ok=True)
(stage/"app.jar").chmod(0o644)
for p in [stage/"web",*(stage/"web").rglob("*")]:p.chmod(0o755 if p.is_dir() else 0o644)
mounts["/opt/buffer/app.jar"]["source"]=str(stage/"app.jar")
mounts["/workspace/tools/buffer/frontend/dist"]["source"]=str(stage/"web")
backup=root/"private"/("compose-before-dashboard-"+str(time.time_ns()))
backup.write_bytes(before)
backup.chmod(0o600)
def write(content):
    temp=override.with_suffix(".onboarding-new")
    temp.write_bytes(content)
    os.replace(temp,override)
def restart():
    subprocess.run(["docker","compose","up","-d","--no-deps","--no-build","--force-recreate","--wait","--wait-timeout","120","api"],cwd=root,check=True)
try:
    write((marker+"\n"+json.dumps(obj,indent=2)+"\n").encode())
    restart()
    content=urllib.request.urlopen("http://127.0.0.1:7780/",timeout=10).read()
    assert content==(stage/"web/index.html").read_bytes()
    owner=json.loads((root/"private/member-credentials.local").read_text())["coordinator"]["token"]
    q=urllib.request.Request("http://127.0.0.1:7780/api/status",headers={"Authorization":"Bearer "+owner})
    assert "remoteWorkerConnected" in json.load(urllib.request.urlopen(q,timeout=10))
except BaseException:
    write(before)
    restart()
    raise
print("TEAM_DASHBOARD_READY")
