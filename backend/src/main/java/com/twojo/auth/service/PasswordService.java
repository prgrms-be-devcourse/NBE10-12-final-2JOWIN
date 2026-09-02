package com.twojo.auth.service;

import com.twojo.auth.SessionRevoker;
import com.twojo.auth.dto.ChangePasswordRequest;
import com.twojo.boundary.MemberCommand;
import com.twojo.boundary.MemberQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경 (AU-04).
 *
 * <p>교체와 세션 폐기를 한 트랜잭션으로 묶는다 — 05 §9가 둘을 한 전이의 원인과 효과로
 * 규정하므로, 갈라지면 "비밀번호는 바뀌었는데 옛 세션이 살아 있는" 상태가 생긴다.
 *
 * <p>member 테이블은 MemberCommand로만 건드린다. auth가 member를 직접 참조하면
 * SessionRevoker(member -> auth)와 맞물려 모듈 순환이 된다 (11 §7.3).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final MemberQuery memberQuery;
    private final MemberCommand memberCommand;
    private final SessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;

    /**
     * 현재 비밀번호를 확인하고 교체한 뒤, 그 구성원의 세션을 전부 끊는다 (AU-04 · 05 §9).
     *
     * <p>바꿀 대상은 요청이 아니라 access token에서 온다 — 09 "본인 것만".
     *
     * <p>같은 now를 두 곳에 넘긴다. password_changed_at이 "이 시각 이후 발급된 토큰만 유효"의
     * 기준이라(06), 폐기 시각과 어긋나면 나중에 판정할 수 없다.
     */
    public void change(UUID memberId, ChangePasswordRequest request, Instant now) {
        MemberQuery.AuthCredential credential = memberQuery.getCredential(memberId);

        // passwordHash가 NULL인 미설정 계정(Q-33)도 여기서 걸린다 — matches는 false를 돌려준다
        if (!passwordEncoder.matches(request.currentPassword(), credential.passwordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);   // 07 §A v1.6.5
        }

        memberCommand.changePassword(memberId, passwordEncoder.encode(request.newPassword()), now);
        sessionRevoker.revokeOnPasswordChange(memberId, now);
    }
}
