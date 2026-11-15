"""Resolve authentic scenario scopes without changing formal requirement state."""
import hashlib
import json
import re
from pathlib import Path
from policy import PolicyError, load, module_for


def _digest(body):
    return hashlib.sha256(body).hexdigest()


def resolve_pilot(root, member, module, scenario_id, requirement=None):
    root = Path(root).resolve()
    owned = module_for(load(root), module)
    layer = member.get("layer")
    if owned["id"] not in member.get("modules", []) or layer not in owned["write_paths"]:
        raise PolicyError("PILOT_MODULE_OR_LAYER_NOT_ASSIGNED")
    if not isinstance(scenario_id, str) or not re.fullmatch(r"S[0-9]{3}\.SC[0-9]{2}", scenario_id):
        raise PolicyError("PILOT_SCENARIO_ID_INVALID")
    network_path = root / "spec/spec-network.json"
    catalog_path = root / "spec/requirements.json"
    network_body, catalog_body = network_path.read_bytes(), catalog_path.read_bytes()
    network, catalog = json.loads(network_body), json.loads(catalog_body)
    matches = [(spec, scene) for spec in network["specifications"] for scene in spec.get("scenarios", []) if scene.get("id") == scenario_id]
    if len(matches) != 1:
        raise PolicyError("PILOT_SCENARIO_NOT_FOUND_OR_AMBIGUOUS")
    specification, scenario = matches[0]
    parents = {ref.rsplit(".AC", 1)[0] for ref in scenario.get("acceptance_refs", [])}
    if not parents or not parents.issubset(set(specification.get("requirements", []))):
        raise PolicyError("PILOT_SCENARIO_PARENT_INVALID")
    if requirement is None:
        if len(parents) != 1:
            raise PolicyError("PILOT_PARENT_REQUIREMENT_REQUIRED")
        requirement = next(iter(parents))
    rows = [row for row in catalog["requirements"] if row["id"] == requirement]
    if requirement not in parents or len(rows) != 1:
        raise PolicyError("PILOT_PARENT_REQUIREMENT_MISMATCH")
    parent = rows[0]
    if specification.get("module") != owned["id"] or parent.get("module") != owned["id"] or layer not in parent.get("layers", []):
        raise PolicyError("PILOT_REQUIREMENT_SCOPE_MISMATCH")
    if parent.get("execution_owner", "member") != "member":
        raise PolicyError("PILOT_REQUIRES_MEMBER_BUSINESS_REQUIREMENT")
    scene_path = Path(specification["path"])
    source_file = root / scene_path
    if scene_path.is_absolute() or ".." in scene_path.parts or not source_file.resolve().is_relative_to(root / "spec/specs"):
        raise PolicyError("PILOT_SOURCE_PATH_ESCAPE")
    source = {
        "network_path": "spec/spec-network.json", "network_sha256": _digest(network_body),
        "catalog_path": "spec/requirements.json", "catalog_sha256": _digest(catalog_body),
        "scenario_path": scene_path.as_posix(), "scenario_sha256": _digest(source_file.read_bytes()),
        "specification": specification, "scenario": scenario, "parent_requirement": parent,
        "fixtures": network.get("fixtures", {}),
    }
    source["sha256"] = _digest(json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    return {
        "scope": "scenario_pilot", "status": "partial", "scenario_id": scenario_id,
        "parent_requirement": requirement, "module": owned["id"], "layer": layer, "member_id": member["id"],
        "source": source, "depends_on": specification.get("depends_on", []),
        "integration_dependencies": parent.get("integration_dependencies", []),
        "external_dependencies": sorted(set(parent.get("external_dependencies", []) + specification.get("external_dependencies", []))),
        "limitations": "Only the selected scenario is under trial; its parent requirement, UI, persistence and integration are not thereby accepted.",
    }


def validate_receipt_scope(data, scope=None):
    if not isinstance(data, dict):
        raise PolicyError("PILOT_RECEIPT_MIXING_FORBIDDEN")
    if scope is None:
        if data.get("scope") == "scenario_pilot" or "scenarios" in data:
            raise PolicyError("PILOT_FORMAL_RECEIPT_MIXING_FORBIDDEN")
        return
    if data and (data.get("scope") != "scenario_pilot" or "requirements" in data or "module" in data or not isinstance(data.get("scenarios"), dict)):
        raise PolicyError("PILOT_FORMAL_RECEIPT_MIXING_FORBIDDEN")
    for receipt in data.get("scenarios", {}).values():
        if any(receipt.get(key) != scope[key] for key in ("member_id", "module", "layer")):
            raise PolicyError("PILOT_RECEIPT_CONTEXT_MISMATCH")
    previous = data.get("scenarios", {}).get(scope["scenario_id"])
    if previous and previous.get("source", {}).get("sha256") != scope["source"]["sha256"]:
        raise PolicyError("PILOT_SOURCE_CHANGED: start a new private receipt file for the revised scope")
