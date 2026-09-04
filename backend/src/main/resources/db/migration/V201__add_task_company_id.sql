-- =============================================================================
-- V201__add_task_company_id.sql — task에 company_id 추가 (ERD v1.6.5 = docs/06-erd.md)
--
-- 할 일은 Deal의 자식이지만 딜과 무관하게 독립 조회되는 대상이라(DB-05 "후속 필요" ·
-- "내 할 일") 기업 관리자(COMPANY_ALL) 범위 조회에 회사 축이 필요하다. activity는
-- 이미 같은 형태이고, 격리 2중 방어(서비스 스코프 + 복합 FK)가 activity에만 걸려 있던
-- 비대칭을 없앤다.
--
-- V1__baseline.sql은 건드리지 않는다 (11 §1.2) — 고치면 체크섬이 바뀌어 팀 전원이
-- 로컬 DB를 초기화해야 하는데, CI는 매번 새 컨테이너라 그걸 못 잡아준다.
-- =============================================================================

ALTER TABLE task ADD COLUMN company_id UUID REFERENCES company (id);

-- 기존 행을 부모 딜의 회사로 채운다. 복합 FK가 붙기 전이라 값이 어긋날 수 없다.
UPDATE task SET company_id = d.company_id FROM deal d WHERE d.id = task.deal_id;

ALTER TABLE task ALTER COLUMN company_id SET NOT NULL;

-- FK 교체는 맨 뒤다 — 단일 FK를 끝까지 살려두다 복합 FK가 붙는 순간 바꿔서
-- 참조 무결성이 비는 구간을 만들지 않는다. deal에 uk_deal_company_id_id UNIQUE
-- (company_id, id)가 이미 있어 복합 FK가 붙는다.
ALTER TABLE task DROP CONSTRAINT task_deal_id_fkey;
ALTER TABLE task ADD CONSTRAINT fk_task_deal FOREIGN KEY (company_id, deal_id) REFERENCES deal (company_id, id);

-- 관리자 범위 조회용. 기존 ix_task_deal_done은 지우지 않는다 — 조회 축이 둘이다
-- (영업 OWNED_ONLY = 딜 범위 · 관리자 COMPANY_ALL = 회사 범위). 06 §인덱스도 둘 다 나열한다.
CREATE INDEX ix_task_company_done ON task (company_id, done_at);
