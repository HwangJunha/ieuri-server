-- community_report.target_type을 넓힌다(SPEC-0026 R11).
--
-- **V64가 CHECK 제약만 넓히고 컬럼 길이를 함께 보지 않았다.** V28이 정한 VARCHAR(10)에는
-- 새 값 셋이 들어가지 않는다 — SPOT_ANSWER(11) · SPOT_QUESTION(13) · SPOT_ANSWER_REPLY(17).
-- 애플리케이션·스키마·CHECK가 모두 통과하고 INSERT에서만 터지므로 유닛테스트로는 잡히지
-- 않았고, 실제 신고 요청에서 `value too long for type character varying(10)`으로 드러났다.
--
-- **V64를 고치지 않고 새 파일을 만드는 이유**: V64는 이미 적용됐다. 편집하면 checksum이
-- 깨져 다른 체크아웃·환경의 기동을 막는다.
--
-- 30으로 잡는 것은 `reason` 컬럼과 같은 값이다 — 가장 긴 현재 값이 17자라 여유가 있고,
-- 열거형이 늘어도 당분간 이 작업을 반복하지 않는다.
ALTER TABLE community.community_report
    ALTER COLUMN target_type TYPE VARCHAR(30);
