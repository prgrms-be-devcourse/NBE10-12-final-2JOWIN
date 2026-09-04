package com.twojo.onboarding.service;

import com.twojo.boundary.PlatformAdminQuery;
import com.twojo.onboarding.entity.PlatformAdmin;
import com.twojo.onboarding.repository.PlatformAdminRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlatformAdminQuery 구현 (AU-08) — onboarding이 관리자 계정을 밖에 내보이는 유일한 경로.
 *
 * <p>엔티티를 그대로 반환하지 않고 record로 바꿔 내보낸다.
 * 엔티티가 새면 호출하는 모듈이 이 패키지의 내부 구조에 묶인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAdminQueryService implements PlatformAdminQuery {

    private final PlatformAdminRepository platformAdminRepository;

    /**
     * 정규화를 여기서 한다 — 호출자에게 맡기면 한 곳만 빠뜨려도
     * 조회가 조용히 실패하고 원인을 찾기 어렵다.
     */
    @Override
    public Optional<AdminCredential> findCredentialByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return platformAdminRepository.findByEmailIgnoreCase(email.trim())
                .map(this::toCredential);
    }

    @Override
    public boolean isActive(UUID platformAdminId) {
        return platformAdminRepository.findById(platformAdminId)
                .map(PlatformAdmin::isActive)
                .orElse(false);
    }

    private AdminCredential toCredential(PlatformAdmin admin) {
        return new AdminCredential(
                admin.getId(), admin.getEmail(), admin.getPasswordHash(), admin.isActive());
    }
}
