#!/usr/bin/env python3
"""vitest JSON 리포트를 잡 요약으로 옮긴다.

통과했을 때는 한 줄이면 되고, 실패했을 때 **어느 테스트가 깨졌는지**를
스텝을 펼치지 않고 보는 것이 목적이다 (#260).

리포트가 없으면 조용히 넘어간다 — 테스트 앞 단계에서 멈춘 경우다.

사용법: vitest-summary.py <리포트 경로>
"""
import json
import os
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

MAX_FAILURES = 20


def main(report: Path) -> int:
    out = os.environ.get("GITHUB_STEP_SUMMARY")
    if not out:
        print("GITHUB_STEP_SUMMARY 가 없다 — 로컬 실행으로 보고 표준출력에 쓴다")

    if not report.is_file():
        write(out, "### 테스트\n\n리포트가 없다 — 테스트 전에 멈췄다.\n")
        return 0

    d = json.loads(report.read_text(encoding="utf-8"))
    total = d.get("numTotalTests", 0)
    passed = d.get("numPassedTests", 0)
    failed = d.get("numFailedTests", 0)
    files = len(d.get("testResults", []))

    mark = "✅" if failed == 0 else "❌"
    lines = [
        "### 테스트",
        "",
        "| | |",
        "| --- | --- |",
        f"| 결과 | {mark} **{passed}/{total}** |",
        f"| 파일 | {files}개 |",
    ]
    if failed:
        lines.append(f"| 실패 | **{failed}개** |")
    lines.append("")

    if failed:
        lines += [f"#### 실패한 테스트 {failed}개", "", "| 파일 | 테스트 |", "| --- | --- |"]
        shown = 0
        for suite in d.get("testResults", []):
            # 리포트의 경로는 절대경로다. 레포 기준으로 줄여야 읽힌다.
            name = suite.get("name", "?").replace("\\", "/")
            if "/frontend/" in name:
                name = name.split("/frontend/", 1)[1]
            for case in suite.get("assertionResults", []):
                if case.get("status") != "failed":
                    continue
                if shown >= MAX_FAILURES:
                    break
                title = " › ".join(case.get("ancestorTitles", []) + [case.get("title", "?")])
                lines.append(f"| `{name}` | {title} |")
                shown += 1
        if failed > shown:
            lines.append(f"| … | 외 {failed - shown}개 |")
        lines.append("")

    write(out, "\n".join(lines) + "\n")
    return 0


def write(out: str | None, text: str) -> None:
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(text)
    else:
        print(text)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(Path(sys.argv[1])))
