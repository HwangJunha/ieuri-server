# 이으리(ieuri) 권한 및 조회 명세 — Step 4: RBAC / 회원 조회(서버)

> 기능을 쪼개서 단계적으로 진행합니다. 이 문서는 **역할 기반 접근제어(RBAC)** 와 **회원 조회(읽기/CQRS)** 서버 로직을 다룹니다.
> 회원가입(Step 1)·로그인(Step 2)·탈퇴/정지(Step 3)는 별도 명세에서 완료되었습니다.

> **스코프 변경 메모**: 원래 이 스텝은 "RBAC + accessToken 즉시 무효화"였으나, 논의 결과 **즉시 무효화는 Valkey 저장소 스텝으로 이월**(In-memory 구현은 멀티앱에서 무의미하므로 Valkey와 함께 만드는 게 합리적, access 5분 TTL이 그동안 완충)하고, 이 스텝에는 **회원 조회(읽기)** 를 넣는다. 즉시 무효화를 이번에 포함하려면 알려주면 복원한다.

---

## 범위 (Scope)

- **이번 스텝**:
  1. **RBAC(제대로)** — 역할(Role)과 권한(Permission)을 **별도 테이블/애그리거트**로 분리하고, 회원↔역할·역할↔권한을 각각 **다대다**로 매핑. 보호된 작업은 **권한(permission) 단위로 인가**. Step 3의 임시 admin 가드(`X-Admin-Api-Key`)를 **권한 기반 인가로 대체·삭제**.
  2. **회원 조회(읽기/CQRS)** — 이 프로젝트의 **첫 읽기(read) 슬라이스**. `me`(본인 정보), 관리자 회원 조회(`member`/`members`, 권한 게이트). CQRS 규약대로 QueryDSL 프로젝션 → read DTO → GraphQL 응답(애그리거트 우회).

- **설계 원칙**: `Member` 애그리거트를 키우지 않는다. 역할/권한은 **독립 애그리거트**, 회원-역할 매핑은 **조인 테이블**(`member_role`). 데이터 주도(role/permission은 시드/관리로 추가, 코드 변경 없이 확장). 리소스 스코프·ABAC·역할 계층 같은 과도한 복잡성은 배제하되 **확장 여지**는 남긴다.

- **명시적 제외(사용자 지시/논의)**: **accessToken 즉시 무효화(→ Valkey 스텝)**, refresh/access 저장소 Redis/Valkey 실제 세팅, 크로스도메인 데이터 삭제, 실제 Apple 자격증명 revoke·SNS 실검증.

---

## 전제 (기존 스텝에서 확보된 것)

- accessToken은 stateless JWT(HS256, 5분), `sub`(=member_id)·`jti`·`iat`·`exp`·`typ`·`iss`. `TokenProviderPort` 발급/검증, `JwtAuthenticationFilter`가 인증 주체 세팅.
- Step 3의 정지/복구는 임시 `AdminGuard`(`X-Admin-Api-Key`)로 보호 중 → **이번에 권한 기반으로 교체·삭제**.
- `member`/소유 테이블(`member_profile`, `member_terms_agreement`, `email_verification`)·`Member` 애그리거트 존재. **이번 스텝에서 `member`에는 컬럼을 추가하지 않는다.**
- 이메일은 `email_encrypted`(AES-GCM)로 저장됨 → 조회 시 복호화 필요(아래 조회 설계 참고).
- 지금까지는 전부 command(쓰기)만 구현됨 → **읽기 경로(QueryDSL)는 이번에 처음 도입**.

---

## RBAC 모델 (핵심)

**용어**
- **Permission(권한)**: 인가의 최소 단위 = "무엇을 할 수 있는가". 코드로 식별(예: `member:suspend`). **인가는 역할이 아니라 권한으로 판정**(엔터프라이즈 관례: 역할명 하드코딩 대신 능력 검사).
- **Role(역할)**: 권한의 묶음. 회원에게 부여되는 단위(예: `ADMIN`, `SUPPORT`).
- **Member ↔ Role**: 다대다(`member_role`). 한 회원이 여러 역할 가능(NIST Core RBAC 표준 — 유효 권한은 역할들의 합집합).
- **Role ↔ Permission**: 다대다(`role_permission`).
- **Effective permissions(유효 권한)**: 회원의 모든 역할이 가진 권한의 합집합. 인가는 이 집합으로 판정.

**베이스라인 시드(데이터, 코드 아님)**
- 역할: `USER`(기본), `ADMIN`.
- 권한(초기): `member:suspend`, `member:reactivate`, `role:assign`, `role:revoke`, **`member:read`**(관리자 회원 조회).
- 매핑: `ADMIN` → 위 권한 전부. `USER` → 관리 권한 없음(일반 이용 baseline).
- 신규/기존 회원은 **기본 `USER` 역할**(가입 시 USER 부여).

