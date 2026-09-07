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

-- REFERENCES company (id)까지 단다 — activity가 회사 FK와 딜 복합 FK를 둘 다 갖는 형태이고
-- (V1 activity), 06 v1.6.5가 "activity와 같은 형태로 통일"로 정했다. 제약명은 PostgreSQL이
-- 짓는다 (task_company_id_fkey).
ALTER TABLE task ADD COLUMN company_id UUID REFERENCES company (id);

-- 기존 행을 부모 딜의 회사로 채운다. 값을 deal에서 그대로 복사하므로 정의상 딜의 회사와
-- 같다 — 아래 복합 FK는 이 백필이 아니라 이후 INSERT를 막는 장치다.
UPDATE task SET company_id = d.company_id FROM deal d WHERE d.id = task.deal_id;

ALTER TABLE task ALTER COLUMN company_id SET NOT NULL;

-- 단일 FK(딜 존재 여부만 확인)를 복합 FK(회사+딜 조합 확인)로 바꾼다. 단일로 두면
-- task.company_id와 딜의 company_id가 어긋나는 조합을 DB가 막지 못한다.
-- deal에 uk_deal_company_id_id UNIQUE (company_id, id)가 이미 있어 복합 FK가 붙는다.
--
-- task_deal_id_fkey는 PostgreSQL이 지은 이름이다 — V1이 deal_id를 인라인으로
-- (REFERENCES deal (id)) 선언해 제약명을 남기지 않았다. 저장소에 이 문자열이 없다.
ALTER TABLE task DROP CONSTRAINT task_deal_id_fkey;
ALTER TABLE task ADD CONSTRAINT fk_task_deal FOREIGN KEY (company_id, deal_id) REFERENCES deal (company_id, id);

-- 관리자 범위 조회용. 기존 ix_task_deal_done은 지우지 않는다 — 조회 축이 둘이다
-- (영업 OWNED_ONLY = 딜 범위 · 관리자 COMPANY_ALL = 회사 범위). 06 §인덱스도 둘 다 나열한다.
CREATE INDEX ix_task_company_done ON task (company_id, done_at);
