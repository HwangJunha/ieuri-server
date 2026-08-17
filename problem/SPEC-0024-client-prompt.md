# SPEC-0024 서버 변경 — 클라이언트 대응 요청

서버 PR: ieuri-server#89 (`feature/SPEC-0024-email-verification-policy`) · 스펙: SPEC-0024 · 이슈: k-trip-spot#56

이메일 인증 정책을 개편했습니다. 한 줄로 요약하면 **"SNS는 인증을 면제하고, 인증을 마치기 전에는 아무도 앱에 들어오지 못한다"** 입니다.

운영 중인 앱이 없어 하위 호환 없이 정리했습니다. **BREAKING 3건**이 있고, 그중 하나는 로그인 실패 처리 로직을 반드시 다시 써야 합니다.

---

## 1. 미인증 회원도 로그인에 성공합니다 (BREAKING)

지금까지 `loginEmail`은 미인증 회원에게 `userErrors`에 `EMAIL_NOT_VERIFIED`를 담아 **실패**했습니다. 이제 **성공하고 토큰을 발급합니다.**

```
# 전 — 로그인 실패를 "인증하러 가라"는 신호로 씀
loginEmail → success: false, userErrors: [{ code: "EMAIL_NOT_VERIFIED" }]

# 후 — 로그인은 성공. payload에 그 코드가 다시는 오지 않음
loginEmail → success: true, accessToken/refreshToken 발급
```

**그 코드를 읽던 분기는 이제 절대 실행되지 않습니다.** 지우고, 대신 로그인 성공 후 `me.emailVerified`로 진입 경로를 결정하세요.

```graphql
query { me { emailVerified canChangeEmail } }
```

- `emailVerified: false` → 이메일 인증 화면
- `emailVerified: true` → 메인

앱 재실행으로 세션을 복원할 때도 같습니다. 저장된 토큰이 유효해도 `me.emailVerified`가 거짓이면 메인이 아니라 인증 화면으로 가야 합니다.

`loginSns`도 동일합니다(원래 이 검사가 없었습니다).

---

## 2. 미인증 상태에서는 대부분의 API가 막힙니다 (BREAKING)

미인증 회원이 허용 목록 밖 오퍼레이션을 부르면 **payload가 아니라 최상위 오류**로 거절됩니다.

```json
{ "errors": [ { "extensions": { "code": "EMAIL_NOT_VERIFIED" } } ] }
```

`data`는 null이거나 아예 없습니다. `userErrors`를 보는 코드로는 잡히지 않으니, **최상위 `errors[].extensions.code`를 보는 공통 처리**가 필요합니다.

미인증 상태에서 부를 수 있는 것은 이게 전부입니다.

| 분류 | 오퍼레이션 |
|---|---|
| 인증 화면이 쓰는 것 | `me` · `verifyEmail` · `resendVerification` · `retargetEmailVerification` · `withdrawMember` · `refreshToken` · `logout` |
| 로그인 이전 단계 | `loginEmail` · `loginSns` · `registerEmailMember` · `registerSnsMember` · `requestPasswordReset` · `resetPassword` · `authHealth` |

`refreshToken`이 열려 있는 이유는 accessToken 만료가 5분이라, 막으면 **인증 코드를 입력하는 도중 세션이 끊기기** 때문입니다. `withdrawMember`·`logout`은 잘못된 주소로 가입한 회원의 탈출구입니다.

---

## 3. SNS 가입은 이메일 인증을 하지 않습니다

provider(카카오·구글·애플)가 이메일을 주면 **가입 즉시 인증 완료**입니다. 인증 코드가 발송되지 않고 `verificationId`가 null로 옵니다.

```
registerSnsMember → emailVerified: true,  verificationId: null   # provider가 이메일 제공
                  → emailVerified: false, verificationId: "..."  # provider가 이메일 미제공
```

`verificationId`가 null이면 인증 화면을 띄우지 말고 바로 메인으로 보내면 됩니다.

### 가입 폼에서 지켜야 할 것

**provider가 이메일을 준 경우 이메일 입력을 읽기 전용으로 표시하고 편집을 허용하지 마세요.**

편집을 허용해도 보안 문제는 없습니다 — 서버가 제출값을 **무시하고** provider 응답값을 저장합니다. 다만 그렇게 되면 **사용자가 입력한 주소와 실제 저장된 주소가 달라집니다.** 사용자는 자기가 입력한 주소로 가입됐다고 믿게 되므로, 폼에서 막아 주셔야 합니다.

