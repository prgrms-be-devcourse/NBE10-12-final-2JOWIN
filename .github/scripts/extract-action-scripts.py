#!/usr/bin/env python3
"""composite action 의 run 블록을 파일로 뽑아 shellcheck 에 걸 수 있게 한다.

actionlint 는 .github/workflows/*.yml 만 본다. action.yml 을 주면
"jobs 섹션이 없다"고 한다 — composite action 을 이해하지 못한다.
그래서 워크플로와 달리 액션의 셸 코드는 아무도 검사하지 않는다.

이 스크립트가 그 구멍을 메운다. run 블록마다 파일 하나를 만들고,
composite 의 `shell: bash` 기본값(-eo pipefail)을 헤더로 붙인다 —
그 조건에서 검사해야 실제로 도는 것과 같은 판정이 나온다.

사용: extract-action-scripts.py <출력 디렉터리>
설계 근거: cd.md §9
"""

import glob
import os
import sys

import yaml

# composite 의 shell: bash 기본값과 같은 조건. -e 가 켜져 있다는 전제에서
# 검사해야 `cmd; rc=$?` 같은 패턴이 잘못이라는 게 드러난다.
HEADER = "#!/bin/bash\nset -eo pipefail\n"

# 셸이 아닌 스텝(uses, shell: python 등)은 대상이 아니다.
SHELLS = (None, "bash", "sh")


def main() -> int:
    if len(sys.argv) != 2:
        print("사용: extract-action-scripts.py <출력 디렉터리>", file=sys.stderr)
        return 2

    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)

    count = 0
    for path in sorted(glob.glob(".github/actions/*/action.yml")):
        with open(path, encoding="utf-8") as fp:
            doc = yaml.safe_load(fp) or {}

        steps = (doc.get("runs") or {}).get("steps") or []
        action = os.path.basename(os.path.dirname(path))

        for i, step in enumerate(steps):
            if "run" not in step or step.get("shell") not in SHELLS:
                continue
            target = os.path.join(out, "%s-%02d.sh" % (action, i))
            with open(target, "w", encoding="utf-8", newline="\n") as fp:
                fp.write(HEADER + step["run"])
            count += 1

    print("추출한 run 블록: %d" % count)
    return 0


if __name__ == "__main__":
    sys.exit(main())
