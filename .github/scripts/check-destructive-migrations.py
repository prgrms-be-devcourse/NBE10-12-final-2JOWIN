#!/usr/bin/env python3
"""마이그레이션에 되돌릴 수 없는 SQL 이 있는지 검사한다.

왜 필요한가:

배포 실패 시 롤백은 이미지만 되돌린다. Flyway 가 이미 마이그레이션을
적용했다면 스키마는 새 상태로 남고, 옛 코드가 그 위에서 돌지 못할 수
있다. 컬럼을 지우는 마이그레이션이 그렇다.

  t0  새 이미지 기동 -> Flyway 가 컬럼 삭제
  t1  앱이 다른 이유로 죽음
  t2  롤백 -> 옛 이미지
  t3  옛 코드가 없는 컬럼을 조회 -> 기동 실패 (exit 3)

되돌리기 스크립트로는 못 푼다. flyway undo 는 유료이고, 유료라도
지워진 데이터는 돌아오지 않는다. 컬럼을 다시 추가하면 빈 컬럼이 생긴다.

해법은 2단계 전개다:
  1차 배포  그 컬럼을 쓰지 않게 코드만 수정 (스키마는 그대로)
  2차 배포  컬럼 삭제
모든 시점에서 "옛 코드 + 새 스키마" 가 동작하므로 롤백이 항상 성립한다.

승인 게이트로는 막지 못한다. 사람이 승인 화면에서 보는 것은 커밋 목록뿐,
그 안에 파괴적 SQL 이 있는지는 보이지 않는다.

의도한 경우에는 PR 에 라벨을 붙여 통과시킨다. 막는 것이 목적이 아니라
"의식적인 결정" 으로 만드는 것이 목적이다.

사용: check-destructive-migrations.py <마이그레이션 디렉터리>
설계 근거: 배포 승인 제거와 함께 도입 (2026-09-08)
"""

import glob
import os
import re
import sys

# 윈도우 콘솔은 기본이 cp949 라 한글 출력이 깨진다. CI(리눅스)에서는
# 영향이 없지만, 이 스크립트는 로컬에서도 돌린다.
sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# 데이터가 사라지는 것만 잡는다.
#
# DROP CONSTRAINT 는 넣지 않았다. 제약을 갈아끼우는 것은 스키마 진화의
# 정상 과정이고 데이터가 사라지지 않는다 (V201 이 그렇게 한다).
PATTERNS = [
    (r'\bDROP\s+COLUMN\b', 'DROP COLUMN — 그 컬럼의 데이터가 사라진다'),
    (r'\bDROP\s+TABLE\b', 'DROP TABLE — 그 테이블이 사라진다'),
    (r'\bTRUNCATE\b', 'TRUNCATE — 행이 전부 사라진다'),
    (r'\bDROP\s+SCHEMA\b', 'DROP SCHEMA'),
    (r'\bDROP\s+DATABASE\b', 'DROP DATABASE'),
]

# 줄 주석과 블록 주석은 검사 대상이 아니다.
LINE_COMMENT = re.compile(r'--[^\n]*')
BLOCK_COMMENT = re.compile(r'/\*.*?\*/', re.S)


def strip_comments(text):
    return LINE_COMMENT.sub('', BLOCK_COMMENT.sub('', text))


def main() -> int:
    root = sys.argv[1] if len(sys.argv) > 1 else 'backend/src/main/resources/db/migration'
    files = sorted(glob.glob(os.path.join(root, '*.sql')))

    hits = []
    for path in files:
        with open(path, encoding='utf-8') as fp:
            text = strip_comments(fp.read())
        for line_no, line in enumerate(text.splitlines(), 1):
            for pattern, why in PATTERNS:
                if re.search(pattern, line, re.I):
                    hits.append((path, line_no, line.strip(), why))

    print(f'검사한 마이그레이션: {len(files)}개')

    if not hits:
        print('되돌릴 수 없는 SQL 없음')
        return 0

    print('\n되돌릴 수 없는 SQL 이 있다:\n')
    for path, line_no, line, why in hits:
        print(f'  {path}:{line_no}')
        print(f'    {line}')
        print(f'    {why}\n')

    print('롤백은 이미지만 되돌린다. 스키마는 새 상태로 남아, 옛 코드가')
    print('그 위에서 돌지 못하면 롤백해도 서비스가 복구되지 않는다.\n')
    print('2단계로 나누면 이 문제가 사라진다:')
    print('  1차 배포  그 컬럼을 쓰지 않게 코드만 수정')
    print('  2차 배포  컬럼 삭제\n')
    print('의도한 것이라면 PR 에 destructive-migration 라벨을 붙일 것.')
    return 1


if __name__ == '__main__':
    sys.exit(main())
