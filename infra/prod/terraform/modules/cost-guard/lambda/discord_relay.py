"""SNS -> Discord 브리지.

이 함수 하나만 Lambda 로 남는다. SNS 는 구독자에게 밀어넣는 구조라 AWS 안에
받는 쪽이 있어야 하는데, AWS Chatbot 은 Discord 를 지원하지 않고 SNS 가 웹훅으로
직접 POST 하는 것도 형식이 안 맞는다 (구독 확인 절차부터 실패한다).

설계 근거: cost-guard.md §4.4
"""

import json
import os
import urllib.request

import boto3

# cost-guard.md §6.4 임베드 색상
COLOR_WARN = 0xFEE75C  # 노랑 — 60/80% 실측 도달
COLOR_CRIT = 0xED4245  # 빨강 — 예측 초과

_ssm = boto3.client("ssm")
_webhook_cache = None


def _webhook_url():
    """웹훅 URL 을 SSM SecureString 에서 읽는다.

    terraform 으로 만들지 않는 이유: tfstate 는 평문이고 tf-plan 역할이 읽을 수
    있다. 사람이 넣어둔 값을 런타임에 가져온다. 컨테이너가 살아 있는 동안
    캐시해서 호출당 SSM 요청을 줄인다.
    """
    global _webhook_cache
    if _webhook_cache is None:
        _webhook_cache = _ssm.get_parameter(
            Name=os.environ["WEBHOOK_SSM_PATH"], WithDecryption=True
        )["Parameter"]["Value"]
    return _webhook_cache


def _post(payload):
    request = urllib.request.Request(
        _webhook_url(),
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:  # noqa: S310
        response.read()


def handler(event, _context):
    records = event.get("Records", [])
    for record in records:
        sns = record.get("Sns", {})
        subject = sns.get("Subject") or "AWS 비용 알림"
        message = sns.get("Message", "")

        # 예측 초과는 실측 경고보다 급하다 — 색으로 구분한다.
        color = COLOR_CRIT if "FORECAST" in message.upper() else COLOR_WARN

        _post(
            {
                "embeds": [
                    {
                        # Discord 제한: title 256자 · description 4096자
                        "title": subject[:256],
                        "description": message[:4000],
                        "color": color,
                    }
                ]
            }
        )

    return {"delivered": len(records)}
