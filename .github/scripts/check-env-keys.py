#!/usr/bin/env python3
"""infra/prod/.env.example 이 실제로 요구되는 키를 다 담고 있는지 대조한다.

빠지면 어떻게 되는가:

  compose 쪽 키   -> docker compose 가 파싱 단계에서 죽는다. 금방 드러난다.
  Spring 쪽 키    -> CI 는 초록불인데 서버에서 앱만 못 뜬다. 늦게 드러난다.

실제로 후자가 있었다. config-check 의 더미 env 에 DB_URL 과
INVITATION_BASE_URL 이 없었는데, config-check 는 compose 파싱만 보므로
통과했다. Spring 은 기본값 없는 placeholder 를 못 채우면 기동에 실패한다.

두 곳에서 "기본값 없는 참조"만 모은다. 기본값이 있으면 없어도 도니까
필수가 아니다.

  application.yml       ${VAR}        (${VAR:기본값} 은 제외)
  compose · Caddyfile   ${VAR:?...}   (${VAR:-기본값} 은 제외)

사용: check-env-keys.py <레포 루트>
설계 근거: PROD_ENV_FILE 준비 중 발견 (2026-09-08)
"""

import glob
import os
import re
import sys

# 윈도우 콘솔은 기본이 cp949 라 한글 출력이 깨진다. CI(리눅스)에서는
# 영향이 없지만, 이 스크립트는 로컬에서도 돌린다.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# .env 가 아니라 deploy.sh 가 .env.image 에 따로 쓴다.
EXEMPT = {"BACKEND_IMAGE"}

# ${VAR} 만. ${VAR:...} 는 기본값이 있으므로 필수가 아니다.
SPRING = re.compile(r"\$\{([A-Z_][A-Z0-9_]*)\}")

# ${VAR:?...} 와 맨몸 ${VAR} 는 필수, ${VAR:-...} 는 기본값이 있다.
#
# 앞에 $ 가 하나 더 붙은 $${VAR} 은 제외한다. compose 가 컨테이너 셸에
# 리터럴 $ 로 넘기는 이스케이프이지 .env 참조가 아니다 — postgres 의
# healthcheck 가 그걸 쓴다.
COMPOSE_REQUIRED = re.compile(r"(?<!\$)\$\{([A-Z_][A-Z0-9_]*)(?::\?[^}]*)?\}")
COMPOSE_DEFAULTED = re.compile(r"(?<!\$)\$\{([A-Z_][A-Z0-9_]*):-[^}]*\}")


def read(path):
    with open(path, encoding="utf-8") as fp:
        return fp.read()


def declared(path):
    """.env.example 에 선언된 키."""
    keys = set()
    for line in read(path).splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        keys.add(line.split("=", 1)[0].strip())
    return keys


def required(root):
    """앱과 인프라 설정이 실제로 요구하는 키."""
    need = {}

    app = os.path.join(root, "backend/src/main/resources/application.yml")
    if os.path.exists(app):
        for name in SPRING.findall(read(app)):
            need.setdefault(name, "application.yml")

    for pattern in ("infra/prod/compose/*.yml", "infra/prod/caddy/*",
                    "infra/prod/monitoring/**/*.yml"):
        for path in glob.glob(os.path.join(root, pattern), recursive=True):
            if os.path.isdir(path):
                continue
            text = read(path)
            defaulted = set(COMPOSE_DEFAULTED.findall(text))
            for name in COMPOSE_REQUIRED.findall(text):
                if name not in defaulted:
                    need.setdefault(name, os.path.relpath(path, root))

    for name in EXEMPT:
        need.pop(name, None)
    return need


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    example = os.path.join(root, "infra/prod/.env.example")

    if not os.path.exists(example):
        print(f"{example} 이 없다")
        return 1

    have = declared(example)
    need = required(root)

    missing = sorted(k for k in need if k not in have)
    extra = sorted(k for k in have if k not in need)

    print(f"요구되는 키 {len(need)}개 · .env.example 에 선언된 키 {len(have)}개")

    if missing:
        print("\n.env.example 에 빠진 키:")
        for k in missing:
            print(f"  {k}  ({need[k]} 이 요구한다)")

    if extra:
        # 남는 것은 실패로 보지 않는다. 기본값이 있는 키를 굳이 명시해 두는
        # 경우가 있고(DB_NAME 처럼 DB_URL 과 맞춰야 하는 값), 그건 의도다.
        print("\n요구되지 않지만 선언된 키 (참고):")
        for k in extra:
            print(f"  {k}")

    if missing:
        print("\n빠진 키는 PROD_ENV_FILE 에도 빠질 가능성이 높다.")
        print("Spring 쪽 키가 빠지면 CI 는 통과하고 서버에서 앱만 못 뜬다.")
        return 1

    print("\n모두 선언되어 있다")
    return 0


if __name__ == "__main__":
    sys.exit(main())
