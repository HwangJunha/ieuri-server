-- auth 도메인: RBAC(역할 기반 접근제어).
-- 인가의 최소 단위는 permission(권한). role(역할)은 권한의 묶음이며 회원에게 부여된다.
-- Member ↔ Role, Role ↔ Permission 은 각각 다대다(조인 테이블). Member 애그리거트는 키우지 않는다.
-- 도메인 내부(auth) FK만 사용한다(role/permission/member 모두 auth 소유) — 크로스도메인 FK 없음.

CREATE TABLE role (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE permission (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_permission_code UNIQUE (code)
);

CREATE TABLE role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
);

CREATE TABLE member_role (
    member_id   BIGINT      NOT NULL,
    role_id     BIGINT      NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_member_role PRIMARY KEY (member_id, role_id),
    -- member 도 auth 도메인 소유이므로 도메인 내부 FK 허용. 탈퇴(회원 삭제) 시 매핑도 함께 정리된다.
    CONSTRAINT fk_member_role_member
        FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_role_role
        FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
);

-- 역할별 회원 조회(members(roleCode=...) 필터·권한 전파)를 위한 보조 인덱스.
CREATE INDEX idx_member_role_role ON member_role (role_id);