provider가 이메일을 주지 않으면(카카오·애플에서 흔합니다) 입력 폼을 비워 두고 사용자가 직접 입력하게 하면 됩니다. 그 경우는 이메일 가입과 똑같이 인증 절차를 밟습니다.

---

## 4. 이메일 변경은 자체 가입 회원만 (BREAKING)

SNS 회원의 `changeEmail`은 `NOT_EMAIL_MEMBER`로 거절됩니다. SNS 이메일은 provider에 종속된 값이라 우리 쪽에서 바꾸면 provider가 주는 값과 영구히 어긋나기 때문입니다.

**설정 화면의 이메일 변경 진입점은 `me.canChangeEmail`로 판단하세요.**

```graphql
query { me { canChangeEmail } }   # Provider.EMAIL 회원만 true
```

`provider`를 받아서 클라이언트가 판정하지 마세요. 나중에 규칙에 조건이 하나 더 붙어도 서버만 고치면 되도록 boolean으로 내려보냅니다.

---

## 5. 인증 주소 재입력 — 신설 (`retargetEmailVerification`)

잘못된 주소로 가입한 회원이 **인증 화면에서 주소를 고칠 수 있는** 경로입니다. 이게 없으면 계정을 버릴 수도 쓸 수도 없는 상태가 됩니다.

```graphql
mutation {
  retargetEmailVerification(input: { newEmail: "right@example.com" }) {
    success
    userErrors { code codeValue message }
    verificationId   # 이후 verifyEmail·resendVerification에 이 핸들을 쓴다
    expiresAt
  }
}
```

- 인증을 **마치지 않은** 회원만 쓸 수 있습니다. 마친 회원은 `ALREADY_VERIFIED`로 거절되고, 그쪽은 `changeEmail`의 몫입니다.
- 가입 유형을 가리지 않습니다 — 이메일 가입자와 provider 무이메일 SNS 가입자가 함께 씁니다.
- 이전 인증 코드는 무효화되고 **새 `verificationId`가 나옵니다.** 들고 있던 핸들을 갱신하세요.
- **쿨다운 60초를 `resendVerification`과 공유합니다.** 코드 재발송 직후 주소 정정을 누르면 `RESEND_COOLDOWN_ACTIVE`로 막힙니다(그 반대도 같습니다). 두 버튼을 같은 타이머로 묶어 주세요.
- 주소는 **인증을 통과할 때** 확정됩니다. 정정 직후 `me.email`은 아직 이전 주소입니다 — 이건 의도된 동작입니다(소유를 증명하기 전에 주소를 선점하지 않기 위해서).

---

## 6. 오류 코드를 스키마에서 받을 수 있습니다

지금까지 `userErrors[].code`가 `String!`이라 어떤 값이 오는지 스키마로 알 수 없었습니다. **`ErrorCode` enum을 추가했습니다.**

```graphql
type UserError {
  code: String!         # 정본 (지금까지와 동일)
  codeValue: ErrorCode  # 같은 값을 enum으로. 코드젠으로 받아 분기에 쓰세요
  message: String!
  field: String
}
```

**`codeValue`로 분기하고, null이면 `code`를 읽어 기본 처리하세요.** 서버가 스키마보다 앞선 코드를 낼 때 `codeValue`만 null이 되고 응답은 깨지지 않습니다.

각 payload의 `userErrors` 필드 설명에 **그 오퍼레이션이 실제로 내는 코드**를 적어 두었습니다. 스키마를 보면 됩니다.

### 최상위 오류 5종 — 분기가 각각 다릅니다

`errors[].extensions.code`로 옵니다. 스키마의 `TopLevelErrorCode`에 문서로 적어 두었습니다(GraphQL 오류는 스키마로 형이 잡히지 않아 타입이 아니라 문서입니다).

| 코드 | 의미 | 클라이언트 동작 |
|---|---|---|
| `UNAUTHENTICATED` | 토큰 없음/무효 | 로그인 화면 |
| `EMAIL_NOT_VERIFIED` | 미인증 회원의 비허용 호출 | **인증 화면** |
| `INVALID_TOKEN` | 토큰 서명·만료·타입 불일치 | refresh로 재발급 시도 |
| `TOKEN_REUSE_DETECTED` | 재사용 감지(탈취 의심), 전체 폐기 | **재발급 불가 — 재로그인** |
| `FORBIDDEN` | 권한 없음 | 권한 안내 |

