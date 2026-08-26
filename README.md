# 2JO · Deal-to-Order SaaS

> 중소 B2B 영업팀을 위한 **견적 발송·승인 추적 도구.**
> 딜을 단계로 관리하고, 견적을 만들어 링크로 보내고, **계정 없는 고객**이 열람·승인한 사실을 기록으로 남기고, 승인된 견적을 주문 스냅샷으로 확정한다.

|  |  |
| --- | --- |
| 팀 | A 조민석 · B 한상민 · C 최선진 · D 이준형 · E 김대연 (5명 · 4주) |
| 문서 | **[docs/](docs/README.md)** — 기획·설계 전체 (번호 순서가 곧 읽는 순서) |

## 레포 구조

```
backend/    Spring Boot 4 · Java 21 · Gradle — API 서버 단일 애플리케이션
frontend/   Vite · React · TypeScript · Radix Themes — 구성원 앱/관리자/고객 열람
infra/      docker-compose (로컬 PostgreSQL 16) · 배포/모니터링 구성 예정
docs/       기획·설계 문서 세트 (명세 변경은 팀 합의 + 버전 업으로만)
.github/    PR 템플릿 · CI (build / flyway-validate / flyway-version-check / frontend)
```

## 빠른 시작

```bash
# 1. 로컬 PostgreSQL (포트 5433 — 로컬 네이티브 PG와 충돌 방지)
docker compose -f infra/docker-compose.yml up -d

# 2. 백엔드 (http://localhost:8080)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

# 3. 프론트엔드 (http://localhost:5173 · MSW 목 우선)
cd frontend && npm install && npm run dev
```

테스트는 실제 PG(`twojo_test` DB)에 Flyway를 태워 돈다 — **docker compose가 떠 있어야 한다.**

```bash
cd backend && ./gradlew test
```

## 백엔드 구조 — Spring Modulith

`com.twojo.{도메인}` 최상위 패키지 = 모듈. **타 도메인 접근은 `boundary` 패키지의 공개 인터페이스로만** — 타인 Repository 주입·엔티티 매핑 금지 (docs/11 §7). 위반은 `ModularityTests`가 잡고, CI가 머지를 차단한다.

| 패키지 | 담당 | 내용 |
| --- | --- | --- |
| `onboarding` `member` `auth` | A | 온보딩 · 구성원 · 인증(+공통 기반) |
| `customer` `product` `activity` | B | 고객사 · 카탈로그 · 활동이력 |
| `deal` `quote` `order` | C | Deal · 견적 · 주문 |
| `approval` `notification` `dashboard` | D | 고객 열람·승인 · 알림 · 현황 |
| `boundary` | 전원 | 도메인 간 계약 인터페이스 9종 (구현은 소유자) |
| `global` | E | ErrorCode · ErrorResponse · PageResponse · 보안 설정 |

## 규약 요약 (정본은 docs/)

- 브랜치: `main` ← `release/x.y.z` ← `develop` ← `{타입}/{도메인코드}_{이슈번호}` (예: `feat/AU_26`) — 전부 PR로만 (docs/13)
- Flyway: `V1__baseline.sql`(E) 이후 도메인 번호대 **A=1xx · B=2xx · C=3xx · D=4xx** (docs/13 §0.2)
- API: `/api/v1` · `/admin/api/v1` · `/public/api/v1` — **명세(docs/07)에 없는 엔드포인트는 v1에 없다**
- 에러: `ErrorCode` enum이 코드·HTTP·문구의 단일 원본 (docs/07 부록)
- 페이징: `?page=0&size=20` (0-base · 최대 100 · 정렬은 서버 고정, Q-39)

## 초기 세팅에서 확정한 사항 (2026-08-27)

| 항목 | 결정 | 비고 |
| --- | --- | --- |
| Spring Boot | **4.1.1** | 문서(docs/14)의 "3.x 고정"은 3.x OSS 지원 종료(2026-06)로 4.x 전환 — **docs/14 버전 업 필요 (팀 공유)** |
| 빌드 | Gradle (Groovy DSL) · Java 21 toolchain | |
| 모듈 경계 | 단일 Gradle 모듈 + **Spring Modulith** | 이벤트 영속화 스타터 제외 (event_publication 테이블 없음 — ERD 25테이블 유지) |
| 경계 인터페이스 위치 | `com.twojo.boundary` | B↔C·C↔D 양방향 호출이 Modulith 순환 금지에 걸려, 계약을 별도 모듈로 분리 |
| 로컬 DB 포트 | **5433** | 팀원 로컬의 네이티브 PostgreSQL(5432)과 충돌 방지 |
| 테스트 DB | docker-compose의 `twojo_test` + CI 서비스 컨테이너 | Testcontainers는 Docker Engine 29 npipe 비호환으로 미사용 |
