"""Create server-local receive-only configuration. Never rotate existing secrets."""
from pathlib import Path
import hashlib,json,os,secrets
root=Path(__file__).resolve().parent
config=root/"state/.campus/buffer/config.local"
if config.exists():
    print("Existing configuration preserved.")
    raise SystemExit(0)
manifest=root/"runtime/spec/modules.json"
if not manifest.exists():raise SystemExit("Deployment package incomplete: runtime/spec/modules.json missing")
build=json.loads((root/"build.json").read_text())
if not build.get("source_commit"):raise SystemExit("Deployment package has no source version")
private=root/"private";private.mkdir(mode=0o700,exist_ok=True)
password_file=private/"db_password"
if password_file.exists():password=password_file.read_text().strip()
else:
    password=secrets.token_urlsafe(32);password_file.write_text(password);password_file.chmod(0o600)
config.parent.mkdir(parents=True,exist_ok=True)
profiles=json.loads((root/"runtime/spec/members.example.json").read_text())["members"]
profiles=profiles+[{"id":"coordinator","layer":"coordinator","modules":[],"admin":True}]
credentials={};members=[]
for profile in profiles:
    token=secrets.token_urlsafe(32)
    members.append({**profile,"tokenHash":hashlib.sha256(token.encode()).hexdigest()})
    credentials[profile["id"]]={"identity":profile["id"],"buffer_url":"https://gitbuffer.seatraces.com","token":token}
settings={"root":"/workspace","repository":"/data/repository","pythonExecutable":"python3","baseline":build["source_commit"],"enabled":False,"verificationEnabled":False,"intervalSeconds":259200,"memoryThresholdPercent":85,"pressureCooldownSeconds":300,"memoryInfoPath":"/host/proc/meminfo","allowedOrigins":["https://gitbuffer.seatraces.com","http://127.0.0.1:7780"],"ciImage":"fdu-campus-ci:baseline","remote":"https://gitee.com/wangjr2021/fdu-study-room.git","branch":"main","giteeOwner":"wangjr2021","giteeRepo":"fdu-study-room","giteeApiToken":"","members":members}
config.write_text(json.dumps(settings,ensure_ascii=False,indent=2));config.chmod(0o600)
properties=root/"state/database.properties"
properties.write_text("spring.datasource.url=jdbc:postgresql://fdu-gitbuffer-database:5432/campus_buffer\nspring.datasource.username=campus_buffer\nspring.datasource.password="+password+"\n")
properties.chmod(0o600)
credential_file=private/"member-credentials.local";credential_file.write_text(json.dumps(credentials,ensure_ascii=False,indent=2));credential_file.chmod(0o600)
if hasattr(os,"chown") and os.geteuid()==0:
    for path in [root/"state",root/"state/.campus",config.parent,config,properties]:os.chown(path,10001,10001)
print("Initialized receive-only mode. Member access codes: private/member-credentials.local")
print("CI and Gitee publication remain disabled until an approved baseline, trusted worker, repository and credentials are configured.")
