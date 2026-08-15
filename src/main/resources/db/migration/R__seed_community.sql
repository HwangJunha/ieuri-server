-- 커뮤니티 더미 데이터(반복 실행 안전 / idempotent). 개발·QA에서 화면을 눈으로 확인하기 위한 것이다.
--
-- **이 시드는 로컬·개발 환경 전용이다.** 프로덕션에 이 파일이 딸려 가면 실제 서비스에 가짜 글이
-- 뜬다. R__ 파일은 체크섬이 바뀔 때마다 재적용되고 환경 구분을 하지 않으므로, 프로덕션 배포 전에
-- 이 파일을 지우거나 Flyway locations에서 제외해야 한다.
--
-- 화자(회원)는 실재하는 id만 쓴다. **20번이 주인공**이고 나머지는 상대역이다:
--   20 : 주 사용자. 본인 글·댓글·좋아요, 차단 건 쪽, 자기 숨김 글의 작성자
--   19 : 20이 차단한 상대. 20에게는 19의 글·댓글이 전부 보이지 않아야 한다
--   18 : 20을 차단한 상대. 차단이 양방향임을 이 사람으로 확인한다(20은 18의 글도 못 본다)
--   17 : 자동 숨김된 글의 작성자. 본인에게만 보이고 남에게는 안 보인다
--   12·14·15·16 : 평범한 참여자. 신고자·댓글러 역할도 겸한다
--   13 : 존재하지 않는 회원이라 쓰지 않는다
--
-- 상단 칩은 앱별·언어별 구성을 확인할 수 있게 두 앱 × 4개 언어를 넣는다(§8).
--
-- spot id는 실재하는 KO 관광지다(경복궁 11094 등). 대부분 서울 시청 반경 2.5km 안에 있어
-- nearbyPosts(37.5665, 126.9780) 한 번으로 여러 건이 잡히고, 해운대(11104)는 331km 밖이라
-- 반경 필터가 실제로 거르는지 확인하는 대조군이다.
--
-- 멱등성: 자연키가 없는 테이블이라 ON CONFLICT를 쓸 수 없어, 시드가 만든 행만 지우고 다시 넣는다.
-- 판별 기준은 "시드가 쓰는 회원이 시드가 쓰는 spot에 쓴 글"이며, 사람이 손으로 만든 글은 건드리지 않는다.

-- 1) 이전 시드 정리. 댓글·사진·좋아요는 FK ON DELETE CASCADE로 함께 지워진다.
DELETE FROM community_post WHERE author_id IN (12, 14, 15, 16, 17, 18, 19, 20);

DELETE FROM community_post_hide WHERE member_id IN (12, 14, 15, 16, 17, 18, 19, 20);
DELETE FROM community_block   WHERE blocker_id IN (18, 20) AND blocked_id IN (18, 19, 20);
DELETE FROM community_report  WHERE reporter_id IN (12, 14, 15, 16, 17, 18, 19, 20);

-- 2) 차단 관계를 글보다 먼저 넣는다. 피드 필터가 이 관계를 읽기 때문이다.
--    저장은 단방향 한 건이고 해석이 양방향이라, 두 행의 방향이 서로 다른 것이 요점이다.
INSERT INTO community_block (blocker_id, blocked_id, created_at) VALUES
    -- 20이 19를 차단했다 → 20의 피드에서 19가 사라진다(내가 차단한 쪽)
    (20, 19, now() - interval '3 days'),
    -- 18이 20을 차단했다 → 20의 피드에서 18도 사라진다(나를 차단한 쪽, 양방향 확인용)
    (18, 20, now() - interval '2 days');

-- 3) 글. created_at을 서로 다르게 줘서 최신순 정렬과 커서 페이징을 눈으로 확인할 수 있게 한다.
--    본문에 이모지를 섞어 UTF8 저장·조회를 함께 확인한다(가족 이모지는 코드포인트 7자다).
INSERT INTO community_post
    (author_id, title, body, like_count, comment_count, hidden, created_at, updated_at)
