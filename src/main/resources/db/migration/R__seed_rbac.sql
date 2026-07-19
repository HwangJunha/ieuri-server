-- RBAC 베이스라인 시드(반복 실행 안전 / idempotent). role/permission/매핑은 데이터로 관리한다.
-- 새 역할·권한·매핑은 이 파일을 수정(체크섬 변경 → 재적용)하거나 관리 기능으로 추가한다. 코드 변경 불필요.

-- 역할: USER(기본, 관리 권한 없음), ADMIN(관리자).
INSERT INTO role (code, name, description) VALUES
    ('USER',  '일반 사용자', '기본 역할. 관리 권한 없음.'),
    ('ADMIN', '관리자',      '회원 관리 및 역할 부여/회수 권한 보유.')
ON CONFLICT (code) DO NOTHING;

-- 권한(초기 5종): 인가의 최소 단위.
INSERT INTO permission (code, description) VALUES
    ('member:suspend',    '회원 정지'),
    ('member:reactivate', '회원 정지 해제'),
    ('role:assign',       '역할 부여'),
    ('role:revoke',       '역할 회수'),
    ('member:read',       '관리자 회원 조회')
ON CONFLICT (code) DO NOTHING;

-- ADMIN → 위 권한 전부. USER → 관리 권한 없음(매핑 없음).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'ADMIN'
  AND p.code IN ('member:suspend', 'member:reactivate', 'role:assign', 'role:revoke', 'member:read')
ON CONFLICT DO NOTHING;

-- 기존 회원 USER 역할 백필: 신규 회원은 가입 핸들러가 USER를 부여하지만,
-- 이 스텝 이전에 가입한 회원에게도 기본 역할을 보장한다(멱등).
INSERT INTO member_role (member_id, role_id)
SELECT m.id, r.id
FROM member m
CROSS JOIN role r
WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;