`INVALID_TOKEN`과 `TOKEN_REUSE_DETECTED`는 **이번에 코드가 새로 붙었습니다.** 이전에는 코드 없이 `errorType: INTERNAL`로 나가서 자동 재로그인이 이 상황을 인식하지 못했습니다. 두 코드는 동작이 다르니 반드시 나눠 처리하세요 — 재사용 감지는 재발급으로 회복되지 않습니다.

### 코드 검증 실패는 오류가 아닙니다

`verifyEmail` · `confirmEmailChange` · `resetPassword`에서 **코드가 틀린 것은 실패가 아니라 결과**입니다.

```
verifyEmail → success: true, verified: false, failureReason: "CODE_MISMATCH"
```

`success`는 "요청이 정상 처리됐는가"이지 "코드가 맞았는가"가 아닙니다. `userErrors`가 아니라 `failureReason`을 보세요.

- `verifyEmail` · `confirmEmailChange`: `EXPIRED` · `CONSUMED` · `MAX_ATTEMPTS_EXCEEDED` · `CODE_MISMATCH`
- `resetPassword`: 위 4개 + `INVALID_OR_EXPIRED`

---

## 7. 리포트하신 4건에 대한 답 (2026-08-17 추가)

### ① 탈퇴 FK 오류 — 고쳤습니다

`deleteMember`가 `member`를 참조하는 FK 자식 5개 중 `password_reset`을 빠뜨리고 있었습니다. 정확한 진단이었습니다. 애플리케이션 레벨에서 함께 지우도록 고쳤고(스키마 CASCADE가 아니라 코드에서 — 무엇이 함께 지워지는지 보여야 하므로), **FK 자식이 늘었는데 삭제 목록에 추가를 잊으면 실패하는 테스트**를 넣었습니다.

"다른 테이블도 확인해 달라"는 요청도 함께 봤습니다. `member` 참조 FK는 5개(`email_verification` · `member_profile` · `member_role` · `member_terms_agreement` · `password_reset`)이고 나머지 4개는 이미 정리되고 있었습니다. refresh 토큰은 Valkey에 있어 FK 대상이 아닙니다.

### ② SNS 회원 재설정 메일 — 오진입니다

SNS 전용 회원에게는 발송되지 않습니다. `requestPasswordReset`은 `provider = 'EMAIL'`로만 조회하고, SNS 회원의 `provider_user_id`는 provider가 준 값이라 이메일 HMAC과 애초에 형태가 다릅니다(64자 hex vs 10~21자). 조회 자체가 불가능합니다.

**테스트하신 주소에 EMAIL 계정이 함께 존재했을 가능성이 큽니다.** POL-002·R5가 `(KAKAO, a@b.com)`과 `(EMAIL, a@b.com)`의 공존을 허용합니다. 로컬 DB를 확인해 보니 실제로 두 주소가 각각 EMAIL+KAKAO, EMAIL+GOOGLE로 존재하고, `password_reset` 5행이 **전부 `provider=EMAIL` 회원 것**입니다. 즉 코드는 그 EMAIL 계정에 정상 발급된 것이고 `CODE_MISMATCH`는 올바른 응답입니다.

재현하시려면 **카카오로만 가입하고 이메일 가입 이력이 없는** 주소로 시도해 주세요. 기대: 요청은 조용히 성공, 메일 없음, `resetPassword`는 `INVALID_OR_EXPIRED`.

다만 이 보장이 테스트로 고정돼 있지 않아 우연에 의존하고 있었습니다. **비밀번호가 없는 계정에는 발급도 발송도 하지 않는다**는 단위 테스트를 추가했습니다.

### ③ SNS 회원에게 이유를 알려 줄지 — (B) 현행 유지

`NOT_EMAIL_MEMBER`를 실으면 **주소만으로 "이 사람이 우리 서비스에 가입했는지 + 어떤 SNS를 쓰는지"** 를 확인할 수 있는 조회 수단이 됩니다. 로그인·가입 경로에서 계정 열거를 막아 온 것과 정면으로 어긋납니다. 앱이 이미 적용해 두신 (B)를 유지해 주세요.

대신 안내 문구에 "이메일로 가입한 계정에만 발송됩니다. SNS로 가입하셨다면 해당 SNS로 로그인해 주세요"처럼 **조건을 알려 주되 이 계정이 어느 쪽인지는 말하지 않는** 형태를 권합니다. 사용자는 스스로 판단할 수 있고 제3자는 아무것도 알아내지 못합니다.

### ④ INVALID_INPUT 사유 분리 — 둘 다 했습니다

`PASSWORD_POLICY_VIOLATION` 코드를 만들고 `field: "input.password"`도 채웁니다.

