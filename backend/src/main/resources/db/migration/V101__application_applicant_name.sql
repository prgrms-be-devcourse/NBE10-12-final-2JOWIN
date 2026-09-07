-- 신청자 이름 (ON-01) — 승인 시 기업 관리자 계정의 member.name 으로 복사된다 (ON-07).
--
-- member.name 은 NOT NULL 인데 신청서에 이 값을 받는 자리가 없었다. 04-user-scenarios.md
-- §1 이 승인 결과를 "김서연 계정 생성"으로 규정하므로 회사명 복사는 답이 아니다 —
-- 사람 이름 자리에 상호가 들어가면 감사 기록과 화면 표시가 둘 다 거짓이 된다.
--
-- 기존 행은 회사명으로 채운다. 이름을 받기 전에 들어온 신청이라 다른 출처가 없고,
-- 승인 후 본인이 프로필 수정(AU-07)으로 고칠 수 있다. 길이는 member.name 과 맞춘다.
ALTER TABLE application ADD COLUMN applicant_name VARCHAR(100);
UPDATE application SET applicant_name = company_name WHERE applicant_name IS NULL;
ALTER TABLE application ALTER COLUMN applicant_name SET NOT NULL;