VALUES
    -- 20번(주인공)의 글 — 본인에게 ownedByViewer=true로 보인다
    -- comment_count=4 : 보이는 댓글 4건(12·14·15·19번). 19번 댓글은 20번에게만 차단으로 가려질 뿐
    -- 숨김이 아니므로 **숫자에는 포함된다** — 차단은 보는 사람마다 다르지만 숫자는 모두에게 같다(R23-4).
    -- 17번의 숨김 댓글 1건만 이 수에서 빠진다.
    (20, '경복궁 야간개장 후기',
     '경복궁 야간개장 다녀왔어요 🌙 한복 입고 가면 입장료가 무료라 사람이 정말 많네요. 수문장 교대식은 꼭 보세요!',
     12, 4, FALSE, now() - interval '2 hours', now() - interval '2 hours'),
    (20, '경복궁 주차 어디에 하나요?',
     '경복궁 근처에 주차할 만한 곳 있을까요? 🚗 주말에 차 가져가려는데 고민이네요.',
     2, 2, FALSE, now() - interval '1 day', now() - interval '1 day'),
    (20, '북촌 골목 사진 몇 장',
     '북촌 골목에서 찍은 사진 몇 장 올려요 📸 오후 4시쯤 빛이 제일 예뻤습니다.',
     31, 1, FALSE, now() - interval '5 days', now() - interval '5 days'),
    (20, '주말 해운대 같이 가실 분',
     '이번 주말 해운대 같이 가실 분 🌊 뚜벅이라 대중교통으로 움직입니다.',
     4, 0, FALSE, now() - interval '6 hours', now() - interval '6 hours'),

    -- 12·14·15·16번 — 20번에게 정상적으로 보이는 타인의 글
    (12, '경복궁 휴무일 주의하세요',
     '경복궁은 매주 화요일 휴무예요. 헛걸음하지 마세요 🙏',
     45, 2, FALSE, now() - interval '3 hours', now() - interval '3 hours'),
    (14, '덕수궁 돌담길 야경 추천',
     '덕수궁 돌담길 산책하기 좋아요. 저녁에 조명 켜지면 분위기가 확 달라집니다 ✨',
     18, 1, FALSE, now() - interval '8 hours', now() - interval '8 hours'),
    (15, '인사동 기념품 가게 추천해주세요',
     '인사동에서 기념품 살 만한 가게 추천해주세요! 외국인 친구 선물용이에요 🎁',
     7, 1, FALSE, now() - interval '12 hours', now() - interval '12 hours'),
    (16, '청계천 야경 사진',
     '청계천 야경 🌃 여름 밤에 발 담그고 있으면 시원해요.',
     23, 0, FALSE, now() - interval '2 days', now() - interval '2 days'),
    (12, '서울광장 잔디 개방 기간',
     '서울광장 잔디밭은 4월~10월에만 개방합니다. 돗자리 챙겨가세요 🧺',
     9, 0, FALSE, now() - interval '4 days', now() - interval '4 days'),

    -- **수정된 글**. edited=TRUE이면 클라이언트가 "수정됨"을 표시해야 한다.
    -- updated_at이 created_at보다 뒤인 것도 함께 확인할 수 있다.
    (15, '덕수궁 입장료 정보 (수정됨)',
     '덕수궁 입장료 1,000원입니다. 문화가 있는 날은 무료예요 ✨ (정보 갱신했습니다)',
     6, 0, FALSE, now() - interval '10 hours', now() - interval '30 minutes'),

    -- 19번 — 20이 차단한 상대. **20의 피드에 이 두 건이 나오면 차단 필터가 깨진 것이다.**
    (19, '[차단검증] 20번에게 안 보여야 하는 글',
     '[차단검증] 20번이 차단한 사용자의 글입니다. 20번 피드에 보이면 안 됩니다.',
     3, 0, FALSE, now() - interval '4 hours', now() - interval '4 hours'),
    (19, '[차단검증] 19번의 두 번째 글',
     '[차단검증] 19번의 두 번째 글. 마찬가지로 20번에게는 보이지 않아야 합니다.',
     1, 0, FALSE, now() - interval '7 hours', now() - interval '7 hours'),

    -- 18번 — 20을 차단한 상대. **차단이 양방향이므로 20의 피드에 나오면 안 된다.**
    (18, '[역차단검증] 18번이 20번을 차단',
     '[역차단검증] 18번이 20번을 차단했습니다. 20번 피드에 이 글이 보이면 양방향 해석이 빠진 것입니다.',
     5, 0, FALSE, now() - interval '9 hours', now() - interval '9 hours'),

    -- 17번 — 자동 숨김된 글. **작성자 17에게만 보이고 20·다른 사람에게는 안 보인다.**
    (17, '[숨김검증] 자동 숨김된 글',
     '[숨김검증] 신고 5건이 쌓여 자동 숨김된 글입니다. 작성자 본인(17)에게만 hidden=true로 보입니다.',
     0, 0, TRUE, now() - interval '1 day', now() - interval '1 day'),

    -- 17번의 정상 글 — 숨김이 글 단위이지 사람 단위가 아님을 보여준다(R22)
    (17, '[대조군] 17번의 정상 글',
     '[대조군] 같은 작성자(17)의 정상 글. 신고당한 것은 위 글이지 이 사람이 아니므로 모두에게 보입니다.',
     14, 0, FALSE, now() - interval '3 days', now() - interval '3 days'),
    -- **장소 없이 카테고리만으로 쓴 글.** spot이 선택임을 확인하는 대조군이다.
    (14, '여행 짐 싸는 팁 공유해요',
     '캐리어에 옷 넣을 때 돌돌 말면 공간이 확 줄어요 🧳 다들 어떻게 싸시나요?',
     3, 0, FALSE, now() - interval '11 hours', now() - interval '11 hours');

