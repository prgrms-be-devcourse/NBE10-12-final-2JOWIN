# 모니터링 스택 (3주차, E) — Prometheus · Loki · Grafana · Promtail

docs/14-tech-stack.md §1.5 · §3-8 기준. 배치 위치 잠정 기본값: **AWS 단일 EC2 docker-compose 셀프 호스팅**.

| 구성 | 역할 |
| --- | --- |
| Prometheus | 메트릭 수집 — 백엔드 `/actuator/prometheus` (micrometer-registry-prometheus 적용 완료) |
| Promtail | 로그 수집 에이전트 — 백엔드 JSON 로그 → Loki 전송 |
| Loki | 로그 저장소 |
| Grafana | 대시보드·알람 |

프로젝트 특화 지표 (14 §1.5): 메일 발송 실패율(email_log FAILED — NT-13 감지 유일 경로) · LOGIN_LOCKED 발생 · 배치 지연 · STALE_VERSION 빈도.

⚠️ 전 구성 **공개망 노출 금지** — 내부망/VPN만 (14 §2-6·11).

여기에 docker-compose.monitoring.yml + promtail/prometheus 설정을 추가한다 (3주차).
