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
| `prod/scripts/` | `deploy.sh` · `backup.sh` |
| `prod/systemd/` | 백업 타이머 (systemd **user** unit — `deploy.sh` 가 설치한다) |

### terraform 모듈

| 모듈 | 만드는 것 |
| --- | --- |
| `network/` | VPC · 퍼블릭 서브넷 · IGW · 보안그룹(상시 80/443만) — NAT Gateway 없음 |
| `compute/` | EC2 · Elastic IP · 인스턴스 프로파일(ECR·S3만) · 키페어 · cloud-init |
| `storage/` | ECR(수명주기 10개) · S3 백업 버킷(수명주기 7일) |
| `mail/` | SES 도메인 인증 · DKIM · 발송 IAM |

### 최초 실행 순서

순서가 강제된다. bootstrap 이 만드는 역할이 있어야 CI 가 apply 를 할 수 있고, EIP 가 생겨야 등록할 A 레코드가 생긴다.

| # | 무엇 | 누가 | 비고 |
| --- | --- | --- | --- |
| 1 | `cd prod/terraform/bootstrap && terraform apply` | 사람 · 로컬 | 이 시점에는 backend 없이 로컬 state |
| 2 | `terraform init -migrate-state -backend-config="bucket=..."` | 사람 · 로컬 | 로컬 → S3. 확인 후 로컬 state 삭제 |
| 3 | Secrets · Variables 등록 | 사람 | 아래 표 |
| 4 | **메인 스택 apply** | **CI** | develop 에 머지 → `infra.yml` 이 승인 게이트를 거쳐 apply |
| 5 | dnszi 에 A 레코드 등록 (`api` → EIP) | 사람 | **4 에서 EIP 가 생긴 뒤에** 가능하다 |
| 6 | 첫 배포 | CI | Let's Encrypt 발급은 5 가 전파된 뒤에 성공한다 |

메인 스택을 손으로 `apply` 하지 않는다. 그 역할(`2jo-tf-apply`)의 신뢰 조건이 `environment:prod` 라서 **사람의 자격증명으로는 맡을 수 없다** — CI 가 승인을 받아야만 토큰이 나온다.

#### 4 단계 전에 있어야 하는 값

| 이름 | 종류 | 값 |
| --- | --- | --- |
| `TF_STATE_BUCKET` | secret | 2 단계에서 쓴 버킷 이름 |
| `AWS_TF_PLAN_ROLE_ARN` · `AWS_TF_APPLY_ROLE_ARN` | secret | bootstrap 출력 |
| `SSH_KEY_NAME` | **variable** | 사람 접속용 AWS 키페어 이름 |
| `DEPLOY_PUBLIC_KEY` | **variable** | 배포용 SSH 공개키 |

앞의 둘이 없으면 `terraform init` 이 `The attribute "bucket" is required` 로, 뒤의 둘이 없으면 `plan` 이 `No value for required variable` 로 죽는다. 그래서 `infra.yml` 의 게이트가 값이 없으면 잡을 아예 건너뛴다 — 첫 apply 전까지 모든 인프라 PR 이 빨간불이 되는 것을 막는다.

공개키와 키페어 이름을 secret 이 아니라 variable 로 두는 이유: 감출 값이 아니고, secret 이면 마스킹돼서 `terraform plan` diff 가 `***` 로 나와 읽을 수 없다.

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
- 시크릿은 저장소에 두지 않는다. GitHub Secrets → 배포 워크플로가 SSH stdin 으로 → `/opt/2jo/.env` (600).
- **예약 작업은 cron 이 아니라 systemd user timer 다.** AL2023 에는 cronie 가 기본 설치되지 않고, `deploy.sh` 를 sudo 없이 돌리기로 한 결정과도 맞는다. cloud-init 은 `enable-linger` 만 켜고, 유닛 설치는 배포가 한다 — `user_data` 는 고쳐도 다시 실행되지 않아 스케줄을 바꿀 수 없다.

### 백업 복원

> 서버는 자기 백업을 읽지 못한다. 인스턴스 프로파일에 `backup/*` **PutObject 만** 있고 `GetObject` 는 없다 — 서버가 침해돼도 과거 백업이 남게 한 의도적 설계다. 그래서 복원은 **사람이 별도 자격증명으로** 한다.

| # | 단계 |
| --- | --- |
| 1 | `aws s3 ls s3://<버킷>/backup/` 으로 대상 확인 |
| 2 | `aws s3 cp s3://<버킷>/backup/<파일> - \| gunzip > dump.sql` 로 로컬에 받는다 |
| 3 | `head -50 dump.sql` — 스키마 헤더가 보이는지 눈으로 확인 |
| 4 | 빈 DB 에 넣어본다: `docker compose exec -T postgres psql -U "$DB_USERNAME" -d <임시DB> < dump.sql` |
| 5 | 운영 DB 로 복원할 때만 기존 DB 를 내리고 진행한다 |

**한 번은 실제로 해봐야 한다.** 검증하지 않은 백업은 백업이 아니다 — 필요해지는 순간이 처음 시도하는 순간이면 안 된다.

### 백업이 도는지 확인

| 확인 | 명령 |
| --- | --- |
| 타이머가 붙어 있나 | `systemctl --user list-timers backup.timer` |
| 마지막 실행 결과 | `journalctl --user -u backup.service -n 50` |
| 지표가 올라갔나 | Prometheus 에서 `twojo_backup_last_success_timestamp_seconds` |

마지막 성공이 25시간을 넘으면 Grafana 알람이 발화한다. **지표가 아예 없어도 발화한다** — 타이머가 설치되지 않은 상태가 가장 위험한데 그때가 가장 조용하기 때문이다.

## 이 디렉터리 밖 관련 파일

| 경로 | 내용 |
| --- | --- |
| `backend/.dockerignore` | 빌드 컨텍스트 제외 목록 |
| `.github/workflows/deploy.yml` | 백엔드 CD |
| `.github/workflows/infra.yml` | terraform fmt · validate · tflint · checkov · Infracost · plan / apply |
| `.github/workflows/cost-report.yml` | 일일 누적 비용 리포트 + 한도 초과 시 EC2 정지 |
| `frontend/.env.production` | `VITE_API_BASE_URL` |
