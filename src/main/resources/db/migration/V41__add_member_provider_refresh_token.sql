-- SPEC-0012 R5·R6. 탈퇴 시 provider 연결 해제에 쓸 refresh token 저장 자리.
-- 개인 자격증명이므로 암호문으로만 저장한다(AES-256-GCM, POL-012). 조회 키가 아니라 임의 IV로 충분하다.
-- 회원 1:1이고 갱신 이력 요구가 없어 별도 테이블 대신 컬럼으로 둔다.
-- Kakao는 어드민 키 + 회원번호로 해제하므로 이 값이 NULL이다. EMAIL 회원도 대상이 아니다.
ALTER TABLE member ADD COLUMN provider_refresh_token_encrypted VARCHAR(1024);
