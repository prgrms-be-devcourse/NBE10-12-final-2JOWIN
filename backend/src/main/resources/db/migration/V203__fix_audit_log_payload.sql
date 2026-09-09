-- 데모 audit_log payload 를 이벤트 규약(이슈 #22 §2)에 맞춘다.
-- 스키마는 바뀌지 않는다 — R__demo_seed 가 ON CONFLICT DO NOTHING 이라 파일만 고치면
-- 이미 DB 가 있는 사람에게 반영되지 않아 여기서 수렴시킨다.
-- 운영 DB 에는 이 id 의 행이 없어 0건으로 통과한다.

-- 변경형은 changes 로 감싼다. stage 가 최상위에 있었다
UPDATE audit_log
   SET payload = '{"dealId":"5d000000-0000-4000-8000-000000000008","changes":{"stage":{"before":"CONSULT","after":"QUOTE"}}}'
 WHERE id = '9e000000-0000-4000-8000-000000000001';

-- 타임라인 문구에 필요한 부가 필드가 빠져 있었다
UPDATE audit_log
   SET payload = '{"dealId":"5d000000-0000-4000-8000-000000000008","quoteNo":"Q-2608-014"}'
 WHERE id = '9e000000-0000-4000-8000-000000000003';
