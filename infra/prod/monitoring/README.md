# 모니터링 스택

전부 **셀프 호스팅**이다. 로그에 영업 데이터가 흐르므로 모니터링 스택도 SC-01 대상으로 본다 (docs/14 §2-11). Grafana Cloud 같은 외부 SaaS 이관은 **§2-11 개정과 팀 합의가 선행**되어야 한다.

## 구성

| 컨테이너 | 역할 | mem_limit |
| --- | --- | --- |
| Prometheus | 지표 수집 · 보존 7일 | 384m |
| Loki | 로그 저장 · 보존 168h | 256m |
| Promtail | 도커 로그 → Loki | 96m |
| Grafana | 대시보드 + **알람 엔진** | 256m |
| postgres_exporter | DB 지표 | 64m |
| node_exporter | 호스트 지표 | 32m |

기동: `docker compose -f compose.yml -f compose.monitoring.yml up -d`

**공개망에 노출되지 않는다.** 호스트에 게시되는 포트는 Caddy 의 80·443 뿐이고, Grafana 를 보려면 SSM 포트 포워딩으로 터널을 뚫는다.

## 스크레이프 대상 5종

| job | 대상 | 경로 |
| --- | --- | --- |
| `prometheus` | `localhost:9090` | `/metrics` |
| `node` | `node-exporter:9100` | `/metrics` |
| `caddy` | `caddy:2019` | `/metrics` |
| `backend` | `backend:8080` | `/actuator/prometheus` |
| `postgres` | `postgres-exporter:9187` | `/metrics` |

엣지(Caddy) 지표를 따로 두는 이유 — **앱이 죽으면 `http_server_requests` 자체가 안 나온다.** "요청이 0"으로 보여 장애를 놓치는데, Caddy 는 그때도 502 를 센다.

## 알람 6개

Grafana Unified Alerting 이 평가와 발송을 모두 한다. Alertmanager 를 쓰지 않아 컨테이너가 하나 줄었다.

| # | 알람 | 조건 |
| --- | --- | --- |
| 1 | 타깃 다운 | `up < 1` · 2분 |
| 2 | 디스크 80% | 루트 사용률 > 0.8 · 10분 |
| 3 | 메모리 부족 | 여유 < 10% · 5분 |
| 4 | 5xx 급증 | Caddy 5xx > 1/s · 5분 |
| 5 | 커넥션 풀 대기 | `hikaricp_connections_pending > 0` · 3분 |
| 6 | 반복 재시작 | `changes(up[10m]) > 3` |

**6개를 넘기지 않는다.** 울려도 아무도 안 보게 되면 0개와 같다. ERROR 로그는 알람이 아니라 패널이다 — 개발 중에는 상시 발생해서 걸어두면 첫날부터 무시하게 된다.

> Grafana 가 죽으면 알람도 죽는다. Grafana 자신의 장애는 Grafana 가 못 알린다.
> 매일 09:00 오는 비용 리포트가 없으면 그걸 간접 신호로 쓴다.

## 반드시 지킬 것 — 카디널리티

지표 **개수**가 아니라 **라벨 조합 수**가 메모리를 먹는다.

| 대상 | 규칙 |
| --- | --- |
| Prometheus | 응답시간 히스토그램을 켜지 않는다. 켜면 시계열이 **30배**(~250 → ~7,500) |
| Loki | 라벨은 `container_id` · `level` · `env` 만. `traceId`·`userId` 를 넣으면 스트림이 무한히 는다 |

그런 값은 **로그 본문에 두고 LogQL 로 검색**한다.

확인 명령 (배포 직후 한 번, 1주 뒤 한 번):

| 항목 | 쿼리 | 기대 |
| --- | --- | --- |
| 시계열 수 | `prometheus_tsdb_head_series` | 10,000 미만 |
| Loki 스트림 | `loki_ingester_memory_streams` | 수십 수준 |
| 히스토그램 미활성 | `http_server_requests_seconds_bucket` | **결과 없음** |

## 대시보드

바닥부터 만들지 않는다. 커뮤니티 대시보드를 import 한 뒤 필요 없는 패널을 지우고 **export 해서 `grafana/provisioning/dashboards/json/` 에 커밋**한다.

| 대상 | Grafana ID |
| --- | --- |
| node_exporter | 1860 |
| postgres_exporter | 9628 |
| JVM (Micrometer) | 4701 |

## 필요한 SSM 파라미터

값은 terraform 으로 만들지 않는다. `fetch-secrets.sh` 가 파라미터 이름의 마지막 조각을 환경변수 키로 바꾼다.

| SSM 경로 | 환경변수 | 쓰는 곳 |
| --- | --- | --- |
| `/2jo/prod/discord-webhook-url` | `DISCORD_WEBHOOK_URL` | Grafana 연락 지점 |
| `/2jo/prod/grafana-admin-password` | `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 |

## 다음 단계 (팀 논의 후)

1단계는 **서버 운영 공통 지표**만 다룬다. 도메인 지표(docs/14 §1.5)는 합의 뒤에 붙인다.

| 지표 | 수집 방법 |
| --- | --- |
| 메일 발송 실패율 | postgres_exporter 커스텀 쿼리 — `email_log` status 별 count |
| LOGIN_LOCKED 발생 | Loki LogQL `count_over_time` |
| STALE_VERSION 빈도 | `http_server_requests_seconds_count{status="409"}` |
| 배치 지연 | `spring_scheduled_tasks_seconds_*` |

**백엔드 계측 없이 전부 수집 가능하다.** 커스텀 쿼리 파일과 알람 임계만 추가하면 된다.
