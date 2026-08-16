-- SPEC-0024 R5. `(provider, email)` 조합을 유일하게 만든다.
--
-- **암호문에는 유니크를 걸 수 없다.** `email_encrypted`는 AES-256-GCM이고 IV가 매번 달라
-- 같은 주소도 매번 다른 값이 된다(POL-012). 그래서 정규화된 주소의 결정론적 HMAC을
-- 판정용 파생 컬럼으로 따로 둔다 — `provider_user_id`가 이미 같은 문제를 푼 방식이다
-- (SPEC-0012 R1). 식별 키는 `provider_user_id` 그대로이며 이 제약이 식별 방식을 바꾸지 않는다.
--
-- **`(KAKAO, a@b.com)`과 `(EMAIL, a@b.com)`은 공존한다**(R5-2, POL-002). 그래서 유니크는
-- 이메일 전역이 아니라 provider와의 조합이다. 전역으로 잡으면 다계정 허용 정책이 깨진다.
--
-- **값은 애플리케이션이 채운다.** pepper가 필요해 SQL로 계산할 수 없다 — 가입·이메일 변경·
-- 인증 주소 재입력이 `email_encrypted`와 **같은 트랜잭션에서 함께** 쓴다.
--
-- **V62 이후이므로 스키마 한정으로 쓴다** — `connection-init-sql`의 search_path가 Flyway
-- 커넥션에 닿지 않는다(DataSourceConfig 참조).

-- ── 1. 회원·reaction·community 데이터 삭제 ─────────────────────────
--
-- **운영 데이터가 없다**(스펙 §3·§6). 착수 시점에 회원·reaction·community 데이터를 전량
-- 삭제하는 것이 이 스펙의 전제이며, 그래서 `email_identifier`를 NOT NULL로 바로 세울 수 있다.
-- 남은 회원이 있으면 pepper 없이는 백필이 불가능해 이 마이그레이션이 성립하지 않는다.
--
-- **회원만 지울 수는 없다.** reaction·community의 회원 참조는 컨텍스트 간이라 물리 FK가
-- 없으므로(도메인 추출성 유지), 회원만 지우면 DB가 막지 않은 채 dangling 참조가 남는다.
-- 그래서 세 덩어리를 함께 지운다.
--
-- 삭제 순서는 FK를 따른다(자식 먼저). `member_audit_log`는 FK가 없고 감사 이력이라 보존한다.

-- community — 참조하는 쪽부터.
DELETE FROM community.community_message_report_item;
DELETE FROM community.community_message_report;
DELETE FROM community.community_message;
DELETE FROM community.community_conversation;
DELETE FROM community.community_report;
DELETE FROM community.community_block;
DELETE FROM community.community_comment;
DELETE FROM community.community_post_hide;
DELETE FROM community.community_post_like;
DELETE FROM community.community_post_chip;
DELETE FROM community.community_post_tag;
DELETE FROM community.community_post_spot;
DELETE FROM community.community_post_photo;
DELETE FROM community.community_orphan_photo_object;
DELETE FROM community.community_post;

-- tour — 회원의 좋아요/싫어요.
DELETE FROM tour.spot_reaction;

-- auth — 회원 애그리거트.
DELETE FROM auth.email_verification;
DELETE FROM auth.password_reset;
DELETE FROM auth.member_terms_agreement;
DELETE FROM auth.member_role;
DELETE FROM auth.member_profile;
DELETE FROM auth.member;

-- ── 2. 파생 컬럼 ───────────────────────────────────────────────────
--
-- HMAC-SHA256을 소문자 16진수로 표현한 64자다(HmacLoginIdentifierProtector와 같은 형식).
ALTER TABLE auth.member ADD COLUMN email_identifier VARCHAR(64) NOT NULL;

COMMENT ON COLUMN auth.member.email_identifier IS
    '정규화 이메일의 HMAC. (provider, email) 유니크 판정 전용이며 email_encrypted와 항상 함께 갱신된다.';

-- ── 3. 유니크 제약 ─────────────────────────────────────────────────
--
-- 최종 방어선이다. 애플리케이션이 먼저 조회로 거절하지만, 같은 주소를 동시에 확정하려는 두
-- 요청 중 하나는 여기서 걸린다.
ALTER TABLE auth.member ADD CONSTRAINT uq_member_provider_email
    UNIQUE (provider, email_identifier);