**확장 방식(코드 변경 없이)**: 새 권한/역할/매핑은 마이그레이션 시드 또는 관리 기능으로 추가. 리졸버 인가는 권한 코드 문자열로 선언.

---

## 회원 조회 (Read / CQRS) — 이번 신규

> CLAUDE.md CQRS 규약: GraphQL args → query 핸들러 → `port/out/query` → **QueryDSL이 read DTO로 직접 프로젝션(애그리거트 우회)** → 리졸버가 read DTO를 GraphQL 응답으로 매핑. `application`은 DGS 타입을 모른다. 우선 per-query 프로젝션으로 시작, N+1 실측 시에만 DataLoader.

**조회 기능**
1. **`me`(본인 정보)** — accessToken 인증 필요(권한 불요, 로그인만). 반환: 닉네임·프로필이미지·상태·emailVerified·**역할 목록**·**유효 권한 목록**·가입일(+본인 이메일). → 클라이언트가 권한으로 **메뉴 렌더**(권한 코드 UX)에 사용.
2. **관리자 회원 조회** — 권한 `member:read` 필요:
   - `member(id: ID!)` — 단건 상세(프로필·상태·정지정보·역할).
   - `members(status, roleCode, page, size)` — 페이지네이션 목록.
3. **(선택) `activeTerms`** — 공개 조회(가입 화면용 활성 약관 목록). 유용하지만 우선순위 낮음 — 포함 여부는 구현 시 판단.

**이메일 복호화 처리**: read DTO는 애그리거트를 우회하므로 QueryDSL은 암호문만 뽑는다. 평문 이메일이 필요한 조회(`me`, 관리자 단건)는 **query 어댑터가 `EmailEncryptorPort`로 복호화**해 read DTO를 완성한다(리졸버/도메인 아님). 목록(`members`)은 **이메일 마스킹 또는 미포함**(대량 복호화 회피 + 최소수집 원칙).

**권한과 조회의 결합**: `member:read` 권한이 관리자 회원 조회 접근을 통제 → RBAC와 조회가 한 슬라이스에서 맞물린다.

---

## 인증/인가 (토큰·필터)

- **accessToken 클레임에 `perms` 추가**: 발급 시점의 유효 권한 코드 목록(역할 flatten). 인가는 토큰 `perms`로 판정(요청당 DB 조회 없음). (선택) `roles`도 표시/감사용 포함.
- `AccessTokenClaims`에 `memberId` + **`permissions: Set<String>`** 추가.
- refresh 재발급 시 **현재 유효 권한을 재조회**해 새 accessToken에 반영(역할 변경 전파).
- `JwtAuthenticationFilter`: 검증 후 `SecurityContext`에 memberId + authorities(perms) 설정.
- `AuthorizationService.require("member:suspend")`(권한 검사) — `AdminGuard` 대체. `me`는 인증만, 관리자 조회/정지/복구/역할변경은 권한 필요.
- **임시 admin 가드 제거**: `AdminGuard`/`AdminProperties`/`ieuri.admin.*` 삭제.

> 트레이드오프: 권한을 토큰에 flatten하므로 role↔permission 매핑 변경은 최대 access TTL(5분) 후 반영. 역할 부여/회수의 즉시 반영(=즉시 무효화)은 **Valkey 스텝으로 이월**(그동안 5분 지연 허용).

---

## 역할 부여/회수 흐름

1. `assignRole(memberId, roleCode)` / `revokeRole(memberId, roleCode)` — 각각 `role:assign`/`role:revoke` 권한 필요.
2. `member_role` 매핑 갱신 + 감사(`MEMBER_ROLE_ASSIGNED`/`_REVOKED`).
3. **자기 강등 방지**: `revokeRole`로 본인의 마지막 관리 역할(관리 권한을 주는 역할) 회수는 거부.
4. (이월) 대상 회원 accessToken 즉시 무효화 → Valkey 스텝에서 연결. 이번엔 5분 후 자연 반영.

---

## 필수 규칙 (Invariants)

1. 인가는 **권한(permission)** 으로 판정. 역할명 하드코딩 금지.
2. 회원 유효 권한 = 부여된 모든 역할의 권한 합집합.
3. 보호된 작업(정지/복구/역할부여·회수/관리자 조회)은 해당 **권한을 가진 accessToken** 없이는 거부(403 상당).
4. **자기 강등 방지**(위 참조).
5. `member`에는 role/permission을 저장하지 않는다(조인 테이블 분리 — 애그리거트 비대화 방지).
6. 읽기는 **애그리거트를 거치지 않는다**(QueryDSL → read DTO 직접 프로젝션). 쓰기 포트로 읽지 않는다(CQRS 유지).
7. 조회 응답의 개인정보는 최소수집: 목록은 이메일 마스킹/미포함, 단건·본인만 복호화 이메일 노출.

