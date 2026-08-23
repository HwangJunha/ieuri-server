-- 메인 DB 베이스라인. 이전 V1~V80을 하나로 접은 것이며, 접기 시점의 개발 클러스터
-- 스키마(2026-08-23, ieuri-db/ieuri-pg)를 pg_dump --schema-only로 그대로 옮겼다.
--
-- **접은 이유는 운영 최초 배포다.** 80개를 순서대로 적용하는 것은 이미 그 역사를 가진
-- 개발 DB에만 의미가 있다. 빈 운영 DB는 최종 형태만 필요하고, 중간 단계는 적용 시간과
-- 실패 지점만 늘린다. 히스토리는 git에 남아 있다.
--
-- **개발 DB는 이 파일을 적용하지 않는다.** 이미 V80까지 적용돼 있어 Flyway가 V1을
-- 스킵하며, 대신 기존 이력의 체크섬이 어긋난다 — 이력을 이 파일 하나로 치환하는 SQL을
-- 사용자가 직접 돌린다(problem/DB-BASELINE-2-repair.sql).
--
-- 옮기면서 손댄 것은 psql 전용 구문뿐이다:
--   - `\restrict`/`\unrestrict` 제거 — psql 메타커맨드라 JDBC가 구문 오류로 죽는다.
--   - `SET`·`set_config` 프리앰블 제거 — 특히 search_path를 ''로 비우는 줄은 뒤따르는
--     구문의 이름 해석을 깨뜨린다.
--
-- **`CREATE EXTENSION`은 여기 없다**(POL-034, 접기 전 V15·V44·V62가 같은 말을 했다).
-- postgis 활성화는 CNPG 오퍼레이터가 슈퍼유저로 수행하고(매니페스트 infra/cnpg/cluster의
-- `cluster.extensions`), 앱 롤 `ieuri`에는 그 권한이 없다 — **넣으면 첫 배포가 그 줄에서
-- `permission denied to create extension`으로 멈춘다**(실측 2026-08-23). 그래서 이 파일은
-- 확장이 이미 있는 DB를 전제하며, geography 컬럼이 그 전제 위에 선다.
--
-- 스키마 생성(`CREATE SCHEMA`)은 반대로 여기 있다 — 접기 전에도 V62·V68이 만들었고,
-- 앱 롤로 만들 수 있다. 매니페스트도 auth·community·tour·category·batch를 보장하지만
-- alarm은 목록에 없어, 그릇을 보장하는 쪽은 이 파일이다.
--
-- 논리 복제(search_pub)도 여기 없다. 접속 정보가 환경마다 달라 마이그레이션이 소유할 수
-- 없다 — 클러스터 구축 절차가 소유한다.

--
-- Name: alarm; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA alarm;

--
-- Name: auth; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA auth;

--
-- Name: batch; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA batch;

--
-- Name: category; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA category;

--
-- Name: community; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA community;

--
-- Name: tour; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA tour;

--
-- Name: community_post_spot_sync_location(); Type: FUNCTION; Schema: community; Owner: -
--

CREATE FUNCTION community.community_post_spot_sync_location() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.location := ST_MakePoint(NEW.map_x, NEW.map_y)::geography;
    RETURN NEW;
END;
$$;

--
-- Name: spot_catalog_sync_location(); Type: FUNCTION; Schema: tour; Owner: -
--

CREATE FUNCTION tour.spot_catalog_sync_location() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.map_x, NEW.map_y), 4326)::geography;
    RETURN NEW;
END;
$$;

--
-- Name: spot_sync_location(); Type: FUNCTION; Schema: tour; Owner: -
--

CREATE FUNCTION tour.spot_sync_location() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.location := ST_MakePoint(NEW.map_x, NEW.map_y)::geography;
    RETURN NEW;
END;
$$;

--
-- Name: device_token; Type: TABLE; Schema: alarm; Owner: -
--

CREATE TABLE alarm.device_token (
    id bigint NOT NULL,
    device_id character varying(255) NOT NULL,
    member_id bigint NOT NULL,
    fcm_token text NOT NULL,
    language character varying(10) NOT NULL,
    platform character varying(10) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_alarm_device_token_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[]))),
    CONSTRAINT ck_alarm_device_token_platform CHECK (((platform)::text = ANY ((ARRAY['IOS'::character varying, 'ANDROID'::character varying])::text[])))
);

--
-- Name: device_token_id_seq; Type: SEQUENCE; Schema: alarm; Owner: -
--

ALTER TABLE alarm.device_token ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME alarm.device_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: notification_message; Type: TABLE; Schema: alarm; Owner: -
--

CREATE TABLE alarm.notification_message (
    id bigint NOT NULL,
    notification_type character varying(30) NOT NULL,
    locale character varying(10) NOT NULL,
    title character varying(200) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    body character varying(200) NOT NULL,
    CONSTRAINT ck_alarm_notification_message_locale CHECK (((locale)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[]))),
    CONSTRAINT ck_alarm_notification_message_type CHECK (((notification_type)::text = ANY ((ARRAY['DM'::character varying, 'SPOT_QA_ANSWER'::character varying, 'SPOT_QA_REPLY'::character varying])::text[])))
);

--
-- Name: notification_message_id_seq; Type: SEQUENCE; Schema: alarm; Owner: -
--

ALTER TABLE alarm.notification_message ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME alarm.notification_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: notification_preference; Type: TABLE; Schema: alarm; Owner: -
--

CREATE TABLE alarm.notification_preference (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    category character varying(20) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_alarm_notification_preference_category CHECK (((category)::text = ANY ((ARRAY['MESSAGE'::character varying, 'SPOT_QA'::character varying])::text[])))
);

--
-- Name: notification_preference_id_seq; Type: SEQUENCE; Schema: alarm; Owner: -
--

ALTER TABLE alarm.notification_preference ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME alarm.notification_preference_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: email_verification; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.email_verification (
    id bigint NOT NULL,
    public_id uuid NOT NULL,
    member_id bigint NOT NULL,
    code_hash character varying(128) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    consumed boolean DEFAULT false NOT NULL,
    verified_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    purpose character varying(20) NOT NULL,
    target_email_encrypted character varying(512),
    CONSTRAINT ck_ev_purpose CHECK (((purpose)::text = ANY ((ARRAY['REGISTRATION'::character varying, 'EMAIL_CHANGE'::character varying])::text[]))),
    CONSTRAINT ck_ev_target_email CHECK ((((purpose)::text <> 'EMAIL_CHANGE'::text) OR (target_email_encrypted IS NOT NULL)))
);

--
-- Name: email_verification_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.email_verification ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.email_verification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.member (
    id bigint NOT NULL,
    provider character varying(20) NOT NULL,
    provider_user_id character varying(255) NOT NULL,
    email_encrypted character varying(512) NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    age_verified boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    suspended_at timestamp with time zone,
    suspended_until timestamp with time zone,
    suspension_reason character varying(500),
    password_hash character varying(255),
    provider_refresh_token_encrypted character varying(1024),
    email_identifier character varying(64) NOT NULL,
    CONSTRAINT ck_member_email_password CHECK ((((provider)::text <> 'EMAIL'::text) OR (password_hash IS NOT NULL))),
    CONSTRAINT ck_member_provider CHECK (((provider)::text = ANY ((ARRAY['APPLE'::character varying, 'GOOGLE'::character varying, 'KAKAO'::character varying, 'EMAIL'::character varying])::text[]))),
    CONSTRAINT ck_member_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);

--
-- Name: COLUMN member.email_identifier; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON COLUMN auth.member.email_identifier IS '정규화 이메일의 HMAC. (provider, email) 유니크 판정 전용이며 email_encrypted와 항상 함께 갱신된다.';

