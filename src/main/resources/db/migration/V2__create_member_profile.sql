-- 회원 프로필. provider 제공 닉네임/프로필이미지로 초기화한다.
-- 닉네임/이미지는 표시용이라 평문. member_id는 도메인 내부 FK(허용).
CREATE TABLE member_profile (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id         BIGINT      NOT NULL,
    nickname          VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(1024),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_member_profile_member UNIQUE (member_id),
    CONSTRAINT fk_member_profile_member FOREIGN KEY (member_id) REFERENCES member (id)
);
