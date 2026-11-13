"""Append the gitbuffer site to the active, bind-mounted Caddyfile with rollback."""
from pathlib import Path
import json,subprocess,socket,shutil,os,sys,datetime
root=Path(__file__).resolve().parent
container="docker-caddy-1";domain="gitbuffer.seatraces.com";expected="124.223.110.193"
def command(args,body=None):
    result=subprocess.run(args,input=body,capture_output=True,timeout=45)
    if result.returncode:
        private=root/"private";private.mkdir(mode=0o700,exist_ok=True)
        log=private/"caddy-operation.log";log.write_bytes(result.stdout+result.stderr);log.chmod(0o600)
        raise RuntimeError("Caddy operation failed; details saved in private/caddy-operation.log")
    return result.stdout
try:
    addresses={x[4][0] for x in socket.getaddrinfo(domain,443,socket.AF_INET)}
    if addresses!={expected}:raise RuntimeError("DNS must resolve gitbuffer.seatraces.com only to 124.223.110.193 before connecting Caddy")
    cid=command(["docker","compose","ps","-q","api"]).decode().strip()
    if not cid:raise RuntimeError("Buffer API is not running; start this package first")
    args=json.loads(command(["docker","inspect",container,"--format","{{json .Config.Cmd}}"]));args=args or []
    if "--resume" in args or any(x in args for x in ["sh","bash"]):raise RuntimeError("Caddy uses an unsupported startup mode; inspect its active configuration manually")
    active=None
    for i,value in enumerate(args):
        if value in ("--config","-c") and i+1<len(args):active=args[i+1]
        elif value.startswith("--config="):active=value.split("=",1)[1]
    if active not in ("/etc/caddy/Caddyfile","/etc/caddy/Caddyfile.iptest"):raise RuntimeError("Expected an explicit Caddyfile path in the Caddy startup command")
    mounts=json.loads(command(["docker","inspect",container,"--format","{{json .Mounts}}"]));mount=next((m for m in mounts if m["Destination"]==active and m["Type"]=="bind"),None)
    if mount is None:raise RuntimeError("Active Caddyfile is not a bind mount")
    path=Path(mount["Source"]).resolve();allowed=Path("/opt/shiguangji/infra/docker").resolve()
    if path.parent!=allowed or path.name not in ("Caddyfile","Caddyfile.iptest"):raise RuntimeError("Unexpected host Caddyfile path; no changes made")
    original=path.read_bytes()
    loaded=json.loads(command(["docker","exec","-w","/etc/caddy",container,"caddy","adapt","--config",active,"--adapter","caddyfile"]))
    live=json.loads(command(["docker","exec",container,"wget","-qO-","http://127.0.0.1:2019/config/"]))
    if loaded!=live:raise RuntimeError("Running Caddy configuration differs from the startup file; no changes made. Review the active configuration first")
    if domain.encode() in original:
        print("gitbuffer.seatraces.com is already present. Existing configuration preserved.")
        raise SystemExit(0)
    candidate=original+b"\n\n"+(root/"Caddyfile.site").read_bytes()
    # The original directory is required for relative imports in stdin configuration.
    command(["docker","exec","-i","-w","/etc/caddy",container,"caddy","adapt","--config","-","--adapter","caddyfile","--validate"],candidate)
    private=root/"private";private.mkdir(mode=0o700,exist_ok=True)
    backup=private/("caddy-backup-"+datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    shutil.copyfile(path,backup);backup.chmod(0o600)
    if path.read_bytes()!=original:raise RuntimeError("Caddyfile changed during preparation; retry after reviewing it")
    def write_in_place(data):
        # Preserve the inode used by the existing bind mount.
        with path.open("wb") as f:f.write(data);f.flush();os.fsync(f.fileno())
    write_in_place(candidate)
    try:command(["docker","exec",container,"caddy","reload","--config",active,"--adapter","caddyfile"])
    except Exception:
        write_in_place(original)
        raise RuntimeError("Reload failed; the original Caddyfile was restored. Inspect private/caddy-operation.log")
    print("Added https://gitbuffer.seatraces.com. Original configuration backup: "+str(backup))
    print("Verify: curl -fsS https://gitbuffer.seatraces.com/health")
except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired) as error:
    print(str(error),file=sys.stderr);raise SystemExit(1)