--
-- Name: member_audit_log; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.member_audit_log (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    event_type character varying(50) NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: member_audit_log_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.member_audit_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.member_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.member ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_profile; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.member_profile (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    nickname character varying(50) NOT NULL,
    profile_image_url character varying(1024),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: member_profile_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.member_profile ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.member_profile_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_role; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.member_role (
    member_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: member_terms_agreement; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.member_terms_agreement (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    terms_id bigint NOT NULL,
    terms_code character varying(50) NOT NULL,
    terms_version character varying(20) NOT NULL,
    agreed boolean NOT NULL,
    agreed_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: member_terms_agreement_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.member_terms_agreement ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.member_terms_agreement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: password_reset; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.password_reset (
    id bigint NOT NULL,
    public_id uuid NOT NULL,
    member_id bigint NOT NULL,
    code_hash character varying(128) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    consumed boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: password_reset_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.password_reset ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.password_reset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: permission; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.permission (
    id bigint NOT NULL,
    code character varying(100) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: permission_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.permission ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: role; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.role (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: role_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.role ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: role_permission; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);

--
-- Name: terms; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.terms (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    title character varying(200) NOT NULL,
    body text NOT NULL,
    version character varying(20) NOT NULL,
    required boolean NOT NULL,
    effective_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: terms_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

ALTER TABLE auth.terms ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME auth.terms_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: batch_job_execution; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_job_execution (
    job_execution_id bigint NOT NULL,
    version bigint,
    job_instance_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);

--
-- Name: batch_job_execution_context; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_job_execution_context (
    job_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);

--
-- Name: batch_job_execution_params; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_job_execution_params (
    job_execution_id bigint NOT NULL,
    parameter_name character varying(100) NOT NULL,
    parameter_type character varying(100) NOT NULL,
    parameter_value character varying(2500),
    identifying character(1) NOT NULL
);

--
-- Name: batch_job_execution_seq; Type: SEQUENCE; Schema: batch; Owner: -
--

CREATE SEQUENCE batch.batch_job_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: batch_job_instance; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_job_instance (
    job_instance_id bigint NOT NULL,
    version bigint,
    job_name character varying(100) NOT NULL,
    job_key character varying(32) NOT NULL
);

--
-- Name: batch_job_instance_seq; Type: SEQUENCE; Schema: batch; Owner: -
--

CREATE SEQUENCE batch.batch_job_instance_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: batch_step_execution; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_step_execution (
    step_execution_id bigint NOT NULL,
    version bigint NOT NULL,
    step_name character varying(100) NOT NULL,
    job_execution_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    status character varying(10),
    commit_count bigint,
    read_count bigint,
    filter_count bigint,
    write_count bigint,
    read_skip_count bigint,
    write_skip_count bigint,
    process_skip_count bigint,
    rollback_count bigint,
    exit_code character varying(2500),
    exit_message character varying(2500),
    last_updated timestamp without time zone
);

--
-- Name: batch_step_execution_context; Type: TABLE; Schema: batch; Owner: -
--

CREATE TABLE batch.batch_step_execution_context (
    step_execution_id bigint NOT NULL,
    short_context character varying(2500) NOT NULL,
    serialized_context text
);

--
-- Name: batch_step_execution_seq; Type: SEQUENCE; Schema: batch; Owner: -
--

CREATE SEQUENCE batch.batch_step_execution_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: app_category; Type: TABLE; Schema: category; Owner: -
--

CREATE TABLE category.app_category (
    id bigint NOT NULL,
    app_key character varying(50) NOT NULL,
    language character varying(2) NOT NULL,
    active boolean DEFAULT true CONSTRAINT app_category_enabled_not_null NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    category_id bigint NOT NULL,
    CONSTRAINT ck_app_category_app_key_not_blank CHECK ((btrim((app_key)::text) <> ''::text)),
    CONSTRAINT ck_app_category_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[])))
);

--
-- Name: app_category_id_seq; Type: SEQUENCE; Schema: category; Owner: -
--

ALTER TABLE category.app_category ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME category.app_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: category; Type: TABLE; Schema: category; Owner: -
--

CREATE TABLE category.category (
    code character varying(30) NOT NULL,
    icon_url character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    id bigint NOT NULL,
    CONSTRAINT ck_category_code_format CHECK (((code)::text ~ '^[A-Z][A-Z0-9_]*$'::text)),
    CONSTRAINT ck_category_code_not_blank CHECK ((btrim((code)::text) <> ''::text))
);

--
-- Name: category_external_mapping; Type: TABLE; Schema: category; Owner: -
--

CREATE TABLE category.category_external_mapping (
    id bigint NOT NULL,
    category_id bigint NOT NULL,
    provider character varying(30) NOT NULL,
    codeset character varying(20) NOT NULL,
    external_code character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: category_external_mapping_id_seq; Type: SEQUENCE; Schema: category; Owner: -
--

ALTER TABLE category.category_external_mapping ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME category.category_external_mapping_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: category_id_seq; Type: SEQUENCE; Schema: category; Owner: -
--

ALTER TABLE category.category ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME category.category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: category_translation; Type: TABLE; Schema: category; Owner: -
--

CREATE TABLE category.category_translation (
    language character varying(2) NOT NULL,
    label character varying(50) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    category_id bigint NOT NULL,
    CONSTRAINT ck_category_translation_label_not_blank CHECK ((btrim((label)::text) <> ''::text)),
    CONSTRAINT ck_category_translation_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[])))
);

--
-- Name: community_block; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_block (
    id bigint NOT NULL,
    blocker_id bigint NOT NULL,
    blocked_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_block_not_self CHECK ((blocker_id <> blocked_id))
);

--
-- Name: community_block_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_block ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_block_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_comment; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_comment (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    author_id bigint NOT NULL,
    body text NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    parent_comment_id bigint,
    CONSTRAINT ck_community_comment_body_not_blank CHECK ((btrim(body) <> ''::text)),
    CONSTRAINT ck_community_comment_parent_not_self CHECK (((parent_comment_id IS NULL) OR (parent_comment_id <> id)))
);

--
-- Name: community_comment_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_comment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_comment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_conversation; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_conversation (
    id bigint NOT NULL,
    member_low bigint NOT NULL,
    member_high bigint NOT NULL,
    last_message_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_read_message_id_low bigint,
    last_read_message_id_high bigint,
    CONSTRAINT ck_community_conversation_members CHECK ((member_low < member_high))
);

--
-- Name: community_conversation_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_conversation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_message; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_message (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    sender_id bigint NOT NULL,
    body_encrypted text NOT NULL,
    photo_object_key character varying(512),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_message_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_message ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_message_quote; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_message_quote (
    id bigint NOT NULL,
    message_id bigint NOT NULL,
    quote_type character varying(30) NOT NULL,
    target_id bigint NOT NULL,
    title_snapshot character varying(255) NOT NULL,
    primary_excerpt_encrypted text NOT NULL,
    secondary_excerpt_encrypted text,
    context_id bigint,
    parent_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_message_quote_type CHECK (((quote_type)::text = 'SPOT_ANSWER'::text))
);

--
-- Name: community_message_quote_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_message_quote ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_message_quote_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_message_report; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_message_report (
    id bigint NOT NULL,
    reporter_id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    reason character varying(30) NOT NULL,
    detail text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_message_report_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_message_report ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_message_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_message_report_item; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_message_report_item (
    id bigint NOT NULL,
    report_id bigint NOT NULL,
    message_id bigint NOT NULL,
    reporter_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_message_report_item_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_message_report_item ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_message_report_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_orphan_photo_object; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_orphan_photo_object (
    id bigint NOT NULL,
    object_key character varying(512) NOT NULL,
    source character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_orphan_photo_object_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_orphan_photo_object ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_orphan_photo_object_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_post; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post (
    id bigint NOT NULL,
    author_id bigint NOT NULL,
    title character varying(100) NOT NULL,
    body text NOT NULL,
    like_count integer DEFAULT 0 NOT NULL,
    comment_count integer DEFAULT 0 NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    edited boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    view_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_community_post_body_not_blank CHECK ((btrim(body) <> ''::text)),
    CONSTRAINT ck_community_post_comment_count CHECK ((comment_count >= 0)),
    CONSTRAINT ck_community_post_like_count CHECK ((like_count >= 0)),
    CONSTRAINT ck_community_post_title_not_blank CHECK ((btrim((title)::text) <> ''::text)),
    CONSTRAINT ck_community_post_view_count CHECK ((view_count >= 0))
);

--
-- Name: community_post_chip; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_chip (
    post_id bigint NOT NULL,
    category character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_post_hide; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_hide (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    post_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_post_hide_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_post_hide ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_post_hide_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_post_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_post ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_post_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_post_like; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_like (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    member_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: community_post_like_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_post_like ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_post_like_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_post_photo; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_photo (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    object_key character varying(512) NOT NULL,
    "position" smallint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_post_photo_position CHECK ((("position" >= 0) AND ("position" <= 4)))
);

--
-- Name: community_post_photo_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_post_photo ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_post_photo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_post_spot; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_spot (
    post_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    map_x double precision NOT NULL,
    map_y double precision NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    catalog_id bigint NOT NULL,
    CONSTRAINT ck_community_post_spot_map_x CHECK (((map_x >= ('-180'::integer)::double precision) AND (map_x <= (180)::double precision))),
    CONSTRAINT ck_community_post_spot_map_y CHECK (((map_y >= ('-90'::integer)::double precision) AND (map_y <= (90)::double precision)))
);

--
-- Name: community_post_tag; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_post_tag (
    id bigint NOT NULL,
    post_id bigint NOT NULL,
    name character varying(30) NOT NULL,
    normalized character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_post_tag_name_not_blank CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT ck_community_post_tag_normalized_not_blank CHECK ((btrim((normalized)::text) <> ''::text))
);

--
-- Name: community_post_tag_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_post_tag ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_post_tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_report; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_report (
    id bigint NOT NULL,
    reporter_id bigint NOT NULL,
    target_type character varying(30) NOT NULL,
    target_id bigint NOT NULL,
    reason character varying(30) NOT NULL,
    detail text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_report_detail_for_other CHECK ((((reason)::text <> 'OTHER'::text) OR (btrim(COALESCE(detail, ''::text)) <> ''::text))),
    CONSTRAINT ck_community_report_reason CHECK (((reason)::text = ANY ((ARRAY['SPAM'::character varying, 'ABUSE'::character varying, 'INAPPROPRIATE_CONTENT'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_community_report_target_type CHECK (((target_type)::text = ANY (ARRAY[('POST'::character varying)::text, ('COMMENT'::character varying)::text, ('MEMBER'::character varying)::text, ('SPOT_QUESTION'::character varying)::text, ('SPOT_ANSWER'::character varying)::text, ('SPOT_ANSWER_REPLY'::character varying)::text])))
);

--
-- Name: community_report_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_report ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_spot_answer; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_spot_answer (
    id bigint NOT NULL,
    question_id bigint NOT NULL,
    catalog_id bigint NOT NULL,
    author_id bigint NOT NULL,
    body text NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_spot_answer_body_not_blank CHECK ((btrim(body) <> ''::text))
);

--
-- Name: community_spot_answer_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_spot_answer ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_spot_answer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_spot_answer_reply; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_spot_answer_reply (
    id bigint NOT NULL,
    answer_id bigint NOT NULL,
    catalog_id bigint NOT NULL,
    author_id bigint NOT NULL,
    body text NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_spot_answer_reply_body_not_blank CHECK ((btrim(body) <> ''::text))
);

--
-- Name: community_spot_answer_reply_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_spot_answer_reply ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_spot_answer_reply_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_spot_catalog_language; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_spot_catalog_language (
    catalog_id bigint NOT NULL,
    language character varying(2) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_spot_catalog_language_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[])))
);

--
-- Name: community_spot_qa_photo; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_spot_qa_photo (
    id bigint NOT NULL,
    owner_type character varying(10) NOT NULL,
    owner_id bigint NOT NULL,
    object_key character varying(512) NOT NULL,
    "position" smallint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_spot_qa_photo_owner_type CHECK (((owner_type)::text = ANY ((ARRAY['QUESTION'::character varying, 'ANSWER'::character varying, 'REPLY'::character varying])::text[]))),
    CONSTRAINT ck_community_spot_qa_photo_position CHECK ((("position" >= 0) AND ("position" <= 4)))
);

--
-- Name: community_spot_qa_photo_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_spot_qa_photo ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_spot_qa_photo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: community_spot_question; Type: TABLE; Schema: community; Owner: -
--

CREATE TABLE community.community_spot_question (
    id bigint NOT NULL,
    catalog_id bigint NOT NULL,
    author_id bigint NOT NULL,
    body text NOT NULL,
    resolved boolean DEFAULT false NOT NULL,
    answer_count integer DEFAULT 0 NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_community_spot_question_answer_count CHECK ((answer_count >= 0)),
    CONSTRAINT ck_community_spot_question_body_not_blank CHECK ((btrim(body) <> ''::text))
);

--
-- Name: community_spot_question_id_seq; Type: SEQUENCE; Schema: community; Owner: -
--

ALTER TABLE community.community_spot_question ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME community.community_spot_question_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: area; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.area (
    area_cd character varying(2) NOT NULL,
    name_ko character varying(128) NOT NULL,
    name_en character varying(128) NOT NULL,
    name_ja character varying(128),
    name_zh character varying(128)
);

--
-- Name: congestion_area; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.congestion_area (
    area_cd character varying(16) NOT NULL,
    category character varying(32) NOT NULL,
    name_ko character varying(128) NOT NULL,
    name_en character varying(128) NOT NULL,
    name_ja character varying(128),
    name_zh character varying(128),
    boundary public.geography(Polygon,4326) NOT NULL
);

--
-- Name: TABLE congestion_area; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.congestion_area IS '서울시 실시간 도시데이터 121개 구역의 정적 메타데이터. 실시간 값은 담지 않는다(캐시 전용).';

--
-- Name: district; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.district (
    code character varying(5) CONSTRAINT sigungu_code_not_null NOT NULL,
    area_cd character varying(2) CONSTRAINT sigungu_province_code_not_null NOT NULL,
    name_ko character varying(128) CONSTRAINT sigungu_name_ko_not_null NOT NULL,
    name_en character varying(128) CONSTRAINT sigungu_name_en_not_null NOT NULL,
    name_ja character varying(128),
    name_zh character varying(128),
    boundary public.geography(MultiPolygon,4326) CONSTRAINT sigungu_boundary_not_null NOT NULL,
    center public.geography(Point,4326) CONSTRAINT sigungu_center_not_null NOT NULL,
    representative_location public.geography(Point,4326),
    representative_spot_count integer,
    representative_updated_at timestamp with time zone
);

--
-- Name: TABLE district; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.district IS '시군구 252건. 행정표준코드 5자리가 PK이며 KTO 4종 API의 공통 키다(SPEC-0015).';

--
-- Name: COLUMN district.representative_location; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON COLUMN tour.district.representative_location IS 'spot 밀집 지점에서 고른 실제 spot 좌표. NULL이면 선정 실패이며 추천에서 제외된다.';

--
-- Name: COLUMN district.representative_spot_count; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON COLUMN tour.district.representative_spot_count IS '선정 근거가 된 밀집 격자 안의 spot 수. 선정 기준 미달 여부를 사후에 확인하는 용도다.';

--
-- Name: COLUMN district.representative_updated_at; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON COLUMN tour.district.representative_updated_at IS '대표 위치를 마지막으로 갱신한 시각. 배치가 돌지 않고 있는 것을 관측하는 용도다.';

--
-- Name: kto_concentration_rate; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.kto_concentration_rate (
    id bigint NOT NULL,
    district_code character varying(5) CONSTRAINT kto_concentration_rate_sigungu_code_not_null NOT NULL,
    tourist_spot_name character varying(256) NOT NULL,
    target_date date NOT NULL,
    cnctr_rate numeric NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE kto_concentration_rate; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.kto_concentration_rate IS 'KTO 관광지별 일간 집중률 예측. 시군구가 아니라 관광지 단위 값이라 지역 점수로 쓰려면 집계가 필요하다. FK 없음(R9).';

--
-- Name: kto_concentration_rate_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.kto_concentration_rate ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME tour.kto_concentration_rate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: kto_spending_intensity; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.kto_spending_intensity (
    id bigint NOT NULL,
    district_code character varying(5) CONSTRAINT kto_spending_intensity_sigungu_code_not_null NOT NULL,
    base_month date NOT NULL,
    indicator_cd character varying(32) NOT NULL,
    indicator_value numeric NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE kto_spending_intensity; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.kto_spending_intensity IS 'KTO 시군구 월별 소비 강도. base_month는 해당 월 1일로 정규화한 date. FK 없음(R9).';

--
-- Name: kto_spending_intensity_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.kto_spending_intensity ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME tour.kto_spending_intensity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: kto_stay_intensity; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.kto_stay_intensity (
    id bigint NOT NULL,
    district_code character varying(5) CONSTRAINT kto_stay_intensity_sigungu_code_not_null NOT NULL,
    base_month date NOT NULL,
    indicator_cd character varying(32) NOT NULL,
    indicator_value numeric NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE kto_stay_intensity; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.kto_stay_intensity IS 'KTO 시군구 월별 체류 강도. base_month는 해당 월 1일로 정규화한 date. FK 없음(R9).';

--
-- Name: kto_stay_intensity_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.kto_stay_intensity ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME tour.kto_stay_intensity_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: kto_visitor_count; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.kto_visitor_count (
    id bigint NOT NULL,
    district_code character varying(5) CONSTRAINT kto_visitor_count_sigungu_code_not_null NOT NULL,
    base_date date NOT NULL,
    visitor_type character varying(32) NOT NULL,
    tou_num numeric NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE kto_visitor_count; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON TABLE tour.kto_visitor_count IS 'KTO 시군구 일별 방문자수. sigungu.code를 참조하지만 FK는 없다(R9 — 알 수 없는 코드를 건너뛰기 위함).';

--
-- Name: kto_visitor_count_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.kto_visitor_count ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME tour.kto_visitor_count_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: map_cell; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.map_cell (
    id bigint NOT NULL,
    name_ko character varying(128) NOT NULL,
    name_en character varying(128) NOT NULL,
    name_ja character varying(128),
    name_zh character varying(128)
);

--
-- Name: map_cell_district; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.map_cell_district (
    cell_id bigint NOT NULL,
    district_code character varying(5) NOT NULL
);

--
-- Name: map_cell_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.map_cell ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.map_cell_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: map_cell_image; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.map_cell_image (
    id bigint NOT NULL,
    cell_id bigint NOT NULL,
    display_order integer NOT NULL,
    image_url character varying(500) NOT NULL,
    thumbnail_url character varying(500),
    caption character varying(255) NOT NULL,
    kind character varying(20) NOT NULL,
    copyright character varying(20) NOT NULL,
    content_id character varying(20),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_map_cell_image_copyright CHECK (((copyright)::text = ANY ((ARRAY['TYPE1'::character varying, 'TYPE3'::character varying, 'UNSPECIFIED'::character varying])::text[]))),
    CONSTRAINT ck_map_cell_image_display_order CHECK ((display_order >= 0)),
    CONSTRAINT ck_map_cell_image_kind CHECK (((kind)::text = ANY ((ARRAY['LANDMARK'::character varying, 'SPECIALTY'::character varying])::text[])))
);

--
-- Name: map_cell_image_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.map_cell_image ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.map_cell_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: map_relation; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.map_relation (
    id bigint NOT NULL,
    owner_id bigint NOT NULL,
    counterpart_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_map_relation_not_self CHECK ((owner_id <> counterpart_id))
);

--
-- Name: map_relation_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.map_relation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.map_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: map_share_link; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.map_share_link (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    token character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    revoked_at timestamp with time zone
);

--
-- Name: map_share_link_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.map_share_link ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.map_share_link_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: member_district_visit; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.member_district_visit (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    district_code character varying(5) NOT NULL
);

--
-- Name: member_district_visit_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.member_district_visit ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.member_district_visit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot (
    id bigint NOT NULL,
    content_id character varying(32) NOT NULL,
    language character varying(2) NOT NULL,
    title character varying(255) NOT NULL,
    address character varying(500),
    thumbnail_image_url character varying(500) NOT NULL,
    map_x double precision NOT NULL,
    map_y double precision NOT NULL,
    category character varying(30) NOT NULL,
    content_type_id integer NOT NULL,
    copyright character varying(20) NOT NULL,
    last_synced_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    catalog_id bigint,
    CONSTRAINT ck_spot_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[]))),
    CONSTRAINT ck_spot_map_x CHECK (((map_x >= ('-180'::integer)::double precision) AND (map_x <= (180)::double precision))),
    CONSTRAINT ck_spot_map_y CHECK (((map_y >= ('-90'::integer)::double precision) AND (map_y <= (90)::double precision))),
    CONSTRAINT ck_spot_thumbnail_not_blank CHECK ((btrim((thumbnail_image_url)::text) <> ''::text))
);

--
-- Name: spot_catalog; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_catalog (
    id bigint NOT NULL,
    map_x double precision NOT NULL,
    map_y double precision NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    thumbnail_image_url character varying(500) NOT NULL,
    category character varying(30) NOT NULL,
    copyright character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    district_code character varying(5),
    CONSTRAINT ck_spot_catalog_coordinates_in_korea CHECK ((((map_x >= (124)::double precision) AND (map_x <= (132)::double precision)) AND ((map_y >= (33)::double precision) AND (map_y <= (39)::double precision))))
);

--
-- Name: spot_catalog_festival; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_catalog_festival (
    catalog_id bigint NOT NULL,
    event_start_date date NOT NULL,
    event_end_date date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_spot_catalog_festival_period CHECK ((event_end_date >= event_start_date))
);

--
-- Name: spot_catalog_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_catalog ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_catalog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_catalog_translation; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_catalog_translation (
    id bigint NOT NULL,
    catalog_id bigint NOT NULL,
    language character varying(2) NOT NULL,
    title character varying(255) NOT NULL,
    address character varying(500),
    map_x double precision NOT NULL,
    map_y double precision NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    category character varying(30) NOT NULL,
    visible boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_spot_catalog_translation_language CHECK (((language)::text = ANY ((ARRAY['KO'::character varying, 'EN'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[]))),
    CONSTRAINT ck_spot_catalog_translation_title_not_blank CHECK ((btrim((title)::text) <> ''::text))
);

--
-- Name: spot_catalog_translation_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_catalog_translation ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_catalog_translation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_detail; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_detail (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    category character varying(30) NOT NULL,
    thumbnail_image_url character varying(500) NOT NULL,
    overview text,
    address character varying(500),
    homepage text,
    tel character varying(255),
    copyright character varying(20) NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    spot_id bigint NOT NULL,
    body_absent boolean DEFAULT false NOT NULL
);

--
-- Name: COLUMN spot_detail.body_absent; Type: COMMENT; Schema: tour; Owner: -
--

COMMENT ON COLUMN tour.spot_detail.body_absent IS '업스트림에 상세 본문이 없음을 확인한 행. true면 title·category·thumbnail_image_url·copyright는 spot 행에서 빌려온 값이고 overview·homepage·tel은 null이다. fetched_at 기준 신선도가 지나면 재조회된다.';

--
-- Name: spot_detail_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_detail ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_detail_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_detail_image; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_detail_image (
    id bigint NOT NULL,
    display_order integer NOT NULL,
    image_url character varying(500) NOT NULL,
    thumbnail_url character varying(500),
    caption character varying(500),
    copyright character varying(20) NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    spot_id bigint NOT NULL
);

--
-- Name: spot_detail_image_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_detail_image ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_detail_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_detail_info; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_detail_info (
    id bigint NOT NULL,
    category character varying(30) NOT NULL,
    display_order integer NOT NULL,
    name character varying(255) NOT NULL,
    text text NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    spot_id bigint NOT NULL
);

--
-- Name: spot_detail_info_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_detail_info ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_detail_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_detail_intro; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_detail_intro (
    id bigint NOT NULL,
    category character varying(30) NOT NULL,
    business_hours text,
    closed_days text,
    parking text,
    inquiry_phone text,
    fee text,
    pet_allowed text,
    credit_card_accepted text,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    spot_id bigint NOT NULL
);

--
-- Name: spot_detail_intro_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_detail_intro ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_detail_intro_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_festival; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_festival (
    id bigint NOT NULL,
    event_start_date date NOT NULL,
    event_end_date date NOT NULL,
    progress_type character varying(50),
    festival_type character varying(50),
    last_synced_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    spot_id bigint NOT NULL,
    CONSTRAINT ck_spot_festival_period CHECK ((event_end_date >= event_start_date))
);

--
-- Name: spot_festival_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_festival ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_festival_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: spot_reaction; Type: TABLE; Schema: tour; Owner: -
--

CREATE TABLE tour.spot_reaction (
    id bigint NOT NULL,
    member_id bigint NOT NULL,
    reaction character varying(10) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    catalog_id bigint NOT NULL,
    language character varying(2) NOT NULL,
    CONSTRAINT ck_spot_reaction_language CHECK (((language)::text = ANY ((ARRAY['EN'::character varying, 'KO'::character varying, 'JA'::character varying, 'ZH'::character varying])::text[]))),
    CONSTRAINT ck_spot_reaction_reaction CHECK (((reaction)::text = ANY ((ARRAY['LIKE'::character varying, 'DISLIKE'::character varying])::text[])))
);

--
-- Name: spot_reaction_id_seq; Type: SEQUENCE; Schema: tour; Owner: -
--

ALTER TABLE tour.spot_reaction ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME tour.spot_reaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

--
-- Name: device_token device_token_pkey; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.device_token
    ADD CONSTRAINT device_token_pkey PRIMARY KEY (id);

--
-- Name: notification_message notification_message_pkey; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.notification_message
    ADD CONSTRAINT notification_message_pkey PRIMARY KEY (id);

--
-- Name: notification_preference notification_preference_pkey; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.notification_preference
    ADD CONSTRAINT notification_preference_pkey PRIMARY KEY (id);

--
-- Name: device_token uq_alarm_device_token_device; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.device_token
    ADD CONSTRAINT uq_alarm_device_token_device UNIQUE (device_id);

--
-- Name: notification_message uq_alarm_notification_message; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.notification_message
    ADD CONSTRAINT uq_alarm_notification_message UNIQUE (notification_type, locale);

--
-- Name: notification_preference uq_alarm_notification_preference; Type: CONSTRAINT; Schema: alarm; Owner: -
--

ALTER TABLE ONLY alarm.notification_preference
    ADD CONSTRAINT uq_alarm_notification_preference UNIQUE (member_id, category);

--
-- Name: email_verification email_verification_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.email_verification
    ADD CONSTRAINT email_verification_pkey PRIMARY KEY (id);

--
-- Name: member_audit_log member_audit_log_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_audit_log
    ADD CONSTRAINT member_audit_log_pkey PRIMARY KEY (id);

--
-- Name: member member_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member
    ADD CONSTRAINT member_pkey PRIMARY KEY (id);

--
-- Name: member_profile member_profile_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_profile
    ADD CONSTRAINT member_profile_pkey PRIMARY KEY (id);

--
-- Name: member_terms_agreement member_terms_agreement_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_terms_agreement
    ADD CONSTRAINT member_terms_agreement_pkey PRIMARY KEY (id);

--
-- Name: password_reset password_reset_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.password_reset
    ADD CONSTRAINT password_reset_pkey PRIMARY KEY (id);

--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);

--
-- Name: member_role pk_member_role; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_role
    ADD CONSTRAINT pk_member_role PRIMARY KEY (member_id, role_id);

--
-- Name: role_permission pk_role_permission; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.role_permission
    ADD CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id);

--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);

--
-- Name: terms terms_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.terms
    ADD CONSTRAINT terms_pkey PRIMARY KEY (id);

--
-- Name: email_verification uq_email_verification_public; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.email_verification
    ADD CONSTRAINT uq_email_verification_public UNIQUE (public_id);

--
-- Name: member_profile uq_member_profile_member; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_profile
    ADD CONSTRAINT uq_member_profile_member UNIQUE (member_id);

--
-- Name: member_profile uq_member_profile_nickname; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_profile
    ADD CONSTRAINT uq_member_profile_nickname UNIQUE (nickname);

--
-- Name: member uq_member_provider_email; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member
    ADD CONSTRAINT uq_member_provider_email UNIQUE (provider, email_identifier);

--
-- Name: member uq_member_provider_user; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member
    ADD CONSTRAINT uq_member_provider_user UNIQUE (provider, provider_user_id);

--
-- Name: member_terms_agreement uq_mta_member_terms; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_terms_agreement
    ADD CONSTRAINT uq_mta_member_terms UNIQUE (member_id, terms_id);

--
-- Name: password_reset uq_password_reset_public; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.password_reset
    ADD CONSTRAINT uq_password_reset_public UNIQUE (public_id);

--
-- Name: permission uq_permission_code; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.permission
    ADD CONSTRAINT uq_permission_code UNIQUE (code);

--
-- Name: role uq_role_code; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.role
    ADD CONSTRAINT uq_role_code UNIQUE (code);

--
-- Name: terms uq_terms_code_version; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.terms
    ADD CONSTRAINT uq_terms_code_version UNIQUE (code, version);

--
-- Name: batch_job_execution_context batch_job_execution_context_pkey; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_execution_context
    ADD CONSTRAINT batch_job_execution_context_pkey PRIMARY KEY (job_execution_id);

--
-- Name: batch_job_execution batch_job_execution_pkey; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_execution
    ADD CONSTRAINT batch_job_execution_pkey PRIMARY KEY (job_execution_id);

--
-- Name: batch_job_instance batch_job_instance_pkey; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_instance
    ADD CONSTRAINT batch_job_instance_pkey PRIMARY KEY (job_instance_id);

--
-- Name: batch_step_execution_context batch_step_execution_context_pkey; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_step_execution_context
    ADD CONSTRAINT batch_step_execution_context_pkey PRIMARY KEY (step_execution_id);

--
-- Name: batch_step_execution batch_step_execution_pkey; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_step_execution
    ADD CONSTRAINT batch_step_execution_pkey PRIMARY KEY (step_execution_id);

--
-- Name: batch_job_instance job_inst_un; Type: CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_instance
    ADD CONSTRAINT job_inst_un UNIQUE (job_name, job_key);

--
-- Name: app_category app_category_pkey; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.app_category
    ADD CONSTRAINT app_category_pkey PRIMARY KEY (id);

--
-- Name: category_external_mapping category_external_mapping_pkey; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_external_mapping
    ADD CONSTRAINT category_external_mapping_pkey PRIMARY KEY (id);

--
-- Name: category category_pkey; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);

--
-- Name: category_translation category_translation_pkey; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_translation
    ADD CONSTRAINT category_translation_pkey PRIMARY KEY (category_id, language);

--
-- Name: app_category uq_app_category; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.app_category
    ADD CONSTRAINT uq_app_category UNIQUE (app_key, language, category_id);

--
-- Name: category uq_category_code; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category
    ADD CONSTRAINT uq_category_code UNIQUE (code);

--
-- Name: category_external_mapping uq_category_external_code; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_external_mapping
    ADD CONSTRAINT uq_category_external_code UNIQUE (provider, codeset, external_code);

--
-- Name: category_external_mapping uq_category_external_mapping; Type: CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_external_mapping
    ADD CONSTRAINT uq_category_external_mapping UNIQUE (provider, codeset, category_id);

--
-- Name: community_block community_block_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_block
    ADD CONSTRAINT community_block_pkey PRIMARY KEY (id);

--
-- Name: community_comment community_comment_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_comment
    ADD CONSTRAINT community_comment_pkey PRIMARY KEY (id);

--
-- Name: community_conversation community_conversation_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_conversation
    ADD CONSTRAINT community_conversation_pkey PRIMARY KEY (id);

--
-- Name: community_message community_message_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message
    ADD CONSTRAINT community_message_pkey PRIMARY KEY (id);

--
-- Name: community_message_quote community_message_quote_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_quote
    ADD CONSTRAINT community_message_quote_pkey PRIMARY KEY (id);

--
-- Name: community_message_report_item community_message_report_item_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report_item
    ADD CONSTRAINT community_message_report_item_pkey PRIMARY KEY (id);

--
-- Name: community_message_report community_message_report_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report
    ADD CONSTRAINT community_message_report_pkey PRIMARY KEY (id);

--
-- Name: community_orphan_photo_object community_orphan_photo_object_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_orphan_photo_object
    ADD CONSTRAINT community_orphan_photo_object_pkey PRIMARY KEY (id);

--
-- Name: community_post_chip community_post_chip_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_chip
    ADD CONSTRAINT community_post_chip_pkey PRIMARY KEY (post_id);

--
-- Name: community_post_hide community_post_hide_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_hide
    ADD CONSTRAINT community_post_hide_pkey PRIMARY KEY (id);

--
-- Name: community_post_like community_post_like_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_like
    ADD CONSTRAINT community_post_like_pkey PRIMARY KEY (id);

--
-- Name: community_post_photo community_post_photo_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_photo
    ADD CONSTRAINT community_post_photo_pkey PRIMARY KEY (id);

--
-- Name: community_post community_post_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post
    ADD CONSTRAINT community_post_pkey PRIMARY KEY (id);

--
-- Name: community_post_spot community_post_spot_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_spot
    ADD CONSTRAINT community_post_spot_pkey PRIMARY KEY (post_id);

--
-- Name: community_post_tag community_post_tag_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_tag
    ADD CONSTRAINT community_post_tag_pkey PRIMARY KEY (id);

--
-- Name: community_report community_report_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_report
    ADD CONSTRAINT community_report_pkey PRIMARY KEY (id);

--
-- Name: community_spot_answer community_spot_answer_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_answer
    ADD CONSTRAINT community_spot_answer_pkey PRIMARY KEY (id);

--
-- Name: community_spot_answer_reply community_spot_answer_reply_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_answer_reply
    ADD CONSTRAINT community_spot_answer_reply_pkey PRIMARY KEY (id);

--
-- Name: community_spot_qa_photo community_spot_qa_photo_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_qa_photo
    ADD CONSTRAINT community_spot_qa_photo_pkey PRIMARY KEY (id);

--
-- Name: community_spot_question community_spot_question_pkey; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_question
    ADD CONSTRAINT community_spot_question_pkey PRIMARY KEY (id);

--
-- Name: community_spot_catalog_language pk_community_spot_catalog_language; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_catalog_language
    ADD CONSTRAINT pk_community_spot_catalog_language PRIMARY KEY (catalog_id, language);

--
-- Name: community_block uq_community_block; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_block
    ADD CONSTRAINT uq_community_block UNIQUE (blocker_id, blocked_id);

--
-- Name: community_conversation uq_community_conversation_pair; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_conversation
    ADD CONSTRAINT uq_community_conversation_pair UNIQUE (member_low, member_high);

--
-- Name: community_message_report_item uq_community_message_report_item; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report_item
    ADD CONSTRAINT uq_community_message_report_item UNIQUE (reporter_id, message_id);

--
-- Name: community_orphan_photo_object uq_community_orphan_photo_object; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_orphan_photo_object
    ADD CONSTRAINT uq_community_orphan_photo_object UNIQUE (object_key);

--
-- Name: community_post_hide uq_community_post_hide; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_hide
    ADD CONSTRAINT uq_community_post_hide UNIQUE (member_id, post_id);

--
-- Name: community_post_like uq_community_post_like; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_like
    ADD CONSTRAINT uq_community_post_like UNIQUE (post_id, member_id);

--
-- Name: community_post_photo uq_community_post_photo_position; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_photo
    ADD CONSTRAINT uq_community_post_photo_position UNIQUE (post_id, "position");

--
-- Name: community_post_tag uq_community_post_tag; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_tag
    ADD CONSTRAINT uq_community_post_tag UNIQUE (post_id, normalized);

--
-- Name: community_report uq_community_report; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_report
    ADD CONSTRAINT uq_community_report UNIQUE (reporter_id, target_type, target_id);

--
-- Name: community_spot_qa_photo uq_community_spot_qa_photo_position; Type: CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_qa_photo
    ADD CONSTRAINT uq_community_spot_qa_photo_position UNIQUE (owner_type, owner_id, "position");

--
-- Name: area area_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.area
    ADD CONSTRAINT area_pkey PRIMARY KEY (area_cd);

--
-- Name: congestion_area congestion_area_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.congestion_area
    ADD CONSTRAINT congestion_area_pkey PRIMARY KEY (area_cd);

--
-- Name: kto_concentration_rate kto_concentration_rate_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_concentration_rate
    ADD CONSTRAINT kto_concentration_rate_pkey PRIMARY KEY (id);

--
-- Name: kto_spending_intensity kto_spending_intensity_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_spending_intensity
    ADD CONSTRAINT kto_spending_intensity_pkey PRIMARY KEY (id);

--
-- Name: kto_stay_intensity kto_stay_intensity_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_stay_intensity
    ADD CONSTRAINT kto_stay_intensity_pkey PRIMARY KEY (id);

--
-- Name: kto_visitor_count kto_visitor_count_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_visitor_count
    ADD CONSTRAINT kto_visitor_count_pkey PRIMARY KEY (id);

--
-- Name: map_cell_district map_cell_district_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_district
    ADD CONSTRAINT map_cell_district_pkey PRIMARY KEY (cell_id, district_code);

--
-- Name: map_cell_image map_cell_image_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_image
    ADD CONSTRAINT map_cell_image_pkey PRIMARY KEY (id);

--
-- Name: map_cell map_cell_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell
    ADD CONSTRAINT map_cell_pkey PRIMARY KEY (id);

--
-- Name: map_relation map_relation_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_relation
    ADD CONSTRAINT map_relation_pkey PRIMARY KEY (id);

--
-- Name: map_share_link map_share_link_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_share_link
    ADD CONSTRAINT map_share_link_pkey PRIMARY KEY (id);

--
-- Name: member_district_visit member_district_visit_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.member_district_visit
    ADD CONSTRAINT member_district_visit_pkey PRIMARY KEY (id);

--
-- Name: district sigungu_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.district
    ADD CONSTRAINT sigungu_pkey PRIMARY KEY (code);

--
-- Name: spot_catalog_festival spot_catalog_festival_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog_festival
    ADD CONSTRAINT spot_catalog_festival_pkey PRIMARY KEY (catalog_id);

--
-- Name: spot_catalog spot_catalog_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog
    ADD CONSTRAINT spot_catalog_pkey PRIMARY KEY (id);

--
-- Name: spot_catalog_translation spot_catalog_translation_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog_translation
    ADD CONSTRAINT spot_catalog_translation_pkey PRIMARY KEY (id);

--
-- Name: spot_detail_image spot_detail_image_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_image
    ADD CONSTRAINT spot_detail_image_pkey PRIMARY KEY (id);

--
-- Name: spot_detail_info spot_detail_info_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_info
    ADD CONSTRAINT spot_detail_info_pkey PRIMARY KEY (id);

--
-- Name: spot_detail_intro spot_detail_intro_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_intro
    ADD CONSTRAINT spot_detail_intro_pkey PRIMARY KEY (id);

--
-- Name: spot_detail spot_detail_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail
    ADD CONSTRAINT spot_detail_pkey PRIMARY KEY (id);

--
-- Name: spot_festival spot_festival_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_festival
    ADD CONSTRAINT spot_festival_pkey PRIMARY KEY (id);

--
-- Name: spot spot_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot
    ADD CONSTRAINT spot_pkey PRIMARY KEY (id);

--
-- Name: spot_reaction spot_reaction_pkey; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_reaction
    ADD CONSTRAINT spot_reaction_pkey PRIMARY KEY (id);

--
-- Name: kto_concentration_rate uk_kto_concentration_rate_natural; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_concentration_rate
    ADD CONSTRAINT uk_kto_concentration_rate_natural UNIQUE (district_code, tourist_spot_name, target_date);

--
-- Name: kto_spending_intensity uk_kto_spending_intensity_natural; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_spending_intensity
    ADD CONSTRAINT uk_kto_spending_intensity_natural UNIQUE (district_code, base_month, indicator_cd);

--
-- Name: kto_stay_intensity uk_kto_stay_intensity_natural; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_stay_intensity
    ADD CONSTRAINT uk_kto_stay_intensity_natural UNIQUE (district_code, base_month, indicator_cd);

--
-- Name: kto_visitor_count uk_kto_visitor_count_natural; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.kto_visitor_count
    ADD CONSTRAINT uk_kto_visitor_count_natural UNIQUE (district_code, base_date, visitor_type);

--
-- Name: map_cell_image uq_map_cell_image_order; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_image
    ADD CONSTRAINT uq_map_cell_image_order UNIQUE (cell_id, display_order);

--
-- Name: map_relation uq_map_relation; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_relation
    ADD CONSTRAINT uq_map_relation UNIQUE (owner_id, counterpart_id);

--
-- Name: map_share_link uq_map_share_link_token; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_share_link
    ADD CONSTRAINT uq_map_share_link_token UNIQUE (token);

--
-- Name: member_district_visit uq_member_district_visit; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.member_district_visit
    ADD CONSTRAINT uq_member_district_visit UNIQUE (member_id, district_code);

--
-- Name: spot uq_spot; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot
    ADD CONSTRAINT uq_spot UNIQUE (content_id, language);

--
-- Name: spot_catalog_translation uq_spot_catalog_translation; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog_translation
    ADD CONSTRAINT uq_spot_catalog_translation UNIQUE (catalog_id, language);

--
-- Name: spot_detail uq_spot_detail; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail
    ADD CONSTRAINT uq_spot_detail UNIQUE (spot_id);

--
-- Name: spot_detail_image uq_spot_detail_image; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_image
    ADD CONSTRAINT uq_spot_detail_image UNIQUE (spot_id, display_order);

--
-- Name: spot_detail_info uq_spot_detail_info; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_info
    ADD CONSTRAINT uq_spot_detail_info UNIQUE (spot_id, category, display_order);

--
-- Name: spot_detail_intro uq_spot_detail_intro; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_intro
    ADD CONSTRAINT uq_spot_detail_intro UNIQUE (spot_id, category);

--
-- Name: spot_festival uq_spot_festival; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_festival
    ADD CONSTRAINT uq_spot_festival UNIQUE (spot_id);

--
-- Name: spot_reaction uq_spot_reaction; Type: CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_reaction
    ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id, language);

--
-- Name: idx_alarm_device_token_member; Type: INDEX; Schema: alarm; Owner: -
--

CREATE INDEX idx_alarm_device_token_member ON alarm.device_token USING btree (member_id);

--
-- Name: idx_alarm_notification_preference_member; Type: INDEX; Schema: alarm; Owner: -
--

CREATE INDEX idx_alarm_notification_preference_member ON alarm.notification_preference USING btree (member_id);

--
-- Name: idx_audit_member; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_audit_member ON auth.member_audit_log USING btree (member_id);

--
-- Name: idx_ev_member; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_ev_member ON auth.email_verification USING btree (member_id);

--
-- Name: idx_ev_member_purpose; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_ev_member_purpose ON auth.email_verification USING btree (member_id, purpose, created_at DESC);

--
-- Name: idx_member_role_role; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_member_role_role ON auth.member_role USING btree (role_id);

--
-- Name: idx_mta_member; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_mta_member ON auth.member_terms_agreement USING btree (member_id);

--
-- Name: idx_pr_member; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_pr_member ON auth.password_reset USING btree (member_id);

--
-- Name: idx_app_category_app_language; Type: INDEX; Schema: category; Owner: -
--

CREATE INDEX idx_app_category_app_language ON category.app_category USING btree (app_key, language, category_id);

--
-- Name: idx_category_external_mapping_lookup; Type: INDEX; Schema: category; Owner: -
--

CREATE INDEX idx_category_external_mapping_lookup ON category.category_external_mapping USING btree (provider, codeset, external_code);

--
-- Name: idx_community_block_blocked; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_block_blocked ON community.community_block USING btree (blocked_id, blocker_id);

--
-- Name: idx_community_block_blocker; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_block_blocker ON community.community_block USING btree (blocker_id, created_at DESC, id DESC);

--
-- Name: idx_community_comment_author; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_comment_author ON community.community_comment USING btree (author_id);

--
-- Name: idx_community_comment_parent; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_comment_parent ON community.community_comment USING btree (parent_comment_id, created_at, id) WHERE (parent_comment_id IS NOT NULL);

--
-- Name: idx_community_comment_post; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_comment_post ON community.community_comment USING btree (post_id, created_at DESC, id DESC);

--
-- Name: idx_community_conversation_high; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_conversation_high ON community.community_conversation USING btree (member_high, last_message_at DESC, id DESC);

--
-- Name: idx_community_conversation_low; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_conversation_low ON community.community_conversation USING btree (member_low, last_message_at DESC, id DESC);

--
-- Name: idx_community_message_conversation; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_conversation ON community.community_message USING btree (conversation_id, created_at DESC, id DESC);

--
-- Name: idx_community_message_created; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_created ON community.community_message USING btree (created_at);

--
-- Name: idx_community_message_quote_message; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_quote_message ON community.community_message_quote USING btree (message_id);

--
-- Name: idx_community_message_report_item_report; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_report_item_report ON community.community_message_report_item USING btree (report_id);

--
-- Name: idx_community_message_sender; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_sender ON community.community_message USING btree (sender_id);

--
-- Name: idx_community_message_unread; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_message_unread ON community.community_message USING btree (conversation_id, id, sender_id);

--
-- Name: idx_community_post_author; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_author ON community.community_post USING btree (author_id);

--
-- Name: idx_community_post_chip_category; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_chip_category ON community.community_post_chip USING btree (category, post_id);

--
-- Name: idx_community_post_feed; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_feed ON community.community_post USING btree (created_at DESC, id DESC);

--
-- Name: idx_community_post_hide_member; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_hide_member ON community.community_post_hide USING btree (member_id, created_at DESC, post_id DESC);

--
-- Name: idx_community_post_like_member; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_like_member ON community.community_post_like USING btree (member_id, post_id);

--
-- Name: idx_community_post_photo_post; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_photo_post ON community.community_post_photo USING btree (post_id, "position");

--
-- Name: idx_community_post_spot_catalog; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_spot_catalog ON community.community_post_spot USING btree (catalog_id, post_id);

--
-- Name: idx_community_post_spot_location; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_spot_location ON community.community_post_spot USING gist (location);

--
-- Name: idx_community_post_tag_normalized; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_tag_normalized ON community.community_post_tag USING btree (normalized, post_id);

--
-- Name: idx_community_post_tag_post; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_post_tag_post ON community.community_post_tag USING btree (post_id);

--
-- Name: idx_community_report_target; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_report_target ON community.community_report USING btree (target_type, target_id);

--
-- Name: idx_community_spot_answer_author; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_answer_author ON community.community_spot_answer USING btree (author_id);

--
-- Name: idx_community_spot_answer_question; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_answer_question ON community.community_spot_answer USING btree (question_id, created_at DESC, id DESC);

--
-- Name: idx_community_spot_answer_reply_answer; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_answer_reply_answer ON community.community_spot_answer_reply USING btree (answer_id, created_at, id);

--
-- Name: idx_community_spot_answer_reply_author; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_answer_reply_author ON community.community_spot_answer_reply USING btree (author_id);

--
-- Name: idx_community_spot_qa_photo_owner; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_qa_photo_owner ON community.community_spot_qa_photo USING btree (owner_type, owner_id, "position");

--
-- Name: idx_community_spot_question_author; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_question_author ON community.community_spot_question USING btree (author_id);

--
-- Name: idx_community_spot_question_catalog; Type: INDEX; Schema: community; Owner: -
--

CREATE INDEX idx_community_spot_question_catalog ON community.community_spot_question USING btree (catalog_id, resolved, created_at DESC, id DESC);

--
-- Name: idx_congestion_area_boundary; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_congestion_area_boundary ON tour.congestion_area USING gist (boundary);

--
-- Name: idx_district_area_cd; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_district_area_cd ON tour.district USING btree (area_cd);

--
-- Name: idx_district_boundary; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_district_boundary ON tour.district USING gist (boundary);

--
-- Name: idx_district_representative_location; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_district_representative_location ON tour.district USING gist (representative_location) WHERE (representative_location IS NOT NULL);

--
-- Name: idx_kto_concentration_rate_district_date; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_kto_concentration_rate_district_date ON tour.kto_concentration_rate USING btree (district_code, target_date DESC);

--
-- Name: idx_kto_spending_intensity_district_month; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_kto_spending_intensity_district_month ON tour.kto_spending_intensity USING btree (district_code, base_month DESC);

--
-- Name: idx_kto_stay_intensity_district_month; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_kto_stay_intensity_district_month ON tour.kto_stay_intensity USING btree (district_code, base_month DESC);

--
-- Name: idx_kto_visitor_count_district_date; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_kto_visitor_count_district_date ON tour.kto_visitor_count USING btree (district_code, base_date DESC);

--
-- Name: idx_map_cell_district_district; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_map_cell_district_district ON tour.map_cell_district USING btree (district_code);

--
-- Name: idx_map_relation_counterpart; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_map_relation_counterpart ON tour.map_relation USING btree (counterpart_id);

--
-- Name: idx_map_relation_owner_created; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_map_relation_owner_created ON tour.map_relation USING btree (owner_id, created_at DESC, counterpart_id DESC);

--
-- Name: idx_spot_catalog_category; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_category ON tour.spot_catalog USING btree (category);

--
-- Name: idx_spot_catalog_district; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_district ON tour.spot_catalog USING btree (district_code) WHERE (district_code IS NOT NULL);

--
-- Name: idx_spot_catalog_festival_end_date; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_festival_end_date ON tour.spot_catalog_festival USING btree (event_end_date);

--
-- Name: idx_spot_catalog_id; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_id ON tour.spot USING btree (catalog_id) WHERE (catalog_id IS NOT NULL);

--
-- Name: idx_spot_catalog_language; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_language ON tour.spot USING btree (catalog_id, language);

--
-- Name: idx_spot_catalog_location; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_location ON tour.spot_catalog USING gist (location);

--
-- Name: idx_spot_catalog_translation_catalog; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_translation_catalog ON tour.spot_catalog_translation USING btree (catalog_id);

--
-- Name: idx_spot_catalog_translation_language; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_translation_language ON tour.spot_catalog_translation USING btree (language);

--
-- Name: idx_spot_catalog_translation_language_category; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_translation_language_category ON tour.spot_catalog_translation USING btree (language, category);

--
-- Name: idx_spot_catalog_translation_visible; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_catalog_translation_visible ON tour.spot_catalog_translation USING btree (visible, language);

--
-- Name: idx_spot_festival_end_date; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_festival_end_date ON tour.spot_festival USING btree (event_end_date);

--
-- Name: idx_spot_language_category; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_language_category ON tour.spot USING btree (language, category);

--
-- Name: idx_spot_location; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_location ON tour.spot USING gist (location);

--
-- Name: idx_spot_reaction_catalog_id; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_reaction_catalog_id ON tour.spot_reaction USING btree (catalog_id);

--
-- Name: idx_spot_reaction_catalog_reaction; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_reaction_catalog_reaction ON tour.spot_reaction USING btree (catalog_id, language, reaction);

--
-- Name: idx_spot_reaction_liked_list; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_reaction_liked_list ON tour.spot_reaction USING btree (member_id, language, reaction, updated_at DESC, catalog_id DESC);

--
-- Name: idx_spot_reaction_member; Type: INDEX; Schema: tour; Owner: -
--

CREATE INDEX idx_spot_reaction_member ON tour.spot_reaction USING btree (member_id);

--
-- Name: uq_map_share_link_active_member; Type: INDEX; Schema: tour; Owner: -
--

CREATE UNIQUE INDEX uq_map_share_link_active_member ON tour.map_share_link USING btree (member_id) WHERE (revoked_at IS NULL);

--
-- Name: community_post_spot trg_community_post_spot_sync_location; Type: TRIGGER; Schema: community; Owner: -
--

CREATE TRIGGER trg_community_post_spot_sync_location BEFORE INSERT OR UPDATE OF map_x, map_y ON community.community_post_spot FOR EACH ROW EXECUTE FUNCTION community.community_post_spot_sync_location();

--
-- Name: spot_catalog trg_spot_catalog_sync_location; Type: TRIGGER; Schema: tour; Owner: -
--

CREATE TRIGGER trg_spot_catalog_sync_location BEFORE INSERT OR UPDATE OF map_x, map_y ON tour.spot_catalog FOR EACH ROW EXECUTE FUNCTION tour.spot_catalog_sync_location();

--
-- Name: spot_catalog_translation trg_spot_catalog_translation_sync_location; Type: TRIGGER; Schema: tour; Owner: -
--

CREATE TRIGGER trg_spot_catalog_translation_sync_location BEFORE INSERT OR UPDATE OF map_x, map_y ON tour.spot_catalog_translation FOR EACH ROW EXECUTE FUNCTION tour.spot_catalog_sync_location();

--
-- Name: spot trg_spot_sync_location; Type: TRIGGER; Schema: tour; Owner: -
--

CREATE TRIGGER trg_spot_sync_location BEFORE INSERT OR UPDATE OF map_x, map_y ON tour.spot FOR EACH ROW EXECUTE FUNCTION tour.spot_sync_location();

--
-- Name: email_verification fk_ev_member; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.email_verification
    ADD CONSTRAINT fk_ev_member FOREIGN KEY (member_id) REFERENCES auth.member(id);

--
-- Name: member_profile fk_member_profile_member; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_profile
    ADD CONSTRAINT fk_member_profile_member FOREIGN KEY (member_id) REFERENCES auth.member(id);

--
-- Name: member_role fk_member_role_member; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_role
    ADD CONSTRAINT fk_member_role_member FOREIGN KEY (member_id) REFERENCES auth.member(id) ON DELETE CASCADE;

--
-- Name: member_role fk_member_role_role; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_role
    ADD CONSTRAINT fk_member_role_role FOREIGN KEY (role_id) REFERENCES auth.role(id) ON DELETE CASCADE;

--
-- Name: member_terms_agreement fk_mta_member; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_terms_agreement
    ADD CONSTRAINT fk_mta_member FOREIGN KEY (member_id) REFERENCES auth.member(id);

--
-- Name: member_terms_agreement fk_mta_terms; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.member_terms_agreement
    ADD CONSTRAINT fk_mta_terms FOREIGN KEY (terms_id) REFERENCES auth.terms(id);

--
-- Name: password_reset fk_pr_member; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.password_reset
    ADD CONSTRAINT fk_pr_member FOREIGN KEY (member_id) REFERENCES auth.member(id);

--
-- Name: role_permission fk_role_permission_permission; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.role_permission
    ADD CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES auth.permission(id) ON DELETE CASCADE;

--
-- Name: role_permission fk_role_permission_role; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.role_permission
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES auth.role(id) ON DELETE CASCADE;

--
-- Name: batch_job_execution_context job_exec_ctx_fk; Type: FK CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_execution_context
    ADD CONSTRAINT job_exec_ctx_fk FOREIGN KEY (job_execution_id) REFERENCES batch.batch_job_execution(job_execution_id);

--
-- Name: batch_job_execution_params job_exec_params_fk; Type: FK CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_execution_params
    ADD CONSTRAINT job_exec_params_fk FOREIGN KEY (job_execution_id) REFERENCES batch.batch_job_execution(job_execution_id);

--
-- Name: batch_step_execution job_exec_step_fk; Type: FK CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_step_execution
    ADD CONSTRAINT job_exec_step_fk FOREIGN KEY (job_execution_id) REFERENCES batch.batch_job_execution(job_execution_id);

--
-- Name: batch_job_execution job_inst_exec_fk; Type: FK CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_job_execution
    ADD CONSTRAINT job_inst_exec_fk FOREIGN KEY (job_instance_id) REFERENCES batch.batch_job_instance(job_instance_id);

--
-- Name: batch_step_execution_context step_exec_ctx_fk; Type: FK CONSTRAINT; Schema: batch; Owner: -
--

ALTER TABLE ONLY batch.batch_step_execution_context
    ADD CONSTRAINT step_exec_ctx_fk FOREIGN KEY (step_execution_id) REFERENCES batch.batch_step_execution(step_execution_id);

--
-- Name: app_category fk_app_category_category; Type: FK CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.app_category
    ADD CONSTRAINT fk_app_category_category FOREIGN KEY (category_id) REFERENCES category.category(id) ON DELETE CASCADE;

--
-- Name: category_external_mapping fk_category_external_mapping_category; Type: FK CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_external_mapping
    ADD CONSTRAINT fk_category_external_mapping_category FOREIGN KEY (category_id) REFERENCES category.category(id) ON DELETE CASCADE;

--
-- Name: category_translation fk_category_translation_category; Type: FK CONSTRAINT; Schema: category; Owner: -
--

ALTER TABLE ONLY category.category_translation
    ADD CONSTRAINT fk_category_translation_category FOREIGN KEY (category_id) REFERENCES category.category(id) ON DELETE CASCADE;

--
-- Name: community_comment fk_community_comment_parent; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_comment
    ADD CONSTRAINT fk_community_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES community.community_comment(id) ON DELETE CASCADE;

--
-- Name: community_comment fk_community_comment_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_comment
    ADD CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_message fk_community_message_conversation; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message
    ADD CONSTRAINT fk_community_message_conversation FOREIGN KEY (conversation_id) REFERENCES community.community_conversation(id) ON DELETE CASCADE;

--
-- Name: community_message_quote fk_community_message_quote_message; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_quote
    ADD CONSTRAINT fk_community_message_quote_message FOREIGN KEY (message_id) REFERENCES community.community_message(id) ON DELETE CASCADE;

--
-- Name: community_message_report fk_community_message_report_conversation; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report
    ADD CONSTRAINT fk_community_message_report_conversation FOREIGN KEY (conversation_id) REFERENCES community.community_conversation(id) ON DELETE CASCADE;

--
-- Name: community_message_report_item fk_community_message_report_item_message; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report_item
    ADD CONSTRAINT fk_community_message_report_item_message FOREIGN KEY (message_id) REFERENCES community.community_message(id) ON DELETE CASCADE;

--
-- Name: community_message_report_item fk_community_message_report_item_report; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_message_report_item
    ADD CONSTRAINT fk_community_message_report_item_report FOREIGN KEY (report_id) REFERENCES community.community_message_report(id) ON DELETE CASCADE;

--
-- Name: community_post_chip fk_community_post_chip_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_chip
    ADD CONSTRAINT fk_community_post_chip_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_post_hide fk_community_post_hide_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_hide
    ADD CONSTRAINT fk_community_post_hide_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_post_like fk_community_post_like_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_like
    ADD CONSTRAINT fk_community_post_like_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_post_photo fk_community_post_photo_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_photo
    ADD CONSTRAINT fk_community_post_photo_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_post_spot fk_community_post_spot_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_spot
    ADD CONSTRAINT fk_community_post_spot_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_post_tag fk_community_post_tag_post; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_post_tag
    ADD CONSTRAINT fk_community_post_tag_post FOREIGN KEY (post_id) REFERENCES community.community_post(id) ON DELETE CASCADE;

--
-- Name: community_spot_answer fk_community_spot_answer_question; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_answer
    ADD CONSTRAINT fk_community_spot_answer_question FOREIGN KEY (question_id) REFERENCES community.community_spot_question(id) ON DELETE CASCADE;

--
-- Name: community_spot_answer_reply fk_community_spot_answer_reply_answer; Type: FK CONSTRAINT; Schema: community; Owner: -
--

ALTER TABLE ONLY community.community_spot_answer_reply
    ADD CONSTRAINT fk_community_spot_answer_reply_answer FOREIGN KEY (answer_id) REFERENCES community.community_spot_answer(id) ON DELETE CASCADE;

--
-- Name: district fk_district_area; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.district
    ADD CONSTRAINT fk_district_area FOREIGN KEY (area_cd) REFERENCES tour.area(area_cd);

--
-- Name: map_cell_district fk_map_cell_district_cell; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_district
    ADD CONSTRAINT fk_map_cell_district_cell FOREIGN KEY (cell_id) REFERENCES tour.map_cell(id) ON DELETE CASCADE;

--
-- Name: map_cell_district fk_map_cell_district_district; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_district
    ADD CONSTRAINT fk_map_cell_district_district FOREIGN KEY (district_code) REFERENCES tour.district(code) ON DELETE CASCADE;

--
-- Name: map_cell_image fk_map_cell_image_cell; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.map_cell_image
    ADD CONSTRAINT fk_map_cell_image_cell FOREIGN KEY (cell_id) REFERENCES tour.map_cell(id) ON DELETE CASCADE;

--
-- Name: member_district_visit fk_member_district_visit_district; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.member_district_visit
    ADD CONSTRAINT fk_member_district_visit_district FOREIGN KEY (district_code) REFERENCES tour.district(code) ON DELETE CASCADE;

--
-- Name: spot fk_spot_catalog; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot
    ADD CONSTRAINT fk_spot_catalog FOREIGN KEY (catalog_id) REFERENCES tour.spot_catalog(id) ON DELETE SET NULL;

--
-- Name: spot_catalog_festival fk_spot_catalog_festival_catalog; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog_festival
    ADD CONSTRAINT fk_spot_catalog_festival_catalog FOREIGN KEY (catalog_id) REFERENCES tour.spot_catalog(id) ON DELETE CASCADE;

--
-- Name: spot_catalog_translation fk_spot_catalog_translation_catalog; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_catalog_translation
    ADD CONSTRAINT fk_spot_catalog_translation_catalog FOREIGN KEY (catalog_id) REFERENCES tour.spot_catalog(id) ON DELETE CASCADE;

--
-- Name: spot_detail_image fk_spot_detail_image_spot; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_image
    ADD CONSTRAINT fk_spot_detail_image_spot FOREIGN KEY (spot_id) REFERENCES tour.spot(id) ON DELETE CASCADE;

--
-- Name: spot_detail_info fk_spot_detail_info_spot; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_info
    ADD CONSTRAINT fk_spot_detail_info_spot FOREIGN KEY (spot_id) REFERENCES tour.spot(id) ON DELETE CASCADE;

--
-- Name: spot_detail_intro fk_spot_detail_intro_spot; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail_intro
    ADD CONSTRAINT fk_spot_detail_intro_spot FOREIGN KEY (spot_id) REFERENCES tour.spot(id) ON DELETE CASCADE;

--
-- Name: spot_detail fk_spot_detail_spot; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_detail
    ADD CONSTRAINT fk_spot_detail_spot FOREIGN KEY (spot_id) REFERENCES tour.spot(id) ON DELETE CASCADE;

--
-- Name: spot_festival fk_spot_festival_spot; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_festival
    ADD CONSTRAINT fk_spot_festival_spot FOREIGN KEY (spot_id) REFERENCES tour.spot(id) ON DELETE CASCADE;

--
-- Name: spot_reaction fk_spot_reaction_catalog; Type: FK CONSTRAINT; Schema: tour; Owner: -
--

ALTER TABLE ONLY tour.spot_reaction
    ADD CONSTRAINT fk_spot_reaction_catalog FOREIGN KEY (catalog_id) REFERENCES tour.spot_catalog(id) ON DELETE CASCADE;


-- ─────────────────────────────────────────────────────────────────────────────
-- 기준 데이터
--
-- 이전에는 R__seed_rbac·R__seed_terms가 들고 있었다. R__를 걷어내면서 여기로 옮겼다 —
-- 스키마 없이는 서비스가 서지 않는 두 가지라 스키마와 함께 와야 한다: 역할·권한이 없으면
-- 인가가 서지 않고, 약관이 없으면 가입이 막힌다(필수 약관 미동의 시 가입 거부).
--
-- **ON CONFLICT를 남겨 둔다.** V1은 빈 DB에 한 번 도는 것이 원칙이지만, 사람이 손으로
-- 넣은 뒤 적용하는 경우에 충돌로 기동이 깨지는 것보다 넘어가는 편이 낫다.
--
-- 여기에 커뮤니티 더미 시드(R__seed_community)는 오지 않는다 — 개발 화면 확인용이며
-- 그 파일 자신이 운영 배포 전 제거를 명시했다. 앞으로 필요하면 개발 DB에 직접 넣는다.
--
-- **R__seed_rbac의 `member_role` 백필도 일부러 뺐다.** 그것은 "이 스텝 이전에 가입한
-- 회원에게도 USER를 보장한다"는 소급 조치인데, V1이 도는 시점의 DB는 비어 있어 백필할
-- 회원이 없다. 신규 가입은 가입 핸들러가 USER를 부여한다.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO auth.role (code, name, description) VALUES
    ('USER',  '일반 사용자', '기본 역할. 관리 권한 없음.'),
    ('ADMIN', '관리자',      '회원 관리 및 역할 부여/회수 권한 보유.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth.permission (code, description) VALUES
    ('member:suspend',    '회원 정지'),
    ('member:reactivate', '회원 정지 해제'),
    ('role:assign',       '역할 부여'),
    ('role:revoke',       '역할 회수'),
    ('member:read',       '관리자 회원 조회')
ON CONFLICT (code) DO NOTHING;

INSERT INTO auth.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.role r
CROSS JOIN auth.permission p
WHERE r.code = 'ADMIN'
  AND p.code IN ('member:suspend', 'member:reactivate', 'role:assign', 'role:revoke', 'member:read')
ON CONFLICT DO NOTHING;

INSERT INTO auth.terms (code, title, body, version, required, effective_at)
VALUES
    ('SERVICE', '이으리 서비스 이용약관',
     '제1조(목적) 본 약관은 이으리(k-trip-spot) 관광 정보 및 커뮤니티 서비스의 이용 조건과 절차, 회원과 회사의 권리·의무를 규정합니다. 제2조(회원가입) 회원은 SNS 인증(Apple/Google/Kakao)을 통해 가입하며, 만 14세 이상만 가입할 수 있습니다. 제3조(서비스) 회사는 관광지 정보, 주변 명소 추천, 커뮤니티, 커머스 기능을 제공합니다.',
     '1.0', TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+09'),

    ('PRIVACY', '개인정보 수집 및 이용 동의',
     '수집 항목: 이메일(암호화 저장), SNS 식별자, 닉네임, 프로필 이미지. 이용 목적: 회원 식별, 서비스 제공, 이메일 검증. 보유 기간: 회원 탈퇴 시까지. 회원은 동의를 거부할 권리가 있으나 거부 시 가입이 제한됩니다.',
     '1.0', TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+09'),

    ('LOCATION', '위치기반 서비스 이용약관',
     '이으리는 관광 특성상 주변 관광지·명소 추천 및 지도 기능 제공을 위해 단말의 위치정보를 이용할 수 있습니다. 위치정보는 실시간 서비스 제공 목적에만 사용되며 별도 저장하지 않습니다. 관광 편의를 위한 필수 약관입니다.',
     '1.0', TRUE, TIMESTAMPTZ '2026-01-01 00:00:00+09'),

    ('MARKETING', '마케팅 정보 수신 동의(선택)',
     '관광지 프로모션, 여행 큐레이션, 이벤트 및 혜택 정보를 이메일/푸시로 수신하는 데 동의합니다. 선택 항목이며 동의하지 않아도 서비스 이용에 제한이 없습니다. 수신 동의는 언제든 철회할 수 있습니다.',
     '1.0', FALSE, TIMESTAMPTZ '2026-01-01 00:00:00+09')
ON CONFLICT (code, version) DO NOTHING;
