package com.twojo.auth.service;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 대조 — 자격이 없는 경로에서도 BCrypt를 정확히 한 번 돌린다 (SC-09).
 *
 * <p>계정이 없을 때 대조를 건너뛰면 응답이 빨리 돌아와, 내용이 같아도 걸린 시간만으로
 * 그 이메일의 계정 존재가 드러난다. 구성원과 관리자가 같은 방어를 쓰므로 한 곳에 둔다.
 */
@Component
public class PasswordMatcher {

    private final PasswordEncoder passwordEncoder;

    /**
     * 대조할 상대가 없을 때 쓰는 해시 — 랜덤 값이라 어떤 비밀번호와도 맞지 않는다.
     * 하는 일은 같은 시간을 쓰는 것뿐이다.
     *
     * <p>상수로 박지 않고 기동 때마다 만든다. BCrypt는 계산 강도를 해시 문자열 안에 적어두므로,
     * 나중에 인코더 강도를 올리면 상수만 옛 강도로 남아 시간 차이가 되살아난다.
     */
    private final String dummyHash;

    public PasswordMatcher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * hash가 null이면 더미와 대조한다 — 결과는 언제나 false이고 걸린 시간만 같아진다.
     * 비밀번호를 설정하지 않은 계정과 비활성 계정도 호출자가 null을 넘겨 이 경로를 태운다.
     */
    public boolean matches(String rawPassword, String hash) {
        return passwordEncoder.matches(rawPassword, hash == null ? dummyHash : hash);
    }
}