-- 위 글 하나를 수정된 상태로 표시한다. INSERT에 edited를 직접 넣지 않고 UPDATE로 두는 이유는
-- "수정은 나중에 일어난 일"이라는 것을 시드 자체가 드러내기 위함이다.
UPDATE community_post SET edited = TRUE
WHERE title = '덕수궁 입장료 정보 (수정됨)' AND author_id = 15;


-- 3-1) 글 ↔ 카테고리. **모든 글에 붙는다** — 장소 유무와 무관하게 칩으로 걸러져야 한다.
INSERT INTO community_post_chip (post_id, category)
SELECT p.id, v.category
FROM community_post p
JOIN (VALUES
    ('경복궁 야간개장 후기', 20, 'TOURIST_SPOT'),
    ('경복궁 주차 어디에 하나요?', 20, 'TOURIST_SPOT'),
    ('북촌 골목 사진 몇 장', 20, 'TOURIST_SPOT'),
    ('주말 해운대 같이 가실 분', 20, 'LEISURE_SPORTS'),
    ('경복궁 휴무일 주의하세요', 12, 'TOURIST_SPOT'),
    ('덕수궁 돌담길 야경 추천', 14, 'TOURIST_SPOT'),
    ('인사동 기념품 가게 추천해주세요', 15, 'SHOPPING'),
    ('청계천 야경 사진', 16, 'TOURIST_SPOT'),
    ('서울광장 잔디 개방 기간', 12, 'FESTIVAL_EVENT'),
    ('덕수궁 입장료 정보 (수정됨)', 15, 'TOURIST_SPOT'),
    ('[차단검증] 20번에게 안 보여야 하는 글', 19, 'TOURIST_SPOT'),
    ('[차단검증] 19번의 두 번째 글', 19, 'TOURIST_SPOT'),
    ('[역차단검증] 18번이 20번을 차단', 18, 'TOURIST_SPOT'),
    ('[숨김검증] 자동 숨김된 글', 17, 'TOURIST_SPOT'),
    ('[대조군] 17번의 정상 글', 17, 'TOURIST_SPOT'),
    ('여행 짐 싸는 팁 공유해요', 14, 'TRAVEL_COURSE')
) AS v(title, author_id, category)
  ON p.title = v.title AND p.author_id = v.author_id;

