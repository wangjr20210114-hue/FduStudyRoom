"""Exercise packaged checkouts, actual Flyway migrations, and both layer views in a disposable buffer."""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

sys.path.insert(0,str(Path(__file__).resolve().parent))
import join

def verify(root,work,dist):
    cred=join.read(work/"slots.local")
    profiles=join.read(work/"profiles.json")["members"]
    admin=secrets.token_urlsafe(32)
    profiles.append({"id":"coordinator","admin":True,"modules":[],"layer":"coordinator","tokenHash":hashlib.sha256(admin.encode()).hexdigest()})
    dc=["wsl","-d","CampusStudyRoom","--exec","docker"] if os.name=="nt" and str(root).startswith("\\\\wsl") else ["docker"]
    container="fdu-onboarding-test-"+secrets.token_hex(4)
    pw=secrets.token_urlsafe(16)
    output=lambda args: subprocess.check_output(args,text=True,encoding="utf-8",errors="replace").strip()
    pg="postgres@sha256:bbf83594798e144bff365a9c191c5d779f181fb3135902064ab8ff11918020fc"
    output(dc+["run","--rm","-d","--name",container,"-p","127.0.0.1::5432","-e","POSTGRES_DB=campus_buffer_test","-e","POSTGRES_PASSWORD="+pw,pg])
    java=None
    try:
        port=output(dc+["port",container,"5432/tcp"]).split(":")[-1]
        with socket.socket() as s:
            s.bind(("127.0.0.1",0))
            web=s.getsockname()[1]
        with tempfile.TemporaryDirectory(dir=work) as tmp:
            tmp=Path(tmp)
            shutil.copytree(root/"spec",tmp/"spec")
            cfg=tmp/"config.local"
            join.save(cfg,{"root":str(tmp),"baseline":join.BASE,"members":profiles,"enabled":False,"verificationEnabled":False})
            env=dict(os.environ,CAMPUS_BUFFER_CONFIG=str(cfg),SERVER_PORT=str(web),
                     CAMPUS_BUFFER_DB_URL=f"jdbc:postgresql://127.0.0.1:{port}/campus_buffer_test",CAMPUS_BUFFER_DB_USER="postgres",CAMPUS_BUFFER_DB_PASSWORD=pw)
            env.update(CAMPUS_BUFFER_TEST_DB_URL=env["CAMPUS_BUFFER_DB_URL"],CAMPUS_BUFFER_TEST_DB_USER="postgres",CAMPUS_BUFFER_TEST_DB_PASSWORD=pw)
            mvn=next((Path.home()/".m2/wrapper").rglob("mvn.cmd")) if os.name=="nt" else "mvn"
            subprocess.run([str(mvn),"-B","-o","-f","tools/buffer/backend/pom.xml","test"],cwd=work/"build-src",env=env,stdout=(work/"tests.log").open("wb"),stderr=subprocess.STDOUT,check=True)
            jar=next((work/"build-src/tools/buffer/backend/target").glob("*.jar"))
            executable=Path(os.environ["JAVA_HOME"])/"bin"/("java.exe" if os.name=="nt" else "java")
            with (work/"test-buffer.log").open("wb") as log:
                java=subprocess.Popen([str(executable),"-jar",str(jar)],env=env,cwd=work,stdout=log,stderr=log)
            join.SITE=f"http://127.0.0.1:{web}"
            for i in range(50):
                try:
                    with urllib.request.urlopen(join.SITE+"/health",timeout=2) as r:
                        if json.load(r)["status"]=="UP": break
                except OSError:
                    if java.poll() is not None: raise ValueError("Test server stopped; see test-buffer.log")
                    time.sleep(1)
            for index,(slot,mod) in enumerate(join.SLOTS.items(),1):
                with zipfile.ZipFile(dist/f"FduStudyRoom-{slot}-{mod}.zip") as z:
                    folder=tmp/("kit-"+slot)
                    z.extractall(folder)
                    join.KIT=folder
                    repo=join.checkout(tmp/slot)
                    assert join.checkout(repo)==repo
                    args=[sys.executable,"scripts/team.py","install-skills","--skills-dir",str(tmp/(slot+"-skills"))]
                    subprocess.run(args,cwd=repo,check=True,stdout=subprocess.DEVNULL)
                    subprocess.run(args+["--check"],cwd=repo,check=True,stdout=subprocess.DEVNULL)
                    token=join.read(folder/"member.json")["access_code"]
                    join.save(repo/".campus/member.local",dict(identity=slot,buffer_url=join.SITE,token=token))
                    for layer in ("backend","frontend"):
                        join.profile(slot,token,layer)
                        result=json.loads(output([sys.executable,str(repo/"scripts/team.py"),"workflow",slot,"--module",mod,"--layer",layer,"--preview"]))
                        assert result["requirements"] and {row["module"] for row in result["requirements"]}=={mod}
                    body={"id":index,"login":"example-"+slot}
                    for _ in range(2):join.buffer("/api/team/github",token,"backend",body)
            for token,body,status in [(cred["b"],{"id":1,"login":"example-a"},409),(cred["a"],{"id":77,"login":"other"},409),(admin,{"id":1,"login":"owner"},403)]:
                try:join.buffer("/api/team/github",token,None if token==admin else "backend",body)
                except urllib.error.HTTPError as e:assert e.code==status,(e.code,status)
                else:raise AssertionError("unexpected enrollment")
            try:join.buffer("/api/team/github",cred["a"],"backend")
            except urllib.error.HTTPError as e:assert e.code==403
            else:raise AssertionError("member got roster")
            assert len(join.buffer("/api/team/github",admin))==6
            print("PASS: 6 replayable clean clones, 6 skill installations, 12 scoped previews, enrollment replay/unique slots/unique accounts/admin and roster checks.")
            java.terminate();java.wait(timeout=20);java=None
    finally:
        if java is not None:
            java.terminate();java.wait(timeout=20)
        subprocess.run(dc+["stop",container],check=True,stdout=subprocess.DEVNULL)

if __name__=="__main__":
    verify(Path(__file__).resolve().parents[2],Path(sys.argv[1]),Path(sys.argv[2]))
