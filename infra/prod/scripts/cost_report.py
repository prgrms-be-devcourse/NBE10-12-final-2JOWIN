#!/usr/bin/env python3
"""일일 누적 비용 리포트 + 백스톱 정지.

이 스크립트가 비용 설계의 최종 방어선이다.

AWS Budgets 는 달력 월 기준이라 매월 1일에 리셋된다. 프로젝트가 9~10월을
걸치므로 Budgets 만으로는 총액을 지킬 수 없다. 여기서 PROJECT_START_DATE 부터
누적해서 재고, 한도를 넘으면 직접 인스턴스를 멈춘다.

계정이 Organizations 멤버라 Budget Action 을 못 쓰는 경우 이 스크립트가
계층 ③ 전체를 대신한다.

설계 근거: cost-guard.md §4.3 · §5.3
"""

from __future__ import annotations

import datetime as dt
import json
import os
import sys
import urllib.request

import boto3

# cost-guard.md §6.4 임베드 색상
COLOR_REPORT = 0x5865F2  # 파랑 — 평상시 일일 리포트
COLOR_WARN = 0xFEE75C  # 노랑 — 90% 도달
COLOR_BLOCK = 0x992D22  # 진빨강 — 차단 발생

TOP_SERVICES = 3
WARN_RATIO = 0.9


def _env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None:
        sys.exit(f"환경변수 {name} 이(가) 없습니다.")
    return value


def post_discord(webhook: str, title: str, description: str, color: int) -> None:
    payload = {"embeds": [{"title": title[:256], "description": description[:4000], "color": color}]}
    request = urllib.request.Request(
        webhook,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:  # noqa: S310
        response.read()


def fetch_costs(start: dt.date, end: dt.date) -> tuple[list[tuple[dt.date, float]], dict[str, float]]:
    """[시작, 끝) 구간의 일별 합계와 서비스별 합계를 돌려준다.

    Cost Explorer 는 us-east-1 전용이고, End 는 배타적이다.
    """
    client = boto3.client("ce", region_name="us-east-1")

    daily: list[tuple[dt.date, float]] = []
    by_service: dict[str, float] = {}
    token = None

    while True:
        kwargs = {
            "TimePeriod": {"Start": start.isoformat(), "End": end.isoformat()},
            "Granularity": "DAILY",
            "Metrics": ["UnblendedCost"],
            "GroupBy": [{"Type": "DIMENSION", "Key": "SERVICE"}],
        }
        if token:
            kwargs["NextPageToken"] = token

        response = client.get_cost_and_usage(**kwargs)

        for bucket in response["ResultsByTime"]:
            day = dt.date.fromisoformat(bucket["TimePeriod"]["Start"])
            day_total = 0.0
            for group in bucket["Groups"]:
                service = group["Keys"][0]
                amount = float(group["Metrics"]["UnblendedCost"]["Amount"])
                day_total += amount
                by_service[service] = by_service.get(service, 0.0) + amount
            daily.append((day, day_total))

        token = response.get("NextPageToken")
        if not token:
            break

    return daily, by_service


def stop_project_instances(region: str, project: str) -> list[str]:
    """Project 태그가 붙은 실행 중 인스턴스를 멈춘다.

    설계 문서는 인스턴스 ID 목록을 받게 되어 있었지만, 태그로 찾는 쪽으로 바꿨다.
    2jo-gha-cost 역할의 ec2:StopInstances 권한이 이미 ec2:ResourceTag/Project
    조건으로 잠겨 있어서, ID 를 넘겨도 결국 같은 집합만 멈출 수 있다.
    태그로 찾으면 compute 가 재생성돼 ID 가 바뀌어도 따라간다.
    """
    ec2 = boto3.client("ec2", region_name=region)
    reservations = ec2.describe_instances(
        Filters=[
            {"Name": "tag:Project", "Values": [project]},
            {"Name": "instance-state-name", "Values": ["running"]},
        ]
    )["Reservations"]

    ids = [i["InstanceId"] for r in reservations for i in r["Instances"]]
    if ids:
        ec2.stop_instances(InstanceIds=ids)
    return ids


def main() -> int:
    webhook = _env("DISCORD_WEBHOOK_URL")
    start = dt.date.fromisoformat(_env("PROJECT_START_DATE"))
    limit = float(_env("BUDGET_LIMIT_USD", "50"))
    region = _env("AWS_REGION", "ap-northeast-2")
    project = _env("PROJECT_TAG", "2jo")

    today = dt.datetime.now(dt.timezone.utc).date()
    if start >= today:
        sys.exit(f"PROJECT_START_DATE({start})가 오늘({today}) 이후입니다.")

    # End 는 배타적이다. today 로 주면 어제까지의 확정 데이터가 들어온다.
    daily, by_service = fetch_costs(start, today)

    total = sum(amount for _, amount in daily)
    days = max(len(daily), 1)
    yesterday_cost = daily[-1][1] if daily else 0.0
    daily_avg = total / days
    remaining = limit - total
    used_pct = (total / limit * 100) if limit else 0.0

    if daily_avg > 0 and remaining > 0:
        exhaust = today + dt.timedelta(days=int(remaining / daily_avg))
        exhaust_text = exhaust.isoformat()
    elif remaining <= 0:
        exhaust_text = "이미 초과"
    else:
        exhaust_text = "산출 불가 (사용량 없음)"

    top = sorted(by_service.items(), key=lambda kv: kv[1], reverse=True)[:TOP_SERVICES]
    top_text = " · ".join(f"{name} ${amount:.2f}" for name, amount in top) or "없음"

    body = "\n".join(
        [
            "```",
            f"전일       ${yesterday_cost:>7.2f}",
            f"누적       ${total:>7.2f}   {used_pct:.0f}%",
            f"잔여       ${remaining:>7.2f}   (한도 ${limit:.0f})",
            f"일평균     ${daily_avg:>7.2f}",
            f"예상 소진  {exhaust_text}",
            f"상위       {top_text}",
            "```",
        ]
    )

    title = f"2JO 인프라 비용 · {today.isoformat()} ({days}일차)"
    post_discord(webhook, title, body, COLOR_REPORT)

    # ── 백스톱 ────────────────────────────────────────────────────────────
    # 자동 재개는 만들지 않는다. 원인을 모른 채 다시 켜지면 같은 속도로 또 소진된다.
    if total >= limit:
        stopped = stop_project_instances(region, project)
        detail = ", ".join(stopped) if stopped else "정지할 실행 중 인스턴스 없음"
        post_discord(
            webhook,
            "한도 초과 — EC2 정지됨",
            f"누적 ${total:.2f} / 한도 ${limit:.0f}\n정지: {detail}\n"
            "재개는 수동입니다. 원인 확인 후 start-instances 하세요.",
            COLOR_BLOCK,
        )
        print(f"한도 초과: ${total:.2f} >= ${limit:.0f} · 정지 {stopped}")
        return 0

    if total >= limit * WARN_RATIO:
        post_discord(
            webhook,
            f"비용 {used_pct:.0f}% 도달",
            f"누적 ${total:.2f} / 한도 ${limit:.0f}\n현재 페이스면 {exhaust_text} 소진 예상",
            COLOR_WARN,
        )

    print(f"누적 ${total:.2f} / 한도 ${limit:.0f} ({used_pct:.0f}%)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
