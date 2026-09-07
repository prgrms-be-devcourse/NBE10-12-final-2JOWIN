# infra

> 실행 위치로 나눈다 — `dev/` = **각자 노트북**, `prod/` = **AWS**.
> 설계 근거는 [docs/16-infra-and-deployment.md](../docs/16-infra-and-deployment.md).

| 폴더 | 도는 곳 | Spring 프로필 | Vite 모드 |
| --- | --- | --- | --- |
| `dev/` | 팀원 노트북 (Docker) | `local` · `test` | `development` |
| `prod/` | AWS EC2 | `prod` | `production` |

> 폴더명은 `dev`지만 백엔드 프로필명은 `local`이다 — 같은 환경을 가리킨다.

---

## dev/ — 로컬 개발

| 경로 | 내용 |
| --- | --- |
| `dev/docker-compose.yml` | PostgreSQL 16 (`twojo`, `twojo_test`) · 포트 **5433** |
| `dev/postgres/init/` | 컨테이너 최초 기동 시 실행되는 SQL |

```bash
docker compose -f infra/dev/docker-compose.yml up -d
```

- 백엔드는 컨테이너로 띄우지 않는다. `./gradlew bootRun`(프로필 `local`)으로 직접 실행한다.
- `./gradlew test`는 `twojo_test` DB가 필요하므로 이 컨테이너가 먼저 떠 있어야 한다.
- 5432가 아니라 **5433**을 쓴다 — 팀원 로컬의 네이티브 PostgreSQL과 충돌 방지.

---

## prod/ — AWS 배포

| 경로 | 내용 |
| --- | --- |
| `prod/terraform/bootstrap/` | tfstate 버킷 · GitHub OIDC · TF 실행 역할 — **1회만 실행** |
| `prod/terraform/modules/` | 재사용 모듈 (아래 표) |
| `prod/terraform/` (루트) | 실제 환경 구성 — `main.tf` · `variables.tf` · `backend.tf`. 버킷 이름은 `init -backend-config` 로 주입한다 (계정 ID가 들어가서 코드에 못 박는다) |
| `prod/docker/` | `backend.Dockerfile` — 빌드 컨텍스트는 `backend/` |
| `prod/compose/` | EC2 위 스택 — Caddy · backend · PostgreSQL · 모니터링 |
| `prod/caddy/` | `Caddyfile` — TLS 종단 · `/actuator` 차단 · 재시도 버퍼 |
| `prod/monitoring/` | Prometheus · Loki · Promtail · Grafana 설정과 대시보드 |
| `prod/scripts/` | `cost_report.py` · `deploy.sh` · `fetch-secrets.sh` · `backup.sh` · `tunnel.sh` |

### terraform 모듈

| 모듈 | 만드는 것 |
| --- | --- |
| `network/` | VPC · 퍼블릭 서브넷 · IGW · 보안그룹(80/443만) — NAT Gateway 없음 |
| `compute/` | EC2 · Elastic IP · 인스턴스 프로파일(SSM) · cloud-init |
| `storage/` | ECR(수명주기 10개) · S3 백업 버킷(수명주기 7일) |
| `mail/` | SES 도메인 인증 · DKIM · 발송 IAM |
| `cost-guard/` | IAM Deny 가드레일 · Budgets · Budget Action 2종 · SNS → Discord Lambda |

### 최초 실행 순서

| # | 명령 | 비고 |
| --- | --- | --- |
| 1 | `cd prod/terraform/bootstrap && terraform apply` | 로컬 state → 생성된 S3로 이관 |
| 2 | `cd prod/terraform && terraform apply -target=module.cost_guard` | **다른 리소스보다 먼저** |
| 3 | dnszi에 A 레코드 등록 (`api` → EIP) | Let's Encrypt 발급 전제 |
| 4 | `cd prod/terraform && terraform apply` | 전체 |

### 이미지 빌드

```bash
docker build -f infra/prod/docker/backend.Dockerfile backend/
```

- Dockerfile을 `infra/` 아래 두는 이유: 이미지 튜닝(베이스 이미지·JVM 옵션·레이어 캐시)이 인프라 소유로 남아 반복 수정에 팀원 리뷰가 필요 없다.
- **`.dockerignore`만 예외로 `backend/`에 둔다.** BuildKit은 `<Dockerfile경로>.dockerignore`도 읽지만 구형 빌더는 컨텍스트 루트만 읽는다. `infra/` 안에만 두면 로컬 구형 빌더에서 `backend/build/`·`.gradle/`이 통째로 컨텍스트에 실린다.

---

## 알아둘 것

- **AWS 환경은 prod 하나뿐이다.** 예산 ₩80,000 안에서 두 번째 상시 환경이 불가능하다 → terraform에 `envs/` 계층을 두지 않았다.
- 그래서 **인프라 변경을 미리 시험할 AWS 환경이 없다.** `terraform plan` PR 코멘트 · Infracost 비용 게이트 · GitHub Environment 승인으로 대신한다.
- 시크릿은 저장소에 두지 않는다. SSM Parameter Store → `scripts/fetch-secrets.sh` → `/opt/2jo/.env`.

## 이 디렉터리 밖 관련 파일

| 경로 | 내용 |
| --- | --- |
| `backend/.dockerignore` | 빌드 컨텍스트 제외 목록 |
| `.github/workflows/deploy.yml` | 백엔드 CD |
| `.github/workflows/infra.yml` | terraform fmt · validate · tflint · checkov · Infracost · plan / apply |
| `.github/workflows/cost-report.yml` | 일일 누적 비용 리포트 + 한도 초과 시 EC2 정지 |
| `frontend/.env.production` | `VITE_API_BASE_URL` |
