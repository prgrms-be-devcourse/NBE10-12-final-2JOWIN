# bootstrap

상태를 저장할 곳과, 누가 terraform 을 돌릴 수 있는가만 만든다.

| | bootstrap | 나머지 스택 |
| --- | --- | --- |
| 실행 주체 | **사람이 로컬에서** | GitHub Actions |
| 실행 횟수 | **1회** | 변경마다 |
| 상태 위치 | 자기가 만든 버킷 `bootstrap/` | 같은 버킷 `prod/` |
| destroy | **금지** (`prevent_destroy`) | 가능 |

CI 에서 못 도는 이유 — **CI 가 쓸 역할을 지금 만드는 중**이라 아직 CI 에 권한이 없다.

## 만드는 것

| 리소스 | 이름 | 비고 |
| --- | --- | --- |
| S3 상태 버킷 | `2jo-tfstate-<계정ID>` | 버저닝 · SSE-S3 · 퍼블릭 차단 4종 · non-TLS Deny · 이전 버전 90일 |
| ~~GitHub OIDC provider~~ | `token.actions.githubusercontent.com` | **만들지 않고 참조만 한다** — 아래 |
| IAM 역할 | `2jo-tf-plan` | PR · `ReadOnlyAccess` + `.tflock` 쓰기 |
| IAM 역할 | `2jo-tf-apply` | `environment:infra-apply` · `AdministratorAccess` |
| IAM 역할 | `2jo-gha-deploy` | `environment:backend-deploy` · ECR push + 배포 중 22번 임시 개방 |

### OIDC 공급자를 만들지 않는 이유

이 계정은 여러 팀이 공유하고, 같은 issuer 의 공급자는 **계정당 하나만** 존재할 수 있다. 이미 다른 팀이 만들어 두었다(2026-02-06 생성).

`import` 해서 우리 상태에 넣으면 `terraform destroy` 한 번에 **다른 팀들의 OIDC 가 전부 끊긴다.** `data` 소스로 참조만 하면 그 사고가 구조적으로 불가능하다.

대가는 반대 방향이다 — 다른 팀이 그 공급자를 지우면 우리 배포가 멈춘다. 막을 방법은 없고 증상은 `AssumeRoleWithWebIdentity` 실패로 나타난다.

기존 공급자가 우리 요구와 맞는지 확인했다: `client_id_list` 가 `sts.amazonaws.com` 하나다. 지문은 다르지만 AWS 는 2023년부터 이 issuer 의 지문을 검증하지 않는다.

## 실행 순서

| # | 명령 | 상태 위치 |
| --- | --- | --- |
| 0 | `aws configure --profile 2jo` → `export AWS_PROFILE=2jo` | — |
| 1 | `terraform init -backend=false` | 로컬. **`-backend=false` 가 필수다** — 아래 |
| 2 | `terraform plan` → 검토 → `terraform apply` | 로컬 |
| 3 | (`backend.tf` 는 이미 레포에 있다) | — |
| 4 | `terraform init -migrate-state -backend-config="bucket=2jo-tfstate-<계정ID>"` → `yes` | **로컬 → S3** |
| 5 | `rm terraform.tfstate*` | 이관 확인 후 |

### 1 단계에 `-backend=false` 가 필요한 이유

`backend.tf` 가 레포에 있는데 버킷은 아직 없다. 그냥 `terraform init` 을 하면 S3 백엔드를 초기화하려다 죽는다.

```
Error: Missing Required Value
The attribute "bucket" is required by the backend.
```

`-backend=false` 는 백엔드 초기화를 건너뛰고 로컬 상태로 시작하게 한다. 버킷을 만든 뒤 4 단계에서 그 상태를 옮긴다.

> 닭이 먼저인 구조다. 상태를 둘 곳을 만드는 스택이라, 자기 상태는 잠시 로컬에 있을 수밖에 없다.

### 버킷 이름을 코드에 박지 않는 이유

버킷 이름에 **계정 ID 가 들어간다**(`2jo-tfstate-<계정ID>`). 공유 계정의 ID 를 레포에 남기지 않으려고 `backend.tf` 에서 `bucket` 만 비워두고 `init` 할 때 주입한다.

메인 스택도 같은 방식이고, CI 는 같은 값을 `TF_STATE_BUCKET` 시크릿에서 넣는다.

> `bucket` 을 빠뜨리면 `init` 이 `The attribute "bucket" is required by the backend.` 로 죽는다. plan·apply 의 첫 줄이라 바로 드러난다.

## 검증

| # | 확인 | 명령 | 기대 |
| --- | --- | --- | --- |
| 1 | 상태 이관됨 | `aws s3 ls s3://2jo-tfstate-<계정ID>/bootstrap/` | `terraform.tfstate` 존재 |
| 2 | 로컬 상태 없음 | `ls terraform.tfstate` | 없음 |
| 3 | 재init 정상 | `terraform init -backend-config="bucket=..." && terraform plan` | `No changes` |
| 4 | 퍼블릭 차단 | `aws s3api get-public-access-block --bucket 2jo-tfstate-<계정ID>` | 4종 전부 `true` |
| 5 | 삭제 방어 | `terraform plan -destroy` (**apply 금지**) | `prevent_destroy` 오류 |
| 6 | OIDC 참조됨 | `terraform state list \| grep -v ^data\.` | **OIDC 가 목록에 없어야 한다** |
| 7 | 신뢰 조건 | `aws iam get-role --role-name 2jo-tf-apply` | `sub` 에 `environment:infra-apply` |
| 8 | 배포 역할 범위 | `2jo-gha-deploy` 로 `ec2:RunInstances` 시도 | `AccessDenied` |

## 사전 확인

발급받은 IAM 사용자에게 아래가 없으면 `AccessDenied` 로 멈춘다.

| 서비스 | 액션 |
| --- | --- |
| S3 | `CreateBucket` · `PutBucketVersioning` · `PutBucketEncryption` · `PutPublicAccessBlock` · `PutBucketPolicy` · `PutLifecycleConfiguration` |
| IAM | `CreateRole` · `CreatePolicy` · `PutRolePolicy` · `AttachRolePolicy` · `TagRole` · `GetOpenIDConnectProvider`(읽기) |
| STS | `GetCallerIdentity` |

```bash
aws sts get-caller-identity
aws iam list-attached-user-policies --user-name <사용자명>
```

## 규칙

| 규칙 | 이유 |
| --- | --- |
| **시크릿을 terraform 으로 만들지 않는다** | tfstate 는 평문이고 `tf-plan` 역할이 읽을 수 있다. DB 비밀번호·JWT 키는 GitHub Secrets 에 두고 배포 워크플로가 `.env` 로 넣는다 |
| 계정 ID 를 변수로 받지 않는다 | 자격증명에서 읽는다 — 잘못된 계정에 apply 하는 사고를 막는다 |
| `.terraform.lock.hcl` 은 커밋한다 | 프로바이더 버전을 팀이 공유해야 한다 |
