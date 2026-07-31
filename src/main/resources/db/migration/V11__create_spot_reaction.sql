-- tourism 도메인: 관광지 좋아요/싫어요 기록(SPEC-0002).
-- DB에 쓰는 유일한 tourism 데이터 — 관광 데이터(TourAPI 응답)는 적재하지 않는다.
--
-- PK는 surrogate id(BIGSERIAL) — JPA 친화적(단일 @Id, 연관·merge 단순). 자연키는 UNIQUE로 강제한다.
-- UNIQUE (member_id, content_id, language) = upsert(재평가 덮어쓰기) 단위(AC-12/13).
--   - content_id: TourAPI contentId(언어별로 다름). 숫자 문자열이나 varchar로 둔다.
--   - language:   판단이 기록된 언어(EN/KO/JA/ZH). 같은 장소라도 언어별 contentId가 달라 언어를 키에 포함한다.
-- 재평가 = 같은 UNIQUE 키에 reaction 갱신(이력 없음). 취소(clear)는 범위 밖.
--
-- member_id는 auth 도메인(member.id)을 가리키지만 **물리 FK를 두지 않는다**(도메인 추출성 유지, no-cross-domain-FK).
-- 무결성/탈퇴 시 정리는 애플리케이션 계층이 담당한다(POL-014, §9.7).
CREATE TABLE spot_reaction (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    content_id VARCHAR(32)  NOT NULL,
    language   VARCHAR(2)   NOT NULL,
    reaction   VARCHAR(10)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_reaction UNIQUE (member_id, content_id, language),
    CONSTRAINT ck_spot_reaction_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_spot_reaction_reaction CHECK (reaction IN ('LIKE', 'DISLIKE'))
);

-- 회원별 취향 조회(향후 재랭킹·탈퇴 시 정리)를 위한 인덱스.
-- UNIQUE 인덱스가 (member_id, ...) 선행이라 member_id 단독 조회도 커버되나, 명시 인덱스로 의도를 드러낸다.
CREATE INDEX idx_spot_reaction_member ON spot_reaction (member_id);
