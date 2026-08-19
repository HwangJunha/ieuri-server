-- alarm 컨텍스트 신설 — 기기 푸시 토큰과 카테고리별 수신 설정(SPEC-0028).
--
-- **새 스키마를 만든다.** SPEC-0023이 나눈 다섯(auth·community·tour·category·batch)에
-- alarm이 없다. auth에 얹으면 auth 스키마에 사는 유일한 비-auth 표가 되어, 컨텍스트당
-- 스키마라는 V62의 전제가 이 한 곳에서만 깨진다. 알림을 별도 앱으로 떼어낼 때
-- 스키마째 옮기지 못하고 표를 골라내야 한다.
--
-- 스키마를 나눴으므로 member를 향한 **FK는 걸지 않는다**(V62 R4와 같은 판단). 탈퇴 연쇄는
-- MemberWithdrawn 이벤트를 구독해 애플리케이션이 지운다.

CREATE SCHEMA IF NOT EXISTS alarm;

-- ── 기기 토큰 ────────────────────────────────────────────────────────

CREATE TABLE alarm.device_token (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 클라이언트가 만들어 보관하는 기기 식별자. **재등록 판별 키다**(R1).
    --
    -- 유니크는 device_id 단독이고 (member_id, device_id)가 아니다. 한 기기를 다른 회원이
    -- 쓰기 시작하면 복합 키로는 이전 회원의 행이 남아 **엉뚱한 사람에게 발송된다**(AC1.4).
    -- 단독 유니크라야 업서트가 소유를 옮긴다.
    device_id  VARCHAR(255) NOT NULL,

    member_id  BIGINT       NOT NULL,

    -- FCM이 발급한 푸시 토큰. **암호화하지 않는다** — 원래 공개된 값이라 POL-012의 대상이
    -- 아니다. 다만 로그에는 원문을 남기지 않는다(POL-013).
    -- 토큰은 로테이션되므로 이 값은 갱신 대상이지 식별자가 아니다.
    fcm_token  TEXT         NOT NULL,

    -- 알림 제목 등 **서버가 조립하는 문구**의 언어. 사용자가 쓴 본문은 이 값과 무관하게
    -- 원문 그대로 나간다(R13). 기기 속성이라 회원이 아니라 이 표가 들고 있다 —
    -- 같은 회원이 기기마다 다른 언어를 쓰는 것이 정상이다.
    language   VARCHAR(10)  NOT NULL,

    platform   VARCHAR(10)  NOT NULL,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_alarm_device_token_device UNIQUE (device_id),
    CONSTRAINT ck_alarm_device_token_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_alarm_device_token_platform CHECK (platform IN ('IOS', 'ANDROID'))
);

-- 발송 경로. 수신자 한 명의 기기 전부를 한 번에 읽는다(AC5.3).
-- 탈퇴 연쇄 삭제(R3)도 이 색인을 쓴다.
CREATE INDEX idx_alarm_device_token_member ON alarm.device_token (member_id);

-- ── 수신 설정 ────────────────────────────────────────────────────────

CREATE TABLE alarm.notification_preference (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 기기가 아니라 **회원**에 묶인다. 재설치·기기 변경에도 유지되어야 한다(AC19.4).
    member_id BIGINT      NOT NULL,

    category  VARCHAR(20) NOT NULL,
    enabled   BOOLEAN     NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_alarm_notification_preference UNIQUE (member_id, category),
    CONSTRAINT ck_alarm_notification_preference_category
        CHECK (category IN ('MESSAGE', 'SPOT_QA'))
);

-- **행이 없는 회원은 켜진 것으로 읽는다**(AC10.3·AC19.1). 가입 시 기본 행을 만들지 않으며,
-- 따라서 기존 회원 백필도 없다 — 없을 때 켜짐으로 읽는 규칙이 백필을 통째로 대신한다.
-- 저장은 사용자가 토글을 처음 건드릴 때만 일어난다.
CREATE INDEX idx_alarm_notification_preference_member
    ON alarm.notification_preference (member_id);

-- ── 알림 제목 문구 ───────────────────────────────────────────────────
--
-- **푸시에는 사용자 본문을 싣지 않는다.** 그래서 이 제목이 알림의 유일한 내용이고,
-- 문구를 다듬는 일이 실제로 생긴다 — 배포 없이 고칠 수 있도록 표로 둔다.
--
-- 본문을 싣지 않는 이유는 잠금화면 노출이다. 누가 무엇을 보냈는지가 화면을 켜지 않아도
-- 보이면, 알림을 켠 대가로 대화 내용이 새어 나간다. 사용자는 앱을 열어 확인한다.

CREATE TABLE alarm.notification_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 알림 종류. 카테고리(MESSAGE·SPOT_QA)보다 잘다 — 카테고리는 사용자가 끄고 켜는 단위이고
    -- 이쪽은 문구 단위라, 답변과 답글이 한 카테고리이면서 다른 문구를 갖는다.
    notification_type VARCHAR(30) NOT NULL,

    -- 수신 기기의 언어. 회원이 아니라 기기의 속성이다(device_token.language와 같은 값).
    locale VARCHAR(10) NOT NULL,

    title VARCHAR(200) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_alarm_notification_message UNIQUE (notification_type, locale),
    CONSTRAINT ck_alarm_notification_message_type
        CHECK (notification_type IN ('DM', 'SPOT_QA_ANSWER', 'SPOT_QA_REPLY')),
    CONSTRAINT ck_alarm_notification_message_locale
        CHECK (locale IN ('EN', 'KO', 'JA', 'ZH'))
);

-- **행이 빠지면 그 언어로는 알림이 나가지 않는다.** 설정과 달리 기본값으로 대체할 수 없다 —
-- 문구가 없으면 보낼 것이 없기 때문이다. 그래서 여기서 3종 × 4언어를 빠짐없이 채운다.
--
-- ZH는 번체(正體)다 — 서비스 대상이 대만·홍콩이라 간체를 쓰지 않는다.
INSERT INTO alarm.notification_message (notification_type, locale, title) VALUES
    ('DM', 'KO', '새 메시지가 도착했어요'),
    ('DM', 'EN', 'You have a new message'),
    ('DM', 'JA', '新しいメッセージが届きました'),
    ('DM', 'ZH', '您有新訊息'),

    ('SPOT_QA_ANSWER', 'KO', '내 질문에 답변이 달렸어요'),
    ('SPOT_QA_ANSWER', 'EN', 'Your question has a new answer'),
    ('SPOT_QA_ANSWER', 'JA', 'あなたの質問に回答がつきました'),
    ('SPOT_QA_ANSWER', 'ZH', '您的提問有了新回覆'),

    ('SPOT_QA_REPLY', 'KO', '내 답변에 답글이 달렸어요'),
    ('SPOT_QA_REPLY', 'EN', 'Your answer has a new reply'),
    ('SPOT_QA_REPLY', 'JA', 'あなたの回答に返信がつきました'),
    ('SPOT_QA_REPLY', 'ZH', '您的回覆有了新留言');
