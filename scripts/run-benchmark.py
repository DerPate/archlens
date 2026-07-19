#!/usr/bin/env python3
"""Run deterministic architecture-question benchmarks against the packaged MCP server."""

from __future__ import annotations

import argparse
import glob
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCENARIOS = PROJECT_ROOT / "benchmarks" / "scenarios"
DEFAULT_REPORT_DIR = PROJECT_ROOT / "target" / "benchmark"
DEFAULT_BASELINE = PROJECT_ROOT / "benchmarks" / "baseline.json"


def find_jar(explicit: str | None) -> Path:
    if explicit:
        jar = Path(explicit).resolve()
        if not jar.is_file():
            raise FileNotFoundError(f"ArchLens jar not found: {jar}")
        return jar
    candidates = [
        Path(candidate)
        for candidate in glob.glob(str(PROJECT_ROOT / "target" / "archlens*.jar"))
        if not any(token in Path(candidate).name for token in ("-sources", "-javadoc", "original-"))
    ]
    if not candidates:
        raise FileNotFoundError("No ArchLens jar found under target/. Run 'mvn package' first.")
    return max(candidates, key=lambda candidate: candidate.stat().st_mtime)


class McpProcess:
    """Sequential stdio JSON-RPC client following the server's required handshake."""

    def __init__(self, jar: Path, cwd: Path, stderr_path: Path) -> None:
        stderr_path.parent.mkdir(parents=True, exist_ok=True)
        self._stderr = stderr_path.open("wb")
        self._proc = subprocess.Popen(
            ["java", "-jar", str(jar)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=self._stderr,
            cwd=cwd,
        )
        self._request_id = 0

    def call(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        self._request_id += 1
        message: dict[str, Any] = {"jsonrpc": "2.0", "id": self._request_id, "method": method}
        if params is not None:
            message["params"] = params
        assert self._proc.stdin is not None
        assert self._proc.stdout is not None
        self._proc.stdin.write((json.dumps(message) + "\n").encode())
        self._proc.stdin.flush()
        raw = self._proc.stdout.readline()
        if not raw:
            raise RuntimeError("ArchLens closed stdout before returning a response")
        response = json.loads(raw)
        if "error" in response:
            raise RuntimeError(f"JSON-RPC error for {method}: {response['error']}")
        return response.get("result", {})

    def notify(self, method: str, params: dict[str, Any] | None = None) -> None:
        message: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        assert self._proc.stdin is not None
        self._proc.stdin.write((json.dumps(message) + "\n").encode())
        self._proc.stdin.flush()

    def tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        result = self.call("tools/call", {"name": name, "arguments": arguments})
        if result.get("isError"):
            text = result.get("content", [{}])[0].get("text", "unknown tool error")
            raise RuntimeError(f"Tool {name} failed: {text}")
        return result

    def initialize(self) -> None:
        self.call(
            "initialize",
            {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {"name": "archlens-benchmark", "version": "1"},
            },
        )
        self.notify("notifications/initialized", {})

    def close(self) -> None:
        if self._proc.stdin is not None:
            self._proc.stdin.close()
        self._proc.wait(timeout=30)
        self._stderr.close()


def load_scenarios(root: Path, selected: set[str]) -> list[tuple[Path, dict[str, Any]]]:
    scenarios: list[tuple[Path, dict[str, Any]]] = []
    for manifest_path in sorted(root.glob("*/scenario.json")):
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        scenario_id = str(manifest["id"])
        if selected and scenario_id not in selected:
            continue
        scenarios.append((manifest_path, manifest))
    missing = selected - {manifest["id"] for _, manifest in scenarios}
    if missing:
        raise ValueError(f"Unknown benchmark scenario(s): {', '.join(sorted(missing))}")
    if not scenarios:
        raise ValueError(f"No benchmark scenarios found under {root}")
    return scenarios


def select_path(value: Any, path: str) -> Any:
    current = value
    if not path:
        return current
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def matches(item: Any, expected: dict[str, Any]) -> bool:
    if not isinstance(item, dict):
        return False
    return all(select_path(item, path) == value for path, value in expected.items())


def evaluate_assertion(structured: Any, assertion: dict[str, Any]) -> tuple[bool, str]:
    path = str(assertion.get("path", ""))
    selected = select_path(structured, path)
    if "equals" in assertion:
        expected = assertion["equals"]
        return selected == expected, f"{path or '<root>'} equals {expected!r} (actual {selected!r})"
    if "contains" in assertion:
        expected = assertion["contains"]
        passed = isinstance(selected, list) and expected in selected
        return passed, f"{path or '<root>'} contains {expected!r}"
    if not isinstance(selected, list):
        return False, f"{path or '<root>'} is not a collection"
    where = assertion.get("where", {})
    found = [item for item in selected if matches(item, where)]
    minimum = int(assertion.get("minCount", 1))
    maximum = assertion.get("maxCount")
    if len(found) < minimum:
        return False, f"expected at least {minimum} match(es) in {path}, found {len(found)} for {where}"
    if maximum is not None and len(found) > int(maximum):
        return False, f"expected at most {maximum} match(es) in {path}, found {len(found)} for {where}"
    for field in assertion.get("requiredFields", []):
        missing = [item for item in found if select_path(item, field) is None]
        if missing:
            return False, f"{len(missing)} matched item(s) lack required field {field}"
    for field in assertion.get("absentFields", []):
        present = [item for item in found if select_path(item, field) is not None]
        if present:
            return False, f"{len(present)} matched item(s) unexpectedly contain field {field}"
    return True, f"{len(found)} match(es) in {path}"


def run_scenario(
    jar: Path,
    manifest_path: Path,
    manifest: dict[str, Any],
    report_dir: Path,
    baseline: dict[str, Any],
) -> dict[str, Any]:
    scenario_id = str(manifest["id"])
    workspace = (manifest_path.parent / manifest["workspace"]).resolve()
    if not workspace.is_dir():
        raise FileNotFoundError(f"Workspace for {scenario_id} does not exist: {workspace}")
    result: dict[str, Any] = {"id": scenario_id, "workspace": str(workspace), "questions": []}
    with tempfile.TemporaryDirectory(prefix=f"archlens-benchmark-{scenario_id}-") as process_dir:
        client = McpProcess(jar, Path(process_dir), report_dir / f"{scenario_id}.stderr.log")
        try:
            client.initialize()
            index_started = time.perf_counter()
            client.tool("index_workspace", {"paths": [str(workspace)]})
            index_duration_ms = round((time.perf_counter() - index_started) * 1000, 2)
            summary = client.tool("query_architecture_graph", {"action": "summary"}).get(
                "structuredContent", {}
            )
            result["metrics"] = {
                "indexDurationMs": index_duration_ms,
                "nodeCount": int(summary.get("nodeCount", 0)),
                "edgeCount": int(summary.get("edgeCount", 0)),
            }
            for question in manifest["questions"]:
                question_result: dict[str, Any] = {
                    "id": question["id"],
                    "question": question["question"],
                    "tool": question["tool"],
                    "assertions": [],
                }
                try:
                    question_started = time.perf_counter()
                    tool_result = client.tool(question["tool"], question.get("arguments", {}))
                    question_result["durationMs"] = round(
                        (time.perf_counter() - question_started) * 1000, 2
                    )
                    structured = tool_result.get("structuredContent", {})
                    for assertion in question.get("assertions", []):
                        passed, detail = evaluate_assertion(structured, assertion)
                        question_result["assertions"].append({"passed": passed, "detail": detail})
                    question_result["passed"] = all(
                        assertion["passed"] for assertion in question_result["assertions"]
                    )
                except Exception as error:  # keep the remaining benchmark questions runnable
                    question_result["passed"] = False
                    question_result["error"] = str(error)
                result["questions"].append(question_result)
        finally:
            client.close()
    result["passed"] = all(question["passed"] for question in result["questions"])
    baseline_scenario = baseline.get(scenario_id, {})
    baseline_metrics = baseline_scenario.get("metrics", {})
    result["baseline"] = baseline_metrics
    result["deltas"] = {
        key: round(result["metrics"][key] - baseline_metrics[key], 2)
        if key in baseline_metrics
        else None
        for key in ("indexDurationMs", "nodeCount", "edgeCount")
    }
    previously_passing = set(baseline_scenario.get("passingQuestions", []))
    result["regressions"] = [
        question["id"]
        for question in result["questions"]
        if question["id"] in previously_passing and not question["passed"]
    ]
    return result


def write_reports(report_dir: Path, jar: Path, scenarios: list[dict[str, Any]]) -> None:
    report_dir.mkdir(parents=True, exist_ok=True)
    question_count = sum(len(scenario["questions"]) for scenario in scenarios)
    passed_count = sum(
        1 for scenario in scenarios for question in scenario["questions"] if question["passed"]
    )
    report = {
        "schemaVersion": 1,
        "jar": str(jar),
        "scenarioCount": len(scenarios),
        "questionCount": question_count,
        "passedCount": passed_count,
        "failedCount": question_count - passed_count,
        "scenarios": scenarios,
    }
    (report_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    lines = [
        "# ArchLens Benchmark Report",
        "",
        f"- Scenarios: {len(scenarios)}",
        f"- Questions: {question_count}",
        f"- Passed: {passed_count}",
        f"- Failed: {question_count - passed_count}",
        f"- Regressions: {sum(len(scenario['regressions']) for scenario in scenarios)}",
        "",
    ]
    for scenario in scenarios:
        lines.append(f"## {scenario['id']}")
        lines.append("")
        metrics = scenario["metrics"]
        deltas = scenario["deltas"]
        lines.append(
            f"- Metrics: index {metrics['indexDurationMs']} ms ({delta(deltas['indexDurationMs'])}), "
            f"nodes {metrics['nodeCount']} ({delta(deltas['nodeCount'])}), "
            f"edges {metrics['edgeCount']} ({delta(deltas['edgeCount'])})"
        )
        lines.append(f"- Regressions: {', '.join(scenario['regressions']) or 'none'}")
        for question in scenario["questions"]:
            marker = "PASS" if question["passed"] else "FAIL"
            duration = f" ({question.get('durationMs', 0)} ms)"
            lines.append(f"- **{marker}** `{question['id']}`{duration} — {question['question']}")
            if question.get("error"):
                lines.append(f"  - {question['error']}")
            for assertion in question["assertions"]:
                lines.append(f"  - {'✓' if assertion['passed'] else '✗'} {assertion['detail']}")
        lines.append("")
    (report_dir / "report.md").write_text("\n".join(lines), encoding="utf-8")


def delta(value: Any) -> str:
    if value is None:
        return "no baseline"
    prefix = "+" if value > 0 else ""
    return f"Δ {prefix}{value}"


def load_baseline(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    return {str(item["id"]): item for item in raw.get("scenarios", [])}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", help="Path to the packaged ArchLens jar")
    parser.add_argument("--scenarios", type=Path, default=DEFAULT_SCENARIOS, help="Scenario root directory")
    parser.add_argument("--scenario", action="append", default=[], help="Run only this scenario id")
    parser.add_argument("--report-dir", type=Path, default=DEFAULT_REPORT_DIR, help="Generated report directory")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE, help="Committed benchmark baseline")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    jar = find_jar(args.jar)
    manifests = load_scenarios(args.scenarios.resolve(), set(args.scenario))
    baseline = load_baseline(args.baseline.resolve())
    scenarios = [
        run_scenario(jar, path, manifest, args.report_dir.resolve(), baseline)
        for path, manifest in manifests
    ]
    write_reports(args.report_dir.resolve(), jar, scenarios)
    for scenario in scenarios:
        print(f"{'PASS' if scenario['passed'] else 'FAIL'} {scenario['id']}")
        for question in scenario["questions"]:
            print(f"  {'PASS' if question['passed'] else 'FAIL'} {question['id']}")
    passed = all(scenario["passed"] and not scenario["regressions"] for scenario in scenarios)
    print(f"Report: {args.report_dir.resolve() / 'report.md'}")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
