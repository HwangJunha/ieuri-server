-- community 도메인: 글에 첨부된 사진의 오브젝트 키(SPEC-0010 R3).
--
-- **DB에는 object key만 저장하고 전체 URL은 저장하지 않는다.** 조회는 클라이언트가
-- cdn.ieuri.co.kr과 이 키를 조합해 만든다. 도메인·CDN이 바뀌어도 마이그레이션이 필요 없다.
--
-- 별도 테이블로 두는 이유는 순서(position)가 계약의 일부이기 때문이다. 배열 컬럼이나
-- 구분자 문자열로 담으면 순서 보장과 개수 제약이 애플리케이션에만 남는다.
CREATE TABLE community_post_photo (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT       NOT NULL,
    -- 확인(confirm) 단계를 통과해 images/ 로 옮겨진 뒤의 최종 키.
    object_key VARCHAR(512) NOT NULL,
    -- 표시 순서. 0부터 최대 4까지 — 사진은 글당 최대 5장(R3).
    position   SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 도메인 내부 FK는 허용된다. 글이 물리 삭제될 일은 없으나(소프트 삭제),
    -- 정합성을 DB가 보장하게 둔다.
    CONSTRAINT fk_community_post_photo_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    -- 최대 5장을 DB에서도 강제한다(R3). 애플리케이션의 TOO_MANY_PHOTOS와 이중 방어다.
    CONSTRAINT ck_community_post_photo_position CHECK (position BETWEEN 0 AND 4),
    -- 같은 글에 같은 순서가 둘일 수 없다.
    CONSTRAINT uq_community_post_photo_position UNIQUE (post_id, position)
);

-- FK 자식 쪽 인덱스. 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다(V19 선례).
-- 글 조회 시 사진을 순서대로 읽는 경로이기도 하다.
CREATE INDEX idx_community_post_photo_post ON community_post_photo (post_id, position);