-- 3-2) 글 ↔ 장소. **선택이다** — 위 '여행 짐 싸는 팁' 글에는 없다.
--      좌표는 spot에서 복사한다(location은 트리거가 파생시킨다).
--
--      VALUES에 적힌 것은 언어별 `spot.id`이므로 spot을 경유해 카탈로그로 바꿔 넣는다
--      (SPEC-0019 R-12).
--
--      > **첫 기동에서는 이 블록이 0행을 넣는다.** 카탈로그 배정은 배치가 하고 배치는 앱이
--      > 뜬 뒤에 돌므로, 기동 시점의 `spot.catalog_id`는 전부 null이다. 배정을 한 번 돌린
--      > 다음 이 파일의 체크섬을 건드려야(공백 한 줄이면 된다) 재실행되어 채워진다.
--      > 로컬에서 커뮤니티 장소 칩이 비어 보이면 원인은 대개 이것이다.
INSERT INTO community_post_spot (post_id, catalog_id, map_x, map_y)
SELECT p.id, s.catalog_id, s.map_x, s.map_y
FROM community_post p
JOIN (VALUES
    ('경복궁 야간개장 후기', 20, 11094),
    ('경복궁 주차 어디에 하나요?', 20, 11094),
    ('북촌 골목 사진 몇 장', 20, 33313),
    ('주말 해운대 같이 가실 분', 20, 11104),
    ('경복궁 휴무일 주의하세요', 12, 11094),
    ('덕수궁 돌담길 야경 추천', 14, 10997),
    ('인사동 기념품 가게 추천해주세요', 15, 32775),
    ('청계천 야경 사진', 16, 9471),
    ('서울광장 잔디 개방 기간', 12, 27574),
    ('덕수궁 입장료 정보 (수정됨)', 15, 10997),
    ('[차단검증] 20번에게 안 보여야 하는 글', 19, 11094),
    ('[차단검증] 19번의 두 번째 글', 19, 33313),
    ('[역차단검증] 18번이 20번을 차단', 18, 10997),
    ('[숨김검증] 자동 숨김된 글', 17, 11094),
    ('[대조군] 17번의 정상 글', 17, 9471)
) AS v(title, author_id, spot_id)
  ON p.title = v.title AND p.author_id = v.author_id
JOIN spot s ON s.id = v.spot_id AND s.catalog_id IS NOT NULL;

-- 4) 사진 키. 실제 R2에 오브젝트가 없으므로 **CDN에서는 깨진 이미지로 나온다** — 키 배열과
--    순서(position)가 계약대로 내려오는지 확인하는 용도다.
INSERT INTO community_post_photo (post_id, object_key, position)
SELECT p.id, 'images/community/20/00000000-0000-4000-8000-00000000000' || g.n, g.n
FROM community_post p
CROSS JOIN (VALUES (0), (1), (2)) AS g(n)
WHERE p.author_id = 20 AND p.title = '북촌 골목 사진 몇 장';

INSERT INTO community_post_photo (post_id, object_key, position)
SELECT p.id, 'images/community/16/00000000-0000-4000-8000-00000000010', 0
FROM community_post p
WHERE p.author_id = 16 AND p.title = '청계천 야경 사진';

-- 5) 댓글. comment_count는 **숨김 댓글만** 제외한 수다(R23).
--    차단은 보는 사람마다 다르지만 숫자는 모두에게 같아야 하므로, 차단 대상의 댓글은 수에 남는다.
INSERT INTO community_comment (post_id, author_id, body, hidden, created_at, updated_at)
SELECT p.id, c.author_id, c.body, c.hidden, now() - (c.age || ' hours')::interval, now() - (c.age || ' hours')::interval
FROM community_post p
JOIN (VALUES
    -- 20번의 경복궁 후기(comment_count = 4) → 숨김 아닌 댓글 4건 + 숨김 1건.
    -- 19번 댓글은 20번 화면에서만 차단으로 가려지고 숫자에는 남는다.
    ('경복궁 야간개장 후기', 20, 12, '저도 지난주에 다녀왔어요! 한복 대여점이 근처에 많더라고요 😊', FALSE, 1),
    ('경복궁 야간개장 후기', 20, 14, '수문장 교대식 시간표가 어떻게 되나요?', FALSE, 1),
    ('경복궁 야간개장 후기', 20, 15, '사진 잘 보고 갑니다 👍', FALSE, 1),
    ('경복궁 야간개장 후기', 20, 19, '[차단검증] 20이 차단한 19의 댓글. 20에게는 보이지 않아야 합니다.', FALSE, 1),
    -- 숨김 댓글 — comment_count(4)에 들어가 있지 않다. 목록에도 작성자 17에게만 보인다.
    ('경복궁 야간개장 후기', 20, 17, '[숨김검증] 신고 누적으로 숨겨진 댓글. 숫자에는 안 잡힙니다.', TRUE, 2),

    -- 20번의 주차 질문(comment_count = 2)
    ('경복궁 주차 어디에 하나요?', 20, 12, '주차장 협소해서 대중교통 추천드려요 🚇 3호선 경복궁역 5번 출구요.', FALSE, 3),
    ('경복궁 주차 어디에 하나요?', 20, 16, '주말엔 진짜 자리 없어요. 근처 사설주차장도 만차입니다.', FALSE, 4),

    -- 타인 글의 댓글
    ('경복궁 휴무일 주의하세요', 12, 20, '오 몰랐네요. 저장해둡니다 🔖', FALSE, 2),
    ('경복궁 휴무일 주의하세요', 12, 14, '작년에 화요일에 갔다가 그냥 돌아왔었어요 😭', FALSE, 5),
    ('덕수궁 돌담길 야경 추천', 14, 20, '돌담길 야경 좋죠. 저도 좋아하는 코스예요.', FALSE, 6),
    ('인사동 기념품 가게 추천해주세요', 15, 20, '쌈지길 안에 아기자기한 가게 많아요!', FALSE, 7),
    -- 북촌 글(comment_count = 1). 18은 20을 차단했으므로 20에게만 이 댓글이 가려진다 —
    -- 그래도 숫자에는 남아, 20의 화면에서 "1건인데 목록은 비어 있음"이 된다(R23-4 확인용).
    ('북촌 골목 사진 몇 장', 20, 18, '[역차단검증] 18의 댓글. 20에게는 보이지 않아야 합니다.', FALSE, 8)
) AS c(post_title, post_author, author_id, body, hidden, age)
  ON p.title = c.post_title AND p.author_id = c.post_author;

