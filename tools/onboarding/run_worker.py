"""Maintain the loopback-only database tunnel and trusted Java worker for this Windows session."""
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parent))
from join import buffer, read, save

BASE=Path(__file__).resolve().parent
PORT=25535

def ssh_arguments():
    return ["ssh","-F","none","-N","-T","-i",str(BASE/"worker_ed25519"),"-o","IdentitiesOnly=yes",
            "-o","BatchMode=yes","-o","PasswordAuthentication=no","-o","ExitOnForwardFailure=yes",
            "-o","StrictHostKeyChecking=yes","-o","HostKeyAlgorithms=ssh-ed25519",
            "-o","UserKnownHostsFile="+str(BASE/"known_hosts"),"-o","ConnectTimeout=10",
            "-o","ServerAliveInterval=20","-o","ServerAliveCountMax=3",
            "-L",f"127.0.0.1:{PORT}:127.0.0.1:25534","fdu-buffer-forward@124.223.110.193"]

def update_memory():
    owner=read(BASE.parent/"invitations-owner.local")
    snap=buffer("/api/status",owner["code"])
    mem=snap["memory"]
    if mem["known"] and 0<=mem["availableBytes"]<=mem["totalBytes"]:
        tmp=BASE/"server-memory.tmp"
        tmp.write_text(f"MemTotal: {mem['totalBytes']//1024} kB\nMemAvailable: {mem['availableBytes']//1024} kB\n",encoding="ascii")
        os.replace(tmp,BASE/"server-memory.local")

def main():
    env=os.environ.copy()
    env.update(CAMPUS_BUFFER_CONFIG=str(BASE/"worker-config.local"),CAMPUS_BUFFER_DB_URL=f"jdbc:postgresql://127.0.0.1:{PORT}/campus_buffer",
               CAMPUS_BUFFER_DB_USER="fdu_buffer_worker",CAMPUS_BUFFER_DB_PASSWORD=read(BASE/"db.local")["password"],
               SPRING_FLYWAY_ENABLED="false",SPRING_MAIN_WEB_APPLICATION_TYPE="none",PYTHONUTF8="1")
    java=read(BASE/"java.local")["java"]
    flags={"creationflags":subprocess.CREATE_NO_WINDOW} if os.name=="nt" else {}
    with (BASE/"worker.log").open("wb") as log:
        tunnel=subprocess.Popen(ssh_arguments(),stdout=log,stderr=log,**flags)
        app=None
        try:
            for _ in range(30):
                if tunnel.poll() is not None:raise RuntimeError("SSH forward did not start.")
                try:
                    with socket.create_connection(("127.0.0.1",PORT),timeout=1):break
                except OSError:time.sleep(1)
            else:raise RuntimeError("Loopback connection timeout.")
            update_memory()
            app=subprocess.Popen([java,"-Xmx256m","-jar",str(BASE/"worker.jar")],cwd=BASE,env=env,stdout=log,stderr=log,**flags)
            while app.poll() is None and tunnel.poll() is None:
                try:
                    update_memory()
                except OSError:
                    pass
                time.sleep(25)
            raise RuntimeError("Worker connection closed; the scheduled task will retry.")
        finally:
            for process in (app,tunnel):
                if process and process.poll() is None:
                    process.terminate()
                    try:process.wait(timeout=10)
                    except subprocess.TimeoutExpired:process.kill()
if __name__=="__main__":
    main()