---

## 스키마 변경 (Flyway)

`V8__create_rbac.sql` — RBAC 테이블(도메인 내 FK 허용, snake_case):
- `role(id PK, code UNIQUE, name, description, created_at)`
- `permission(id PK, code UNIQUE, description, created_at)`
- `role_permission(role_id→role, permission_id→permission, PK(role_id, permission_id))`
- `member_role(member_id→member, role_id→role, assigned_at, PK(member_id, role_id))`

`R__seed_rbac.sql`(반복 실행 안전) — 베이스라인 역할/권한/매핑 시드(USER/ADMIN, 권한 5종, ADMIN↔권한).

> `member` 무변경. 탈퇴(회원 삭제) 시 `member_role`도 함께 삭제(자식 삭제 순서에 포함). 조회는 스키마 변경 없음(기존 테이블 프로젝션).

---

## 헥사고날 배치

- **domain**: `Permission`(vo, code), `Role`(애그리거트: code + permissions). 회원-역할 매핑은 관계이므로 `Member`에 넣지 않고 포트로 다룸.
- **application**:
  - 쓰기: `AssignRoleUseCase`/`RevokeRoleUseCase` + 핸들러. 포트 `RoleRepositoryPort`, 유효권한 조회.
  - **읽기(신규)**: `port/in/query`(`GetMeQueryUseCase`, `GetMemberQueryUseCase`, `ListMembersQueryUseCase`), `query/`(read DTO + 핸들러), `port/out/query`(read DTO 반환). 토큰 발급 로직에 권한 반영.
- **infrastructure**:
  - RBAC JPA 엔티티/리포지토리/어댑터, `AuthorizationService`(AdminGuard 대체), 역할 부트스트랩 컴포넌트, JWT 발급/검증에 perms, DGS 리졸버(`assignRole`/`revokeRole`), 정지/복구 리졸버 가드 교체.
  - **읽기(신규)**: `adapter/out/query`(QueryDSL 프로젝션 → read DTO, 이메일 복호화), `adapter/in/graphql`(query 리졸버 `me`/`member`/`members`, codegen 응답 타입). **QueryDSL 의존성 추가 필요**(현재 미포함): `querydsl-jpa(jakarta)` + kapt Q클래스 생성.
- 의존성 방향(infrastructure → application → domain)은 ArchUnit으로 계속 강제. (읽기 경로도 동일 규칙 — `port/out/query`는 read DTO 반환 허용, 도메인 포트 대칭 강요 안 함.)

---

## 설정 (Config) — 예시

```yaml
ieuri:
  rbac:
    # 최초 ADMIN 부트스트랩. 부팅 시 이 회원들에게 ADMIN 역할 부여(idempotent). 운영은 환경변수 주입.
    bootstrap-admin-member-ids: ${IEURI_BOOTSTRAP_ADMIN_IDS:}
  # (제거) admin.api-key — 권한 기반 인가로 대체
```

---

## 저장소 & 멀티앱 (참고 — 이월 배경)

- accessToken은 **저장 안 함(stateless)**. refresh만 저장(회전·재사용감지·폐기).
- 즉시 무효화는 회원별 "무효화 컷오프 마커" 저장소가 필요(토큰 저장 아님). In-memory는 멀티앱에서 인스턴스 공유 불가 → **RefreshTokenStore + (미래)AccessTokenRevocationStore 둘 다 Valkey로** 갈 때 함께 만든다. 그래서 이번 스텝에서 제외.

---

## 결정 완료

**확정됨**:
- **제대로 된 RBAC**: role/permission/role_permission/member_role 분리, 다대다. `Member` 안 키움. 다중 역할 허용(표준). **권한 단위 인가**(역할명 하드코딩 금지). 토큰에 perms flatten(클라 메뉴 렌더 겸용).
- **자기 강등 거부**. 임시 admin 가드 제거·권한 대체. 최초 ADMIN 설정 부트스트랩.
- **회원 조회(읽기) 첫 도입**: `me`(본인)·`member`/`members`(관리자, `member:read`). CQRS QueryDSL 프로젝션(애그리거트 우회), 이메일은 query 어댑터가 복호화(목록은 마스킹/미포함).
- **accessToken 즉시 무효화는 Valkey 스텝으로 이월**(access 5분 TTL이 완충). refresh/access 저장소 Valkey 세팅도 별도.
- **DB**: V8 RBAC 4테이블 + `R__seed_rbac` 시드. `member` 무변경.

**남은 판단 없음.** 이 스텝 명세 확정. (즉시 무효화를 이번에 포함하려면 알려주면 복원)
