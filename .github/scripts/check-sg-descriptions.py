#!/usr/bin/env python3
"""보안그룹 설명이 AWS 가 받는 문자만 쓰는지 검사한다.

AWS 는 보안그룹과 그 규칙의 description 에 허용 문자를 제한한다.
벗어나면 plan 은 멀쩡히 통과하고 apply 가 400 으로 죽는다:

  InvalidParameterValue: Invalid rule description. Valid descriptions are
  strings less than 256 characters from the following set:
  a-zA-Z0-9. _-:/()#,@[]+=&;{}!$*

실제로 "Let's Encrypt" 의 아포스트로피 하나 때문에 첫 apply 가
반쯤 만들어진 채로 멈췄다. 아웃바운드 규칙이 빠져서 서버가 인터넷에
나가지 못했고 cloud-init 이 도커를 받지 못했다.

tflint aws 룰셋 0.48.0 에는 IAM 역할 설명 검사(aws_iam_role_invalid_description)
는 있지만 보안그룹 규칙 설명 검사는 없다. 그 구멍을 여기서 메운다.

사용: check-sg-descriptions.py <terraform 루트>
설계 근거: 첫 apply 실패 (2026-09-08)
"""

import glob
import os
import re
import sys

# AWS 문서의 허용 집합. 공백도 포함된다.
ALLOWED = re.compile(r'^[a-zA-Z0-9. _\-:/()#,@\[\]+=&;{}!$*]*$')
MAX_LEN = 255

# 설명 규칙이 걸리는 리소스들.
TARGETS = (
    "aws_security_group",
    "aws_vpc_security_group_ingress_rule",
    "aws_vpc_security_group_egress_rule",
    "aws_default_security_group",
)

RESOURCE = re.compile(r'^resource\s+"([a-z0-9_]+)"\s+"([a-zA-Z0-9_-]+)"\s*\{', re.M)
DESCRIPTION = re.compile(r'^\s*description\s*=\s*"((?:[^"\\]|\\.)*)"', re.M)


def blocks(text):
    """resource 블록을 (타입, 이름, 본문, 시작줄) 로 자른다."""
    marks = [(m.start(), m.group(1), m.group(2)) for m in RESOURCE.finditer(text)]
    for i, (pos, kind, name) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(text)
        yield kind, name, text[pos:end], text.count("\n", 0, pos) + 1


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    bad = 0
    checked = 0

    for path in sorted(glob.glob(os.path.join(root, "**", "*.tf"), recursive=True)):
        if f"{os.sep}.terraform{os.sep}" in path:
            continue
        with open(path, encoding="utf-8") as fp:
            text = fp.read()

        for kind, name, body, line in blocks(text):
            if kind not in TARGETS:
                continue
            for m in DESCRIPTION.finditer(body):
                value = m.group(1)
                checked += 1
                offenders = sorted({c for c in value if not ALLOWED.match(c)})
                if offenders:
                    bad += 1
                    shown = " ".join(repr(c) for c in offenders)
                    print(f"{path}:{line}: {kind}.{name} 의 description 에 "
                          f"AWS 가 거부하는 문자가 있다: {shown}")
                    print(f"  값: {value}")
                elif len(value) > MAX_LEN:
                    bad += 1
                    print(f"{path}:{line}: {kind}.{name} 의 description 이 "
                          f"{len(value)}자다 (상한 {MAX_LEN})")

    print(f"검사한 description: {checked}개")
    if bad:
        print(f"\n허용 문자: a-zA-Z0-9. _-:/()#,@[]+=&;{{}}!$*")
        print("apply 단계에서 InvalidParameterValue 로 죽는다. plan 은 통과한다.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