```json
{ "code": "PASSWORD_POLICY_VIOLATION", "codeValue": "PASSWORD_POLICY_VIOLATION",
  "field": "input.password", "message": "비밀번호에 특수문자를 포함해야 합니다." }
```

어느 규칙을 어겼는지까지는 나누지 않았습니다 — 전체 규칙(8~100자·영문·숫자·특수문자)을 안내하는 편이 사용자에게 낫고, 규칙이 바뀔 때 클라이언트를 고치지 않아도 됩니다.

`field`는 지금까지 항상 null이었는데, 특정 입력을 가리키는 코드 6개에 경로를 채웠습니다: `PASSWORD_POLICY_VIOLATION`·`EMAIL_ALREADY_IN_USE`·`INVALID_EMAIL_FORMAT`·`SAME_AS_CURRENT_EMAIL`·`INVALID_NICKNAME`·`NICKNAME_ALREADY_TAKEN`.

### 부수 — DB 예외 원문 노출도 고쳤습니다

지적하신 대로 POL-013 위반이었고, 탈퇴만이 아니라 **매핑되지 않은 모든 예외**가 같은 상태였습니다. 이제 고정 메시지와 `INTERNAL_ERROR` 코드만 나가고 원인·스택은 서버 로그에만 남습니다.

```json
{ "errors": [ { "message": "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                "extensions": { "code": "INTERNAL_ERROR" } } ] }
```

이 코드를 받으면 재시도 안내를 띄우시면 됩니다. 상세는 서버 로그에 있으니 재현 시각과 오퍼레이션 이름을 주시면 추적해 드립니다.

---

## 8. 인증 코드 입력은 6칸 개별 박스로

단일 텍스트 필드가 아니라 6개의 개별 입력 박스입니다.

- 한 칸에 숫자를 입력하면 다음 칸으로 포커스 이동
- 빈 칸에서 삭제를 누르면 이전 칸으로 포커스 이동
- 6자리 문자열 붙여넣기 → 여섯 칸이 순서대로 채워짐
- **앞자리 0을 보존해서** 검증에 전달 (`012345`를 `12345`로 만들지 마세요)

안내·오류 문구는 EN/KO/JA/ZH 4개 언어로 제공합니다. `ZH`는 **번체(正體)** 입니다 — 간체를 쓰지 마세요.

---

## 요약 체크리스트

- [ ] `loginEmail`의 `EMAIL_NOT_VERIFIED` 분기 제거 → `me.emailVerified`로 라우팅
- [ ] 세션 복원 시에도 `me.emailVerified`로 진입 경로 결정
- [ ] 최상위 `errors[].extensions.code` 공통 처리 추가 (5종, 각각 다른 동작)
- [ ] `INVALID_TOKEN`(재발급)과 `TOKEN_REUSE_DETECTED`(재로그인) 분리
- [ ] SNS 가입 폼: provider 이메일 제공 시 읽기 전용, 미제공 시 입력 가능
- [ ] `registerSnsMember`의 `verificationId`가 null이면 인증 화면 건너뛰기
- [ ] 설정 화면 이메일 변경 진입점을 `me.canChangeEmail`로 제어
- [ ] `retargetEmailVerification` 화면 추가 (인증 화면 안에서 "주소가 틀렸나요?")
- [ ] 재발송/주소정정 버튼을 60초 공유 타이머로 묶기
- [ ] `userErrors[].codeValue`(enum)로 분기, null이면 `code` 폴백
- [ ] `failureReason`을 `userErrors`와 구분해 처리
- [ ] 인증 코드 입력을 6칸 박스로, 앞자리 0 보존
- [ ] `PASSWORD_POLICY_VIOLATION` + `field`로 비밀번호 입력란에 오류 표시
- [ ] `INTERNAL_ERROR` 수신 시 재시도 안내 (내부 상세는 더 이상 오지 않음)

---

## 참고 — 서버가 보장하는 것 / 클라이언트가 지켜야 하는 것

서버는 **저장값의 정확성**을 보장합니다. 클라이언트가 SNS 가입 폼에서 이메일 편집을 허용해도 임의 주소가 인증 처리되지 않습니다(서버가 제출값을 버리고 provider 값을 씁니다).

클라이언트가 지켜야 하는 것은 **사용자에게 보이는 것과 실제가 일치하는 것**입니다 — 편집을 허용하면 사용자가 입력한 주소와 저장된 주소가 갈립니다. 보안이 아니라 신뢰의 문제입니다.
