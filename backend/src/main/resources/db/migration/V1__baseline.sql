-- =============================================================================
-- V1__baseline.sql — 테이블 25개 (ERD v1.6.3 = docs/06-erd.md가 직접 입력)
-- 작성 규칙:
--   · 전 테이블 UUID PK · created_at/updated_at TIMESTAMPTZ NOT NULL
--   · 금액 BIGINT 원 단위 정수 (Q-12) · 상태값 = 전이표 v1.6 영문 코드 CHECK
--   · 복합 FK: 부모가 둘 이상인 테이블은 (company_id, id)로 교차 테넌트 차단 (SC-01)
--   · 소프트 삭제: customer · deal · activity (deleted_at)
--   · 이 파일 이후의 스키마 변경은 소유자 번호대 — A=1xx · B=2xx · C=3xx · D=4xx
-- ERD와 다른 점(의도적): document_sequence.year_month는 char(4) 대신 varchar(4)
--   — Hibernate validate의 타입 대조(char↔varchar) 충돌 방지. 값 규약("2608")은 동일.
-- =============================================================================

-- ── 플랫폼 ───────────────────────────────────────────────────────────────────

-- 가입 신청 — 반려 이력 보존 (Q-15). business_no는 재신청 허용이라 유니크 금지
CREATE TABLE application (
    id            UUID PRIMARY KEY,
    company_name  VARCHAR(255) NOT NULL,
    business_no   VARCHAR(20)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reject_reason VARCHAR(500),
    decided_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 플랫폼 관리자 — 구성원과 별도 계정 (AU-08)
CREATE TABLE platform_admin (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── 테넌트 ───────────────────────────────────────────────────────────────────

-- 회사 — 신청서와 1:1 (application_id UNIQUE = 승인 멱등)
CREATE TABLE company (
    id             UUID PRIMARY KEY,
    application_id UUID         NOT NULL UNIQUE REFERENCES application (id),
    name           VARCHAR(255) NOT NULL,
    business_no    VARCHAR(20)  NOT NULL UNIQUE,  -- 사업자번호당 테넌트 1개 (전역). 회사 이름은 유니크 아님(동명 상호 합법)
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    suspend_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 구성원 — 삭제 없음, 비활성화만. password_hash NULL = 승인 직후 미설정 (Q-33)
CREATE TABLE member (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES company (id),
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(255),
    name                VARCHAR(100) NOT NULL,
    phone               VARCHAR(30),
    role                VARCHAR(20)  NOT NULL CHECK (role IN ('COMPANY_ADMIN', 'SALES_REP')),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    password_changed_at TIMESTAMPTZ,  -- AU-04·05 — 이 시각 이후 발급 토큰만 유효
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_company_id_id UNIQUE (company_id, id)  -- 복합 FK 참조 대상
);
CREATE UNIQUE INDEX uk_member_email_lower ON member (lower(email));  -- 전역 유일 (Q-14)

-- 초대 — 재발송 = EXPIRED(RESENT) 종결 + 새 행 (Q-31)
CREATE TABLE invitation (
    id                   UUID PRIMARY KEY,
    company_id           UUID         NOT NULL REFERENCES company (id),
    invited_by_member_id UUID         NOT NULL,
    email                VARCHAR(255) NOT NULL,
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('COMPANY_ADMIN', 'SALES_REP')),
    token_hash           VARCHAR(255) NOT NULL UNIQUE,  -- 원문 미저장
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELED', 'EXPIRED')),
    expires_at           TIMESTAMPTZ  NOT NULL,  -- 7일 (MB-04, Q-34)
    accepted_at          TIMESTAMPTZ,
    canceled_at          TIMESTAMPTZ,
    expired_at           TIMESTAMPTZ,
    expired_reason       VARCHAR(20) CHECK (expired_reason IN ('TIME', 'RESENT')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (company_id, invited_by_member_id) REFERENCES member (company_id, id)
);
-- 회사·이메일당 대기 초대 1개
CREATE UNIQUE INDEX uk_invitation_pending ON invitation (company_id, email) WHERE status = 'PENDING';

-- ── 인증 (테넌트 격리 예외 있음) ─────────────────────────────────────────────

-- 세션 원본 — "즉시 차단"의 실체. 다중 기기 허용이라 부분 유니크 없음 (Q-28)
CREATE TABLE refresh_token (
    id                UUID PRIMARY KEY,
    actor_type        VARCHAR(20)  NOT NULL CHECK (actor_type IN ('MEMBER', 'PLATFORM_ADMIN')),
    member_id         UUID REFERENCES member (id),
    platform_admin_id UUID REFERENCES platform_admin (id),
    token_hash        VARCHAR(255) NOT NULL UNIQUE,
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),  -- EXPIRED 상태 없음: expires_at 비교 판정
    revoked_reason    VARCHAR(30) CHECK (revoked_reason IN
                          ('ROTATED', 'LOGOUT', 'PASSWORD_CHANGED', 'MEMBER_DEACTIVATED', 'COMPANY_SUSPENDED', 'REUSE_DETECTED')),
    expires_at        TIMESTAMPTZ  NOT NULL,  -- 미유지 12h / 유지 14d (Q-32)
    last_used_at      TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_refresh_token_owner CHECK ((member_id IS NOT NULL) <> (platform_admin_id IS NOT NULL))  -- 정확히 하나
);
CREATE INDEX ix_refresh_token_member_status ON refresh_token (member_id, status);

-- 비밀번호 재설정 — RESET 30분 / INITIAL_SETUP 7일 (Q-33·34)
CREATE TABLE password_reset_token (
    id         UUID PRIMARY KEY,
    member_id  UUID         NOT NULL REFERENCES member (id),
    purpose    VARCHAR(20)  NOT NULL CHECK (purpose IN ('RESET', 'INITIAL_SETUP')),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED')),
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 구성원당 활성 재설정 토큰 1개
CREATE UNIQUE INDEX uk_password_reset_token_active ON password_reset_token (member_id) WHERE status = 'ACTIVE';

-- 로그인 시도 — 미가입 포함 기록 (SC-09) · company_id 없음(테넌트 격리 예외) · 30일 후 삭제 배치
CREATE TABLE login_attempt (
    id           UUID PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,  -- lower() 정규화는 앱 책임
    actor_type   VARCHAR(20)  NOT NULL CHECK (actor_type IN ('MEMBER', 'PLATFORM_ADMIN')),
    member_id    UUID REFERENCES member (id),  -- null = 미가입 또는 관리자
    success      BOOLEAN      NOT NULL,
    ip_address   VARCHAR(45),  -- IPv6
    attempted_at TIMESTAMPTZ  NOT NULL,  -- AU-09 판정
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_attempt_email_time ON login_attempt (email, attempted_at DESC);

-- ── 영업 ─────────────────────────────────────────────────────────────────────

-- 고객사 — 회사 공유 자원, 담당 개념 없음 (SC-03). created_by는 기록용
CREATE TABLE customer (
    id                   UUID PRIMARY KEY,
    company_id           UUID         NOT NULL REFERENCES company (id),
    created_by_member_id UUID         NOT NULL REFERENCES member (id),
    name                 VARCHAR(255) NOT NULL,
    industry             VARCHAR(100),
    size                 VARCHAR(50),
    note                 TEXT,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_customer_company_id_id UNIQUE (company_id, id)
);
CREATE INDEX ix_customer_company ON customer (company_id);

-- 고객사 담당자 — 대표 1명 부분 유니크 (CU-11). company_id 없음(부모 경유 격리)
CREATE TABLE customer_contact (
    id          UUID PRIMARY KEY,
    customer_id UUID         NOT NULL REFERENCES customer (id),
    name        VARCHAR(100) NOT NULL,
    title       VARCHAR(100),
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(30),
    is_primary  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_customer_contact_primary ON customer_contact (customer_id) WHERE is_primary;
CREATE INDEX ix_customer_contact_customer ON customer_contact (customer_id);

-- Deal — 고정 6단계 (Q-11) · assignee가 조회 범위의 축 (SC-02)
CREATE TABLE deal (
    id                 UUID PRIMARY KEY,
    company_id         UUID         NOT NULL REFERENCES company (id),
    customer_id        UUID         NOT NULL,
    assignee_member_id UUID         NOT NULL,
    title              VARCHAR(255) NOT NULL,
    stage              VARCHAR(20)  NOT NULL CHECK (stage IN ('LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION', 'WON', 'LOST')),
    expected_amount    BIGINT CHECK (expected_amount >= 0),  -- null 허용(미정, DL-02)
    due_date           DATE,
    lost_reason        VARCHAR(500),
    lost_from_stage    VARCHAR(20),  -- 재개용 (DL-12)
    version            INT          NOT NULL,  -- 낙관적 락
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_deal_company_id_id UNIQUE (company_id, id),
    CONSTRAINT fk_deal_customer FOREIGN KEY (company_id, customer_id) REFERENCES customer (company_id, id),
    CONSTRAINT fk_deal_assignee FOREIGN KEY (company_id, assignee_member_id) REFERENCES member (company_id, id)
);
CREATE INDEX ix_deal_company_assignee_stage ON deal (company_id, assignee_member_id, stage);
CREATE INDEX ix_deal_customer ON deal (customer_id);

-- ── 카탈로그 ─────────────────────────────────────────────────────────────────

-- 상품 — 이름 유일은 판매 중지 포함 (재등록 대신 판매 재개 사용)
CREATE TABLE product (
    id          UUID PRIMARY KEY,
    company_id  UUID         NOT NULL REFERENCES company (id),
    name        VARCHAR(255) NOT NULL,
    unit        VARCHAR(50)  NOT NULL,
    unit_price  BIGINT       NOT NULL CHECK (unit_price >= 0),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISCONTINUED')),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_company_name UNIQUE (company_id, name)
);

-- ── 견적 ─────────────────────────────────────────────────────────────────────

-- 견적 — 7상태 · 금액 3분리 서버 계산 · 발송 후 불변 (QT-16)
CREATE TABLE quote (
    id                   UUID PRIMARY KEY,
    company_id           UUID         NOT NULL REFERENCES company (id),
    deal_id              UUID         NOT NULL,
    quote_no             VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL CHECK (status IN
                             ('DRAFT', 'SENT', 'VIEWED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),
    vat_mode             VARCHAR(20)  NOT NULL DEFAULT 'EXCLUDED' CHECK (vat_mode IN ('EXCLUDED', 'INCLUDED')),  -- Q-16
    supply_amount        BIGINT       NOT NULL CHECK (supply_amount >= 0),
    vat_amount           BIGINT       NOT NULL CHECK (vat_amount >= 0),
    total_amount         BIGINT       NOT NULL CHECK (total_amount >= 0),
    valid_until          DATE         NOT NULL,  -- = 링크 만료 (Q-17)
    terms                TEXT,
    cloned_from_quote_id UUID REFERENCES quote (id),  -- 복제 계보 (Q-18) · QT-28 대체 이동
    sent_at              TIMESTAMPTZ,
    first_viewed_at      TIMESTAMPTZ,  -- AP-07
    responded_at         TIMESTAMPTZ,
    reject_reason        VARCHAR(500),  -- AP-10
    responder_name       VARCHAR(50),   -- AP-19 자기 신고 — 검증 없음 (Q-44). 응답 전 NULL
    responder_title      VARCHAR(50),
    version              INT          NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_quote_company_id_id UNIQUE (company_id, id),
    CONSTRAINT uk_quote_no UNIQUE (company_id, quote_no),  -- 채번 최종 방어선
    CONSTRAINT fk_quote_deal FOREIGN KEY (company_id, deal_id) REFERENCES deal (company_id, id)
);
CREATE INDEX ix_quote_deal ON quote (deal_id);
CREATE INDEX ix_quote_company_status ON quote (company_id, status);

-- 견적 항목 — 작성 시점 카탈로그 값 복사 (QT-24 · PR-04 변경 무영향)
CREATE TABLE quote_item (
    id                        UUID PRIMARY KEY,
    quote_id                  UUID         NOT NULL REFERENCES quote (id),
    product_id                UUID REFERENCES product (id),  -- null = 직접 입력 (QT-03)
    name                      VARCHAR(255) NOT NULL,  -- 값 복사
    unit                      VARCHAR(50)  NOT NULL,  -- 값 복사
    quantity                  INT          NOT NULL CHECK (quantity > 0),
    unit_price                BIGINT       NOT NULL CHECK (unit_price >= 0),  -- 0원 하한 (Q-02)
    amount                    BIGINT       NOT NULL CHECK (amount >= 0),
    catalog_price_at_creation BIGINT,  -- QT-24 · QT-29 확장 지점 (직접 입력이면 NULL)
    sort_order                INT          NOT NULL CHECK (sort_order >= 0),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_quote_item_quote ON quote_item (quote_id);

-- ── 고객 승인 (D 소유) ───────────────────────────────────────────────────────

-- 열람 링크 — 견적당 활성 1개 · 해시만 저장
CREATE TABLE quote_view_token (
    id                   UUID PRIMARY KEY,
    quote_id             UUID         NOT NULL REFERENCES quote (id),
    recipient_contact_id UUID         NOT NULL REFERENCES customer_contact (id),  -- 수신인 (AP-13)
    token_hash           VARCHAR(255) NOT NULL UNIQUE,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'RESPONDED', 'EXPIRED')),
    expired_reason       VARCHAR(20) CHECK (expired_reason IN ('TIME', 'MANUAL', 'WITHDRAWN', 'RESENT', 'DEAL_LOST')),
    expires_at           TIMESTAMPTZ  NOT NULL,  -- valid_until 당일 23:59:59 KST
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_quote_view_token_active ON quote_view_token (quote_id) WHERE status = 'ACTIVE';  -- AP-03
CREATE INDEX ix_quote_view_token_contact ON quote_view_token (recipient_contact_id);  -- CU-14 판정

-- 고객 문의 — 기록만 (Q-20), 답변 스레드 없음
CREATE TABLE customer_inquiry (
    id         UUID PRIMARY KEY,
    quote_id   UUID        NOT NULL REFERENCES quote (id),
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_inquiry_quote ON customer_inquiry (quote_id);

-- ── 주문 ─────────────────────────────────────────────────────────────────────

-- 주문 — 상태 없음 (Q-09) · 1견적 1주문 · deal은 quote 경유 조회
CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    company_id    UUID        NOT NULL REFERENCES company (id),
    quote_id      UUID        NOT NULL UNIQUE,  -- OD-03 최종 방어
    order_no      VARCHAR(30) NOT NULL,
    supply_amount BIGINT      NOT NULL CHECK (supply_amount >= 0),  -- 전환 시점 복사 (OD-04)
    vat_amount    BIGINT      NOT NULL CHECK (vat_amount >= 0),
    total_amount  BIGINT      NOT NULL CHECK (total_amount >= 0),
    start_date    DATE,
    delivery_date DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_order_no UNIQUE (company_id, order_no),
    CONSTRAINT fk_orders_quote FOREIGN KEY (company_id, quote_id) REFERENCES quote (company_id, id)
);

-- 주문 항목 — FK 없는 값 복사 스냅샷 (OD-04)
CREATE TABLE order_item (
    id         UUID PRIMARY KEY,
    order_id   UUID         NOT NULL REFERENCES orders (id),
    name       VARCHAR(255) NOT NULL,
    unit       VARCHAR(50)  NOT NULL,
    quantity   INT          NOT NULL CHECK (quantity > 0),
    unit_price BIGINT       NOT NULL CHECK (unit_price >= 0),
    amount     BIGINT       NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_item_order ON order_item (order_id);

-- ── 이력 ─────────────────────────────────────────────────────────────────────

-- 상담 기록 (수동, AC-01~05) — author는 수정·삭제 판정용, 조회 경로 아님
CREATE TABLE activity (
    id               UUID PRIMARY KEY,
    company_id       UUID        NOT NULL REFERENCES company (id),
    deal_id          UUID        NOT NULL,
    author_member_id UUID        NOT NULL,
    channel          VARCHAR(20) NOT NULL CHECK (channel IN ('CALL', 'MEETING', 'EMAIL')),  -- AC-02
    content          TEXT        NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,  -- AC-03
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_activity_deal   FOREIGN KEY (company_id, deal_id) REFERENCES deal (company_id, id),
    CONSTRAINT fk_activity_author FOREIGN KEY (company_id, author_member_id) REFERENCES member (company_id, id)
);
CREATE INDEX ix_activity_deal ON activity (deal_id);

-- 다음 할 일 — 배정 컬럼 없음 (Q-29): deal의 순수 자식, "내 할 일" = 담당 Deal의 미완료
CREATE TABLE task (
    id         UUID PRIMARY KEY,
    deal_id    UUID         NOT NULL REFERENCES deal (id),
    content    VARCHAR(500) NOT NULL,
    due_date   DATE         NOT NULL,  -- AC-09
    done_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_task_deal_done ON task (deal_id, done_at);

-- 감사 로그 — 자동 이벤트·변경 (AC-07·11). payload에 비밀번호·토큰·해시 금지 (앱 규약)
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    company_id  UUID        NOT NULL REFERENCES company (id),  -- 회사 불명 이벤트는 login_attempt 전담
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID        NOT NULL,
    event_type  VARCHAR(50) NOT NULL,  -- STAGE_MOVED 등 + 인증 6종
    actor_type  VARCHAR(20) NOT NULL CHECK (actor_type IN ('MEMBER', 'PLATFORM_ADMIN', 'CUSTOMER_LINK', 'SYSTEM')),
    actor_id    UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload     JSONB,  -- 변경된 필드만 before/after · 견적·주문 이벤트는 dealId 필수 (AC-06)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_company_time ON audit_log (company_id, occurred_at);

-- ── 공통 ─────────────────────────────────────────────────────────────────────

-- 표시 번호 채번 — SELECT ... FOR UPDATE → +1 (행 없으면 INSERT). APPLICATION은 제외 (v1.6)
CREATE TABLE document_sequence (
    id         UUID PRIMARY KEY,
    company_id UUID        NOT NULL REFERENCES company (id),
    doc_type   VARCHAR(10) NOT NULL CHECK (doc_type IN ('QUOTE', 'ORDER')),
    year_month VARCHAR(4)  NOT NULL,  -- 월별 리셋 (예: '2608')
    last_seq   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_document_sequence UNIQUE (company_id, doc_type, year_month)
);

-- ── 알림 ─────────────────────────────────────────────────────────────────────

-- 인앱 알림 — 복합 FK로 교차 테넌트 유출 차단 · 읽은 알림 90일 후 삭제 배치
CREATE TABLE notification (
    id                  UUID PRIMARY KEY,
    company_id          UUID         NOT NULL REFERENCES company (id),
    recipient_member_id UUID         NOT NULL,
    type                VARCHAR(30)  NOT NULL CHECK (type IN
                            ('QUOTE_VIEWED', 'QUOTE_APPROVED', 'QUOTE_REJECTED', 'REMIND_NO_RESPONSE',
                             'INQUIRY_RECEIVED', 'EMAIL_FAILED')),  -- NT-03~05·10·12
    message             VARCHAR(500) NOT NULL,
    ref_type            VARCHAR(50),
    ref_id              UUID,
    read_at             TIMESTAMPTZ,  -- null = 안 읽음
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (company_id, recipient_member_id) REFERENCES member (company_id, id)
);
CREATE INDEX ix_notification_recipient_read ON notification (recipient_member_id, read_at);

-- 메일 발송 기록 — UNIQUE가 배치 재실행 이중 발송을 DB에서 차단 (NT-05·06)
CREATE TABLE email_log (
    id              UUID PRIMARY KEY,
    company_id      UUID REFERENCES company (id),  -- 플랫폼 발송(NT-13)은 null
    template_type   VARCHAR(30)  NOT NULL,  -- NT-01~06·10·13
    recipient_email VARCHAR(255) NOT NULL,
    ref_type        VARCHAR(50),
    ref_id          UUID,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED')),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_email_log_dedup UNIQUE (template_type, ref_id, recipient_email)
);

-- 구성원별 메일 수신 설정 (NT-07) — 행 없으면 기본 ON
CREATE TABLE notification_setting (
    id         UUID PRIMARY KEY,
    member_id  UUID        NOT NULL REFERENCES member (id),
    type       VARCHAR(30) NOT NULL,
    enabled    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_notification_setting UNIQUE (member_id, type)
);
