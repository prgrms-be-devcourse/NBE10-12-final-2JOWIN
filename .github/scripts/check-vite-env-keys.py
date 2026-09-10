#!/usr/bin/env python3
"""프론트 코드가 쓰는 VITE_* 키가 선언되어 있는지 확인한다.

`import.meta.env` 에는 인덱스 시그니처가 있어서 **선언에 없는 키를 써도
타입 오류가 나지 않는다.** 빌드는 통과하고, 배포 환경에 그 키를 넣지 않으면
런타임에 undefined 가 되어 호출이 조용히 엉뚱한 곳으로 간다.

백엔드의 check-env-keys.py 와 같은 종류다. 다른 점은 이쪽이 더 조용하다는
것뿐이다 — 백엔드는 기동이 실패하지만 프론트는 화면이 그냥 안 된다.

선언은 vite-env.d.ts 한 곳만 본다. 목록을 두 벌로 두면 어긋난다.

사용법: check-vite-env-keys.py <frontend 디렉터리>
"""
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# import.meta.env.VITE_X 와 import.meta.env["VITE_X"] 두 형태를 모두 잡는다
USED = re.compile(r"""import\.meta\.env(?:\.(VITE_[A-Z0-9_]+)|\[\s*['"](VITE_[A-Z0-9_]+)['"]\s*\])""")
DECLARED = re.compile(r"readonly\s+(VITE_[A-Z0-9_]+)\s*\??\s*:")

SRC_SUFFIXES = {".ts", ".tsx", ".js", ".jsx"}


def main(root: Path) -> int:
    decl_file = root / "src" / "vite-env.d.ts"
    if not decl_file.is_file():
        print(f"선언 파일을 찾지 못했다: {decl_file}")
        return 1

    declared = set(DECLARED.findall(decl_file.read_text(encoding="utf-8")))

    used: dict[str, list[str]] = {}
    for path in sorted((root / "src").rglob("*")):
        if path.suffix not in SRC_SUFFIXES or path == decl_file:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for a, b in USED.findall(text):
            key = a or b
            used.setdefault(key, []).append(str(path.relative_to(root)))

    print(f"선언된 키 {len(declared)}개 · 코드가 쓰는 키 {len(used)}개")

    missing = sorted(set(used) - declared)
    unused = sorted(declared - set(used))

    if unused:
        print("\n선언만 있고 쓰지 않는 키 (실패 아님):")
        for key in unused:
            print(f"  {key}")

    if not missing:
        print("\n쓰는 키가 전부 선언되어 있다")
        return 0

    print("\n선언되지 않은 키를 쓰고 있다:")
    for key in missing:
        where = sorted(set(used[key]))
        print(f"  {key}")
        for w in where[:5]:
            print(f"      {w}")
        if len(where) > 5:
            print(f"      … 외 {len(where) - 5}곳")

    print(f"\n{decl_file.relative_to(root)} 의 ImportMetaEnv 에 추가할 것:")
    for key in missing:
        print(f"  readonly {key}?: string")
    print("\n그리고 배포 환경(Vercel)에도 같은 키가 있는지 확인할 것 —")
    print("없으면 런타임에 undefined 가 된다.")
    return 1


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(Path(sys.argv[1])))
