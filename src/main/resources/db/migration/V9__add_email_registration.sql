-- 자체(EMAIL) 회원가입 지원: 비밀번호 해시 컬럼 추가 + provider에 'EMAIL' 허용.
-- 비밀번호는 단방향 해시(BCrypt+pepper)로만 저장한다. SNS 회원은 password_hash가 NULL이다.

ALTER TABLE member ADD COLUMN password_hash VARCHAR(255);

-- provider 체크 제약에 'EMAIL' 추가(기존 제약 교체).
ALTER TABLE member DROP CONSTRAINT ck_member_provider;
ALTER TABLE member ADD CONSTRAINT ck_member_provider
    CHECK (provider IN ('APPLE', 'GOOGLE', 'KAKAO', 'EMAIL'));

-- EMAIL 회원은 비밀번호 해시가 반드시 있어야 한다(SNS 회원은 NULL 허용).
ALTER TABLE member ADD CONSTRAINT ck_member_email_password
    CHECK (provider <> 'EMAIL' OR password_hash IS NOT NULL);
