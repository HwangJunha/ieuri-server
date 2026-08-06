-- community 도메인: 글 태그. **사용자가 직접 입력한다.**
--
-- 고정 enum(주제 태그)을 걷어내고 자유 입력 태그로 바꾼다. 고정 세트는 운영자가 미리 정한
-- 분류를 유저에게 강요하는데, 실제로 무엇이 묶이는지는 글이 쌓여 봐야 안다. 자유 태그는
-- 그 판단을 유저에게 맡기고, 서버는 같은 문자열을 같은 태그로 취급하기만 하면 된다.
--
-- 별도 테이블인 이유는 **태그로 글을 찾기 때문**이다. 배열이나 구분자 문자열로 담으면
-- "이 태그가 붙은 글" 조회가 전체 스캔이 된다.
CREATE TABLE community_post_tag (
    id      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT      NOT NULL,
    -- 표시용 원문. 유저가 입력한 대소문자·공백을 그대로 보존한다.
    name    VARCHAR(30) NOT NULL,
    -- 검색·중복 판정용 정규화 값(소문자·앞뒤 공백 제거). "Seoul"과 "seoul"을 같은 태그로 본다.
    -- 표시는 name, 매칭은 이 컬럼이라는 분리가 요점이다.
    normalized VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_post_tag_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    -- 한 글에 같은 태그가 두 번 붙지 않는다.
    CONSTRAINT uq_community_post_tag UNIQUE (post_id, normalized),
    CONSTRAINT ck_community_post_tag_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_community_post_tag_normalized_not_blank CHECK (btrim(normalized) <> '')
);

-- 태그로 글 찾기: WHERE normalized = ? → post_id로 조인.
CREATE INDEX idx_community_post_tag_normalized ON community_post_tag (normalized, post_id);

-- FK 자식 인덱스이자 글의 태그를 읽는 경로.
CREATE INDEX idx_community_post_tag_post ON community_post_tag (post_id);