-- 5-1) 조회수. 좋아요 수와 일부러 다른 값을 준다 — 둘은 출처가 다른 별개의 계수이며,
--      조회수는 중복을 걸러 내지 않아 통상 좋아요보다 훨씬 크다.
UPDATE community_post SET view_count = like_count * 7 + 13
WHERE author_id IN (12, 14, 15, 16, 17, 18, 19, 20);

-- 5-2) 대댓글. **깊이 2단계 확인용** — 최상위 댓글에 답글이 달린 모습과,
--      대댓글의 replies가 비어 있는 것을 함께 본다. comment_count에는 이 답글들도 포함된다.
INSERT INTO community_comment (post_id, parent_comment_id, author_id, body, hidden, created_at, updated_at)
SELECT c.post_id, c.id, v.author_id, v.body, FALSE,
       c.created_at + interval '10 minutes', c.created_at + interval '10 minutes'
FROM community_comment c
JOIN community_post p ON p.id = c.post_id
JOIN (VALUES
    ('수문장 교대식 시간표가 어떻게 되나요?', 20, '10시, 14시 두 번이에요! 정각에 시작합니다 ⏰'),
    ('수문장 교대식 시간표가 어떻게 되나요?', 12, '겨울에는 시간이 조금 달라질 수 있어요.')
) AS v(parent_body, author_id, body)
  ON c.body = v.parent_body
WHERE p.title = '경복궁 야간개장 후기' AND p.author_id = 20
  AND c.parent_comment_id IS NULL;

-- 위 답글 2건을 댓글 수에 반영한다. 대댓글도 댓글이다.
UPDATE community_post SET comment_count = comment_count + 2
WHERE title = '경복궁 야간개장 후기' AND author_id = 20;

-- 6) 좋아요. like_count 컬럼과 실제 행 수는 일부러 다르다 — 카운트는 파생 저장값이고
--    likedByViewer는 이 행으로 판정되므로, 두 값의 출처가 다름을 보여준다.
--    20번이 누른 좋아요만 넣어 20의 피드에서 likedByViewer=true가 뜨는 글을 만든다.
INSERT INTO community_post_like (post_id, member_id, created_at)
SELECT p.id, 20, now() - interval '1 hour'
FROM community_post p
WHERE (p.author_id = 12 AND p.title = '경복궁 휴무일 주의하세요')
   OR (p.author_id = 14 AND p.title = '덕수궁 돌담길 야경 추천');

-- 다른 회원들의 좋아요 — 20번 글에 붙여 본인 글의 반응을 보이게 한다.
INSERT INTO community_post_like (post_id, member_id, created_at)
SELECT p.id, m.member_id, now() - interval '2 hours'
FROM community_post p
CROSS JOIN (VALUES (12), (14), (15), (16)) AS m(member_id)
WHERE p.author_id = 20;

