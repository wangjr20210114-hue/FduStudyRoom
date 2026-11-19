"""Export the two team evidence tables, then import into an empty project inbox atomically."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

TABLES=("submissions","events")
def digest(table):
    return f"SELECT md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY id),'')) FROM buffer.{table} t"

def psql(prefix,role,db,sql):
    return subprocess.check_output([*prefix,"psql","-XqAt","-v","ON_ERROR_STOP=1","-U",role,"-d",db],
                                   input=sql.encode()).decode().strip()

def export(folder):
    prefix=["wsl","-d","CampusStudyRoom","--exec","docker","exec","-i","fdu-codex-round2-273201-postgres-1"]
    get=lambda q:psql(prefix,"pilot","pilot","SET TIME ZONE 'UTC';\n"+q)
    before={t:get(digest(t)) for t in TABLES}
    body=subprocess.check_output([*prefix,"pg_dump","-U","pilot","-d","pilot","--data-only","--no-owner","--no-privileges",
                                  "--table=buffer.submissions","--table=buffer.events"])
    assert before=={t:get(digest(t)) for t in TABLES}
    gzipfile=folder/"history.sql.gz"
    gzipfile.write_bytes(gzip.compress(body))
    result={"sha256":hashlib.sha256(body).hexdigest(),"tables":before}
    (folder/"history.json").write_text(json.dumps(result))
    print({table:get("SELECT count(*) FROM buffer."+table) for table in TABLES})

def restore(folder):
    prefix=["docker","exec","-i","fdu-gitbuffer-database-1"]
    run=lambda sql:psql(prefix,"campus_buffer","campus_buffer",sql)
    meta=json.loads((folder/"history.json").read_text())
    sql=gzip.decompress((folder/"history.sql.gz").read_bytes())
    assert hashlib.sha256(sql).hexdigest()==meta["sha256"] and set(meta["tables"])==set(TABLES)
    current=lambda:{t:run("SET TIME ZONE 'UTC';\n"+digest(t)) for t in TABLES}
    if current()==meta["tables"]:
        print("History already matches.")
        return
    check_empty="DO $$ BEGIN IF EXISTS (SELECT FROM buffer.submissions) OR EXISTS (SELECT FROM buffer.events) THEN RAISE EXCEPTION 'Inbox is nonempty; manual reconciliation required'; END IF; END $$;"
    checks=[]
    for t in TABLES:
        d=meta["tables"][t]
        assert len(d)==32 and all(x in "0123456789abcdef" for x in d)
        checks.append("DO $$ BEGIN IF ("+digest(t)+") <> '"+d+"' THEN RAISE EXCEPTION 'History differs'; END IF; END $$;")
    run("BEGIN;\nLOCK TABLE buffer.submissions, buffer.events IN ACCESS EXCLUSIVE MODE;\n"+check_empty+"\n"+sql.decode()+
        "\nSET TIME ZONE 'UTC';\n"+"\n".join(checks)+"\nSELECT setval(pg_get_serial_sequence('buffer.events','id'),coalesce(max(id),1),count(*)>0) FROM buffer.events;\nCOMMIT;\n")
    assert current()==meta["tables"]
    print("TEAM_HISTORY_RESTORED")

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("action",choices=["export","restore"])
    parser.add_argument("folder",type=Path)
    args=parser.parse_args()
    (export if args.action=="export" else restore)(args.folder)
