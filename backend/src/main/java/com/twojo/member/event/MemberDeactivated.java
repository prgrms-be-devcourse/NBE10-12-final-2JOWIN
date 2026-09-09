package com.twojo.member.event;

import com.twojo.boundary.AuditActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 구성원 비활성화 (MB-09) — 관리자가 실제로 상태를 바꾼 순간에만 발행된다.
 *
 * <p>발생형이라 {@code changes}에 해당하는 필드가 없다. 어느 상태에서 어디로 갔는지가 이벤트 이름에
 * 다 들어 있어 before/after를 실을 이유가 없다.
 *
 * <p>{@code transferToMemberId}는 <b>실제로 담당 Deal이 넘어갔을 때만</b> 값이 온다. 요청에 대상이
 * 실려 와도 넘길 진행 중 Deal이 없으면 이관 자체가 일어나지 않으므로, 그때 값을 실으면 아무것도
 * 넘어가지 않았는데 넘겼다는 기록이 남는다. 값이 {@code null}이면 "대상을 안 보냈다"가 아니라
 * <b>"넘어간 Deal이 없다"</b>는 뜻이다.
 *
 * <p>{@code dealIds}는 Deal 하나하나가 아니라 <b>이 한 건에 묶어</b> 남기는 이관 내역이다 (Q-48).
 * 구독자가 {@code deal}을 읽지 못해, 여기 없으면 어느 Deal이 넘어갔는지 어디서도 복구되지 않는다.
 * 빈 목록일 수는 있어도 {@code null}일 수는 없다.
 *
 * @param memberId 비활성화된 구성원 — 감사 기록의 대상
 * @param actor 실행한 관리자
 * @param dealIds 실제로 넘어간 Deal id — 이관이 없었으면 빈 목록
 */
public record MemberDeactivated(
        UUID companyId,
        UUID memberId,
        AuditActor actor,
        Instant occurredAt,
        UUID transferToMemberId,
        List<UUID> dealIds) {

    /** 리스너가 별도 스레드에서 읽으므로 발행 시점에 복사해 고정한다. null이면 여기서 터진다. */
    public MemberDeactivated {
        dealIds = List.copyOf(dealIds);
    }
}