-- 7) 신고. 17번의 숨김 글에 **서로 다른 신고자 5명**을 붙여 임계값 도달 상태를 재현한다.
--    글의 hidden이 이미 TRUE인 것과 이 5건이 앞뒤가 맞아야 한다.
INSERT INTO community_report (reporter_id, target_type, target_id, reason, detail, created_at)
SELECT r.reporter_id, 'POST', p.id, r.reason, r.detail, now() - interval '1 day'
FROM community_post p
JOIN (VALUES
    (12, 'SPAM',                  NULL),
    (14, 'ABUSE',                 NULL),
    (15, 'INAPPROPRIATE_CONTENT', NULL),
    (16, 'SPAM',                  NULL),
    (20, 'OTHER',                 '기타 사유 상세입니다. OTHER는 상세가 필수입니다.')
) AS r(reporter_id, reason, detail)
  ON TRUE
WHERE p.author_id = 17 AND p.hidden = TRUE;

-- 사용자 대상 신고 — 5명이 신고해도 **아무 자동 조치가 없음**을 확인하는 데이터다(R22).
-- 19번의 글이 여전히 hidden=FALSE인 것이 그 증거다.
INSERT INTO community_report (reporter_id, target_type, target_id, reason, detail, created_at)
VALUES
    (12, 'MEMBER', 19, 'ABUSE', NULL, now() - interval '2 days'),
    (14, 'MEMBER', 19, 'SPAM',  NULL, now() - interval '2 days'),
    (15, 'MEMBER', 19, 'ABUSE', NULL, now() - interval '2 days'),
    (16, 'MEMBER', 19, 'ABUSE', NULL, now() - interval '2 days'),
    (17, 'MEMBER', 19, 'OTHER', '사용자 신고는 기록만 되고 자동 조치가 없어야 합니다.', now() - interval '2 days');

-- 8) 앱별 카테고리 구성(app_category, SPEC-0020). **앱마다·언어마다 구성이 다르다** —
--    그것이 이 테이블의 존재 이유다. 커뮤니티 상단 칩과 관광지 목록이 같은 이 표를 읽는다.
--
--    카테고리의 가용 집합이 언어별로 갈린다(TourAPI contentTypeId 체계):
--      국문(KO)   : 여행코스(TRAVEL_COURSE)가 있고 교통(TRANSPORT)이 없다
--      외국어(EN/JA/ZH): 교통이 있고 여행코스가 없다
--    그래서 언어마다 8개씩 넣는다. 없는 카테고리를 칩으로 띄우면 눌러도 결과가 비어 있다.
--
--    표시 문구는 category_translation이 갖는다(V58). 이 표는 노출 여부와 순서만 정한다.
DELETE FROM app_category WHERE app_key IN ('k-trip-spot', 'demo-app');

INSERT INTO app_category (app_key, language, category, position, enabled) VALUES
    ('k-trip-spot', 'KO', 'RESTAURANT', 0, TRUE),
    ('k-trip-spot', 'KO', 'FESTIVAL_EVENT', 1, TRUE),
    ('k-trip-spot', 'KO', 'TOURIST_SPOT', 2, TRUE),
    ('k-trip-spot', 'KO', 'CULTURAL_FACILITY', 3, TRUE),
    ('k-trip-spot', 'KO', 'LEISURE_SPORTS', 4, TRUE),
    ('k-trip-spot', 'KO', 'LODGING', 5, TRUE),
    ('k-trip-spot', 'KO', 'SHOPPING', 6, TRUE),
    ('k-trip-spot', 'KO', 'TRAVEL_COURSE', 7, TRUE),
    ('k-trip-spot', 'EN', 'RESTAURANT', 0, TRUE),
    ('k-trip-spot', 'EN', 'FESTIVAL_EVENT', 1, TRUE),
    ('k-trip-spot', 'EN', 'TOURIST_SPOT', 2, TRUE),
    ('k-trip-spot', 'EN', 'CULTURAL_FACILITY', 3, TRUE),
    ('k-trip-spot', 'EN', 'LEISURE_SPORTS', 4, TRUE),
    ('k-trip-spot', 'EN', 'LODGING', 5, TRUE),
    ('k-trip-spot', 'EN', 'SHOPPING', 6, TRUE),
    ('k-trip-spot', 'EN', 'TRANSPORT', 7, TRUE),
    ('k-trip-spot', 'JA', 'RESTAURANT', 0, TRUE),
    ('k-trip-spot', 'JA', 'FESTIVAL_EVENT', 1, TRUE),
    ('k-trip-spot', 'JA', 'TOURIST_SPOT', 2, TRUE),
    ('k-trip-spot', 'JA', 'CULTURAL_FACILITY', 3, TRUE),
    ('k-trip-spot', 'JA', 'LEISURE_SPORTS', 4, TRUE),
    ('k-trip-spot', 'JA', 'LODGING', 5, TRUE),
    ('k-trip-spot', 'JA', 'SHOPPING', 6, TRUE),
    ('k-trip-spot', 'JA', 'TRANSPORT', 7, TRUE),
    ('k-trip-spot', 'ZH', 'RESTAURANT', 0, TRUE),
    ('k-trip-spot', 'ZH', 'FESTIVAL_EVENT', 1, TRUE),
    ('k-trip-spot', 'ZH', 'TOURIST_SPOT', 2, TRUE),
    ('k-trip-spot', 'ZH', 'CULTURAL_FACILITY', 3, TRUE),
    ('k-trip-spot', 'ZH', 'LEISURE_SPORTS', 4, TRUE),
    ('k-trip-spot', 'ZH', 'LODGING', 5, TRUE),
    ('k-trip-spot', 'ZH', 'SHOPPING', 6, TRUE),
    ('k-trip-spot', 'ZH', 'TRANSPORT', 7, TRUE),
    ('demo-app', 'KO', 'SHOPPING', 0, TRUE),
    ('demo-app', 'KO', 'LEISURE_SPORTS', 1, TRUE);

