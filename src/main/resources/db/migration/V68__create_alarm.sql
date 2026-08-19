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
