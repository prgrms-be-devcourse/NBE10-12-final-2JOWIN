package com.twojo.customer.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사 담당자 — 대표 1명 부분 유니크 (CU-11). company_id 없음(부모 경유 격리).
 * 발송 이력 있으면 삭제 불가 (CU-14 — ViewTokenQuery.existsForContact 경유 판정).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerContact extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;

    private String name;

    private String title;

    private String email;

    private String phone;

    private boolean isPrimary;

    /**
     * 담당자 등록 (CU-09·10). 대표 여부는 여기서 정하지 않는다 —
     * 별도 엔드포인트 {@code POST .../contacts/{cid}/set-primary}가 담당한다 (CU-11).
     */
    public static CustomerContact create(UUID customerId, String name, String title,
                                         String phone, String email) {
        CustomerContact contact = new CustomerContact();
        contact.customerId = Objects.requireNonNull(customerId, "customerId");
        contact.name = Objects.requireNonNull(name, "name");
        contact.title = title;
        contact.phone = phone;
        contact.email = Objects.requireNonNull(email, "email");
        return contact;
    }

    /**
     * 담당자 수정 — <b>null로 온 필드는 바꾸지 않는다</b> (08 §B의 PATCH 주석 · 11 §1.3).
     * 필드 하나만 골라 보내는 화면을 전제하므로, 안 보낸 값을 지우면 안 된다.
     * (고객사·상품은 반대로 전체 대입이다 — 수정 폼이 기존 값을 채워 전부 보낸다)
     */
    public void update(String name, String title, String phone, String email) {
        if (name != null) {
            this.name = name;
        }
        if (title != null) {
            this.title = title;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (email != null) {
            this.email = email;
        }
    }

    /**
     * 대표 담당자로 지정 (CU-11).
     *
     * <p><b>기존 대표 해제는 여기서 하지 않는다</b> — 다른 담당자를 조회해야 하므로 서비스가
     * {@code releasePrimary()} → {@code markPrimary()} 순으로 오케스트레이션한다.
     * 엔티티는 자기 플래그만 다룬다.
     */
    public void markPrimary() {
        this.isPrimary = true;
    }

    /**
     * 대표 지정 해제 — <b>서비스의 대표 교체 안에서만 쓴다.</b>
     * 해제만 하는 엔드포인트는 없다 (08 §B — 대표 0명을 막기 위해서다).
     */
    public void releasePrimary() {
        this.isPrimary = false;
    }
}