-- 8-1) 개별 글 숨김. **community_post.hidden과 다른 개념이다** — 그쪽은 신고 누적으로
--      모두에게 감춰진 것이고, 이건 한 사람이 자기 화면에서만 감춘 것이다.
--
--      20번이 남의 글 2건을 숨긴 상태로 둔다:
--        - posts()          → 그 2건이 빠져 **12건 중 10건**만 보인다
--        - posts(hiddenByViewer: true) → 그 2건만 나온다(해제 화면)
--        - post(id:)        → 숨긴 글은 null이다
--
--      숨긴 글의 작성자(12·16)는 차단 대상이 아니다 — 그 사람의 **다른 글은 그대로 보인다**.
--      그것이 차단과 숨기기가 다른 축이라는 증거다(12번은 서울광장 글이 남는다).
INSERT INTO community_post_hide (member_id, post_id, created_at)
SELECT 20, p.id, now() - interval '30 minutes'
FROM community_post p
JOIN (VALUES
    ('경복궁 휴무일 주의하세요', 12),
    ('청계천 야경 사진', 16)
) AS v(title, author_id)
  ON p.title = v.title AND p.author_id = v.author_id;

-- 9) 태그. **사용자가 입력하는 값**이라 고정 목록이 아니다 — 같은 태그가 여러 글에 붙는
--    모습과, 대소문자가 달라도 같은 태그로 묶이는 것을 확인할 수 있게 넣는다.
INSERT INTO community_post_tag (post_id, name, normalized)
SELECT p.id, t.name, t.normalized
FROM community_post p
JOIN (VALUES
    ('경복궁 야간개장 후기', 20, '야간개장', '야간개장'),
    ('경복궁 야간개장 후기', 20, '한복', '한복'),
    ('북촌 골목 사진 몇 장', 20, '사진맛집', '사진맛집'),
    ('경복궁 휴무일 주의하세요', 12, '꿀팁', '꿀팁'),
    ('덕수궁 돌담길 야경 추천', 14, '야경', '야경'),
    -- 장소 없는 글에도 태그가 붙는다 — 태그는 spot과 무관하다.
    ('여행 짐 싸는 팁 공유해요', 14, '짐싸기', '짐싸기'),
    -- 대소문자가 다른 같은 태그. normalized가 같아 태그 검색에 함께 잡혀야 한다.
    ('청계천 야경 사진', 16, 'Seoul', 'seoul'),
    ('서울광장 잔디 개방 기간', 12, 'seoul', 'seoul')
) AS t(post_title, author_id, name, normalized)
  ON p.title = t.post_title AND p.author_id = t.author_id;

