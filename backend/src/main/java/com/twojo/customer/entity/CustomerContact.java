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
     *
     * <p>단, <b>담당자가 없는 고객사의 첫 담당자는 서비스가 대표로 지정한다</b>
     * (#107 설계 결정 1). 대표가 붙는 경로는 그 자동 지정과 {@code set-primary} 둘이다.
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
     * 담당자 수정 — <b>null로 온 필드는 바꾸지 않는다</b> (08 §B의 PATCH 주석).
     * 필드 하나만 골라 보내는 화면을 전제하므로, 안 보낸 값을 지우면 안 된다.
     * (고객사·상품·활동·할 일도 같은 규약이다)
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
     * <p><b>기존 대표 해제는 여기서 하지 않는다</b> — 다른 담당자를 조회해야 하므로 서비스의
     * 몫이다. 엔티티는 자기 플래그만 다룬다.
     *
     * <p><b>교체할 때 이 메서드와 {@code releasePrimary()}를 따로 호출하면 위험하다.</b>
     * 두 UPDATE 사이에 대표가 2명인 순간이 생기는데, JPA는 그 순서를 보장하지 않아
     * {@code uk_customer_contact_primary}(부분 유니크) 위반이 날 수 있다.
     *
     * <p><b>#107에서 옛 대표를 먼저 확정하는 쪽으로 정했다</b> — {@code CustomerService.setPrimaryContact}가
     * 해제를 {@code saveAndFlush}로 먼저 반영한 뒤 새 대표를 지정한다. 한 문장(bulk UPDATE)으로 끝내는
     * 방식은 쓰지 않는다: 부분 유니크는 인덱스라 {@code DEFERRABLE}이 불가능하고, 한 UPDATE 문 안에서도
     * 검사가 행 단위로 걸려 새 대표 행이 먼저 갱신되면 같은 위반이 난다.
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
