"""Normalize actual Vitest JSON / JUnit XML output and reject false-green receipts."""
import json
import re
import xml.etree.ElementTree as ET
from policy import PolicyError

RESULTS = {"PASS", "FAIL", "ERROR", "SKIPPED"}


def _vitest_runtime_error(message):
    header = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", message).lstrip()
    return re.match(r"(?:Error: (?:BROWSER_|ELEMENT_|Test timed out\b)|(?:TypeError|ReferenceError|SyntaxError|TimeoutError|AbortError)(?::|$))", header) is not None

def summarize(cases):
    names = [c["id"] for c in cases]
    if any(not isinstance(name,str) or not name.strip() or name in ("::","#") for name in names):raise PolicyError("MISSING_TEST_ID")
    if len(names) != len(set(names)):
        raise PolicyError("DUPLICATE_TEST_ID")
    counts = {key: 0 for key in ("total", "executed", "passed", "failed", "errors", "skipped")}
    for case in cases:
        if case.get("result") not in RESULTS:
            raise PolicyError("UNKNOWN_TEST_RESULT")
        counts["total"] += 1
        counts[{"PASS": "passed", "FAIL": "failed", "ERROR": "errors", "SKIPPED": "skipped"}[case["result"]]] += 1
    counts["executed"] = counts["passed"] + counts["failed"] + counts["errors"]
    return {"counts": counts, "cases": cases}

def parse_report(body, format):
    try:
        if format == "vitest":
            value = json.loads(body)
            cases = []
            for suite in value.get("testResults", []):
                for case in suite.get("assertionResults", []):
                    status = {"passed": "PASS", "failed": "FAIL", "pending": "SKIPPED", "skipped": "SKIPPED", "todo": "SKIPPED"}.get(case.get("status"))
                    if status is None:
                        raise PolicyError("UNKNOWN_TEST_RESULT")
                    title = case.get("fullName") or " ".join(case.get("ancestorTitles", []) + [case.get("title", "")])
                    messages = [str(x) for x in case.get("failureMessages", [])]
                    if status == "FAIL" and any(_vitest_runtime_error(message) for message in messages):
                        status = "ERROR"
                    cases.append({"id": suite.get("name", "") + "::" + title, "result": status, "details": "\n".join(messages)[:8000]})
            result = summarize(cases)
            for key, field in [("numTotalTests", "total"), ("numPassedTests", "passed"), ("numFailedTests", "failed")]:
                expected = result["counts"][field] + (result["counts"]["errors"] if field == "failed" else 0)
                if key in value and value[key] != expected:
                    raise PolicyError("REPORT_COUNT_MISMATCH")
            return result
        if format == "junit":
            root = ET.fromstring(body)
            cases = []
            for case in root.iter("testcase"):
                status = "ERROR" if case.find("error") is not None else "FAIL" if case.find("failure") is not None else "SKIPPED" if case.find("skipped") is not None else "PASS"
                failure=case.find("error") if case.find("error") is not None else case.find("failure")
                detail=("" if failure is None else failure.get("message","")+"\n"+"".join(failure.itertext()))[:8000]
                cases.append({"id": case.get("classname", "") + "#" + case.get("name", ""), "result": status, "details": detail})
            result = summarize(cases)
            if root.get("tests") is not None and int(root.get("tests")) != result["counts"]["total"]:
                raise PolicyError("REPORT_COUNT_MISMATCH")
            return result
    except (ValueError, TypeError, KeyError, ET.ParseError) as error:
        raise PolicyError("INVALID_TEST_REPORT") from error
    raise PolicyError("UNKNOWN_TEST_REPORT_FORMAT")

def merge_reports(reports, required=()):
    result = summarize([case for report in reports for case in report["cases"]])
    result["required_tests"] = sorted(set(required)) if required else [c["id"] for c in result["cases"]]
    return result

def validate_result(result, stage):
    if not isinstance(result, dict) or not isinstance(result.get("cases"), list):
        raise PolicyError("TEST_RESULT_REQUIRED")
    actual = summarize(result["cases"])
    if actual["counts"] != result.get("counts"):
        raise PolicyError("REPORT_COUNT_MISMATCH")
    counts = actual["counts"]
    if counts["executed"] <= 0:
        raise PolicyError("ZERO_EXECUTED_TESTS")
    required = result.get("required_tests")
    by_id = {c["id"]: c["result"] for c in actual["cases"]}
    if not isinstance(required, list) or not required or len(set(required)) != len(required):
        raise PolicyError("REQUIRED_TEST_IDS_MISSING")
    if any(name not in by_id for name in required):
        raise PolicyError("REQUIRED_TEST_NOT_RUN")
    if counts["errors"] or counts["skipped"]:
        raise PolicyError("ERROR_OR_SKIPPED_IS_NOT_A_VALID_TDD_RUN")
    if stage in ("red", "mutation"):
        if not any(by_id[name] == "FAIL" for name in required):
            raise PolicyError("NO_REQUIRED_ASSERTION_FAILED")
    elif counts["failed"] or any(by_id[name] != "PASS" for name in required):
        raise PolicyError("GREEN_HAS_FAILURES")
    return True