-- ============================================================
-- 확인용 쿼리 (실행되지 않는 주석이다. 필요할 때 복사해서 쓴다.)
--
-- 20번에게 보이는 글 — 16건 중 **10건**이어야 한다.
--   제외 6건 = 19의 2건 + 18의 1건(차단) + 17의 자동 숨김 1건 + 20이 개별로 숨긴 2건.
--   본인 글 4건은 다 보인다.
--
-- 개별 숨김만 확인하려면:
--   SELECT p.id, p.title FROM community_post p
--   JOIN community_post_hide h ON h.post_id = p.id AND h.member_id = 20;
--   → 2건(경복궁 휴무일, 청계천 야경). posts(hiddenByViewer: true)가 이 둘을 줘야 한다.
--
--   SELECT p.id, p.author_id, p.title, p.hidden, left(p.body, 30) AS body
--   FROM community_post p
--   WHERE p.deleted_at IS NULL
--     AND p.author_id NOT IN (
--       SELECT blocked_id FROM community_block WHERE blocker_id = 20
--       UNION
--       SELECT blocker_id FROM community_block WHERE blocked_id = 20
--     )
--     AND (p.hidden = FALSE OR p.author_id = 20)
--   ORDER BY p.created_at DESC;
--
-- 같은 조회를 17번으로 하면 자기 숨김 글이 하나 더 보인다(작성자 예외).
-- 차단 관계가 없는 사람(예: 12번)으로 보면 숨김 1건만 빠져 15건이 보인다.
--
-- 지역 피드 — 서울 시청 반경 5km. 해운대(11104) 글은 331km 밖이라 빠지고,
-- 장소 없는 글('여행 짐 싸는 팁')은 community_post_spot 행이 없어 애초에 조인되지 않는다.
--
--   SELECT p.id, s.title, p.title
--   FROM community_post p
--   JOIN community_post_spot ps ON ps.post_id = p.id
--   JOIN spot_catalog s ON s.id = ps.catalog_id
--   WHERE p.deleted_at IS NULL
--     AND ST_DWithin(s.location, ST_MakePoint(126.9780, 37.5665)::geography, 5000)
--   ORDER BY p.created_at DESC;
--
-- 댓글 수 정합성(R23) — 모든 글에서 declared = actual이어야 한다.
--
--   SELECT p.id, p.comment_count AS declared,
--          count(c.id) FILTER (WHERE NOT c.hidden AND c.deleted_at IS NULL) AS actual
--   FROM community_post p
--   LEFT JOIN community_comment c ON c.post_id = p.id
--   GROUP BY p.id, p.comment_count
--   HAVING p.comment_count <> count(c.id) FILTER (WHERE NOT c.hidden AND c.deleted_at IS NULL);
--
-- 수정된 글 — edited=TRUE가 정확히 1건이어야 한다(15번의 덕수궁 글).
--
--   SELECT id, title, edited, created_at < updated_at AS updated_later
--   FROM community_post WHERE edited;
--
-- 숨겨진 글은 수정할 수 없다 — 17번의 숨김 글로 editPost를 호출하면
-- POST_NOT_EDITABLE이 나와야 한다(서버 규칙, 시드로는 상태만 만들어 둔다).
--
-- 태그 — 'seoul'로 조회하면 대소문자가 달라도 2건이 잡혀야 한다.
--
--   SELECT t.name, t.normalized, p.title FROM community_post_tag t
--   JOIN community_post p ON p.id = t.post_id WHERE t.normalized = 'seoul';
--
-- 상단 칩 — X-App-Key와 Accept-Language 조합에 따라 다른 목록이 나와야 한다.
--   k-trip-spot + ko : 8건 (여행코스 있음, 교통 없음)
--   k-trip-spot + en : 8건 (교통 있음, 여행코스 없음)
--   demo-app    + ko : 2건
--   헤더 없음        : 기본 앱(k-trip-spot) + 폴백 언어(EN) = 8건
--
--   SELECT app_key, language, category, position FROM app_category
--   WHERE enabled ORDER BY app_key, language, position;
--
-- 신고 임계값 — 17번 숨김 글의 서로 다른 신고자가 5명이어야 한다.
-- 19번(MEMBER 대상)도 5명이지만 그 사람의 글은 hidden=FALSE로 남아야 한다(R22).
--
--   SELECT target_type, target_id, count(DISTINCT reporter_id) AS reporters
--   FROM community_report GROUP BY target_type, target_id ORDER BY reporters DESC;
-- ============================================================
