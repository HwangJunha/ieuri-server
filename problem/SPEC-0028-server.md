> spec: SPEC-0028 · source: `Specs/SPEC-0028/spec.md` (Obsidian) · status: approved

# FCM 앱 푸시 알림 — 서버 설계

요구사항·수용 기준·계약은 스펙에 있다. 이 문서는 이 저장소의 설계 결정만 담는다.

이슈 [#94](https://github.com/HwangJunha/ieuri/issues/94) · 브랜치 `feature/SPEC-0028-fcm-push`

## 1. 새 도메인 — `alarm`

`org.around.ieuri.alarm`을 신설한다. 기존 4개 도메인(auth · community · tourism · platform)
어디에도 넣지 않는다.

알림은 **전달 인프라**이지 특정 도메인의 기능이 아니다. 트리거가 community(DM · Q&A)에
있고 수신자는 auth의 회원이며, 앞으로 tourism이 트리거를 더할 수 있다. community에 넣으면
tourism 트리거가 생기는 순간 community가 tourism을 알아야 한다.

`platform`도 아니다 — `apps.md`가 platform을 "DB 구조·배포·관측 등 도메인 횡단 인프라"로
정의하는데, 알림은 사용자에게 보이는 기능이지 인프라 설비가 아니다.

패키지 구조는 기존 도메인과 같다.

```
alarm/
  domain/            DeviceToken, NotificationPreference, NotificationCategory,
                     PushNotification(제목·본문·목적지를 담은 값)
  application/
    command/         RegisterDeviceToken, DeleteDeviceToken, UpdateNotificationPreference
    query/           NotificationPreferenceView
    service/         DeviceTokenCommandService, NotificationPreferenceService,
                     PushDispatchService
    port/in/         유스케이스 인터페이스
    port/out/        DeviceTokenRepositoryPort, NotificationPreferenceRepositoryPort,
                     PushSenderPort
    event/           AnswerCreated, AnswerReplyCreated (community에서 넘어오는 것을 받는 형태)
  infrastructure/
    adapter/in/graphql/    DeviceTokenMutationResolver, NotificationPreferenceResolver
    adapter/out/push/      FcmPushSender (PushSenderPort 구현)
    adapter/out/persistence/
    adapter/in/event/      community 이벤트 리스너
```

## 2. 트리거를 어떻게 받는가

**community가 alarm을 직접 호출하지 않는다.** 호출하면 community가 alarm에 의존하게 되고,
알림 발송이 실패하면 메시지 전송이 함께 실패할 수 있다(R12 위반).

기존 패턴을 그대로 쓴다 — 각 도메인이 outbound 이벤트 포트를 가지고 Spring 어댑터가
발행하는 구조가 `ConversationEventPort` · `TourismEventPublisherPort` · `DomainEventPublisherPort`로
이미 세 번 반복되고 있다. alarm은 그 이벤트를 **구독하는 쪽**에 선다.

| 트리거 | 기존 자리 | alarm이 하는 일 |
|---|---|---|
| DM 새 메시지 | `ConversationCommandService.sendMessage` | 새 도메인 이벤트를 발행하게 하고 구독한다 |
| 답변 등록 | `SpotAnswerCommandService` | 같음 |
| 답글 등록 | `SpotAnswerCommandService` | 같음 |

**DM도 이벤트를 새로 낸다(2026-08-19 변경).** 초안은 기존 `NewMessageStored`를 구독하려
했으나 **그럴 수 없다** — 그 타입은 `community/application/event`에 있고,
`LayeredArchitectureTest`가 도메인 간 구독을 `domain/event` 타입으로만 허용한다. 옮기면
community 내부 구독자(`NewMessageBroadcastListener`)까지 영향을 받으므로, 알림 트리거
3종을 `community/domain/event/PushTriggerEvents.kt`에 새로 둔다.

역할이 갈리는 것이기도 하다 — `NewMessageStored`는 실시간 구독(대화를 보고 있는 사람)을
위한 것이고 푸시는 앱이 꺼진 사람을 위한 것이라, 한 이벤트에 두 목적을 얹지 않는다.

**이 이벤트들은 본문을 싣는다.** 다른 이벤트가 id만 싣는 것과 다른데, DM 본문이 암호화
저장이라(POL-050) 되읽으려면 알림 전용 복호화 조회 포트를 새로 만들어야 하기 때문이다.
노출 범위는 늘지 않는다 — 본문의 목적지가 어차피 FCM이다. **Kafka 전환 시 재검토 대상이다.**

POL-028대로 지금은 Spring ApplicationEvent이고 Kafka 전환은 나중이다. **여기서 POL-048은
적용되지 않는다** — POL-048은 구독 이벤트가 파드를 넘어야 한다는 규정인데, 푸시 발송은
이벤트를 받은 파드가 FCM에 HTTP로 보내면 끝이라 수신자가 어느 파드에 붙어 있는지와
무관하다. 발신 파드에서 처리가 완결된다.

## 3. 발송 실패는 원래 작업을 건드리지 않는다 (R12)

리스너가 예외를 삼킨다. `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받아
**원래 트랜잭션이 커밋된 뒤에** 실행한다.

- 커밋 전에 발송하면 롤백된 메시지의 알림이 나간다
- 리스너가 예외를 던져도 원래 트랜잭션은 이미 끝나 영향이 없다
- 리스너 안에서 예외를 잡아 로그만 남긴다. **재시도하지 않는다**(AC12.3)

### R12는 지연도 포함한다 (2026-08-19 추가)

**`@Async`가 함께 있어야 한다.** Spring 이벤트는 기본이 동기라 `AFTER_COMMIT` 콜백도
커밋한 스레드 — 곧 사용자의 요청 스레드 — 에서 실행된다. 없으면 FCM 왕복이 메시지 전송
응답 시간에 그대로 실려, 발송이 느려질 때 전송도 함께 느려진다. 실패가 전파되지 않는 것만
R12가 아니다.

alarm 전용 executor(`AlarmAsyncConfig`)를 둔다 — 발송이 밀렸을 때 다른 도메인의 비동기
작업이 함께 굶으면 안 된다. 유계 큐를 쓰고 차면 폐기한다(푸시는 유실 허용, POL-048).

**발송은 트랜잭션 밖에서 일어난다.** FCM 호출은 블로킹 HTTP라 트랜잭션 안에 두면 커넥션을
왕복 내내 붙들어 알림과 무관한 질의까지 풀에서 굶는다. 조회와 삭제를 짧은 트랜잭션으로
잘라 `PushDispatchStore`가 갖고, 그 사이에 발송이 놓인다. FCM 클라이언트에는 타임아웃을
명시한다 — 기본값 0은 무제한이다.

FCM 5xx 재시도를 넣지 않는 이유: 알림 1건 누락의 비용이 재시도 큐를 운영하는 비용보다
작다. POL-040이 "근거 없는 시간 기반 수치를 쓰지 말라"고 하는데 재시도 간격에 댈 근거가
없다.

## 4. 무효 토큰 제거 (R4)

**발송 실패 응답이 토큰 수명주기의 주 수단이다.** 기간 만료를 두지 않으므로(§6), 죽은
토큰을 걷어내는 일은 전부 여기서 일어난다.

FCM HTTP v1 오류 코드로 판정한다. 뭉뚱그려 지우면 장애 한 번에 정상 토큰이 전멸한다.

| 오류 코드 | HTTP | 처리 |
|---|---|---|
| `UNREGISTERED` | 404 | **삭제.** 앱이 등록 해제됨 — 삭제·재설치의 정상 경로다 |
| `INVALID_ARGUMENT` | 400 | **토큰 형식 문제일 때만 삭제.** 페이로드 문제일 수도 있어 구분이 필요하다 |
| `SENDER_ID_MISMATCH` | 403 | 삭제하지 않고 오류로 남긴다. 프로젝트 설정 오류이며 삭제로 덮으면 원인이 사라진다 |
| `THIRD_PARTY_AUTH_ERROR` | 401 | 삭제하지 않는다. APNs 자격증명 문제라, 지우면 인증서가 잘못된 동안 iOS 토큰이 전멸한다 |
| `UNAVAILABLE` · `INTERNAL` | 503 · 500 | 삭제하지 않는다. 서비스 측 일시 오류 |
| `QUOTA_EXCEEDED` | 429 | 삭제하지 않는다 |

`INVALID_ARGUMENT`가 유일하게 판단이 필요한 자리다. 같은 코드가 토큰 형식과 메시지
페이로드 양쪽에서 나오므로, 응답 본문으로 어느 쪽인지 가려야 한다. **가릴 수 없으면
지우지 않는다** — 우리 페이로드 버그로 사용자 토큰을 지우는 것이 더 나쁘다.

배치 발송은 토큰별로 결과가 오므로 무효인 것만 골라 지우고 나머지로는 정상 발송한다(AC4.3).

**어댑터가 코드를 해석하고 application에는 결과 형태로 넘긴다.** `PushSenderPort`가
"삭제 대상 토큰 목록"을 돌려주면 FCM 응답 파싱 없이 삭제 로직을 단위 테스트할 수 있다.

APNs 응답을 직접 보지 않는다. FCM이 중계해 자신의 코드로 변환하므로 판정 대상은 FCM
코드 하나다.

## 5. 수신자 언어 (R20)

푸시에는 요청이 없어 `Accept-Language`(POL-039)를 쓸 수 없다. 기기 토큰 레코드가
`Locale`을 함께 들고, 발송 시 그 값으로 제목 문구를 고른다.

**언어는 고정되지 않는다.** `registerDeviceToken`이 업서트라 클라이언트가 같은 `deviceId`로
재호출하면 언어만 갈린다. 한 번 받고 고정하면 OS 언어를 바꾼 사용자가 영영 옛 언어로
알림을 받는다.

같은 회원이 기기마다 다른 언어를 쓸 수 있고, 그것이 정상이다 — 언어는 회원이 아니라
기기의 속성이라 기기 레코드가 갖는다.

**본문은 번역하지 않는다.** 사용자가 쓴 원문을 그대로 싣는다(R13).

## 6. 기기 토큰 저장

- `deviceId`가 재등록 판별 키다(R1). FCM 토큰은 로테이션되므로 키가 될 수 없다
- 유니크 제약은 `deviceId` 단독이다. `(memberId, deviceId)`가 아니다 — 한 기기를 다른
  회원이 쓰기 시작하면 이전 회원 귀속이 남아 엉뚱한 사람에게 발송된다(AC1.4)
- **암호화하지 않는다.** FCM 토큰은 원래 공개된 값이라 POL-012의 대상이 아니다
- 로그에는 원문을 남기지 않는다(POL-013)
- 탈퇴 시 연쇄 삭제(R3). `MemberDataEraseService`에 기존 삭제 대상들과 함께 붙인다

### 기간 만료를 두지 않는다

**보관 기간·파기 배치를 만들지 않는다.** 죽은 토큰은 §4의 발송 실패 응답으로 걷어낸다.

시간 기준 만료는 "안 쓴 지 오래됐으니 죽었다"는 추정이고, 계절적으로 앱을 쓰는 사용자의
살아 있는 토큰을 지운다. 그 실패는 조용하다 — 안 오는 알림은 아무도 신고하지 않는다.

로그아웃 없이 앱을 지운 기기의 행은 다음 발송에서 `UNREGISTERED`가 올 때까지 남는다.
남는 비용은 행 하나이고, 잘못 지운 비용은 알림 두절이다.

DB 스키마는 SPEC-0023의 5분할 구조를 따른다 — `alarm`은 그 다섯에 없으므로 어느 스키마에
둘지는 **서버 하네스가 정한다.**

**`alarm` 스키마를 새로 만든다(2026-08-19 결정).** `auth`에 얹으면 그 스키마에 사는 유일한
비-auth 표가 되어, 컨텍스트당 스키마라는 V62의 전제가 이 한 곳에서만 깨진다. 나중에 알림을
별도 앱으로 떼어낼 때 스키마째 옮기지 못하고 표를 골라내야 한다.

등록해야 하는 자리가 셋이다 — `DbSchema.ALARM`, Hikari `search_path`, `SchemaPlacementTest`의
배치표. 마지막 것이 빠지기 쉬운데 아키텍처 테스트가 잡아 준다.

## 7. 수신 설정 (R19)

회원 단위 저장이다. 기기가 아니라 회원에 묶여야 재설치·기기 변경에도 유지된다(AC19.4).

**설정 행이 없는 회원은 켜진 것으로 본다**(AC10.3 · AC19.1). 가입 시 기본 행을 만들지
않는다 — 만들면 기존 회원 전체에 대한 백필이 필요하고, 없을 때 켜짐으로 읽으면 그
백필이 통째로 사라진다. `Me.notificationPreferences`는 저장된 행과 무관하게 항상 두
항목을 조립해 반환한다.

## 8. FCM 자격증명 (POL-046 준용)

- 서비스 계정 자격증명은 **환경변수로만** 주입한다. 설정 파일과 코드에 리터럴 기본값을
  두지 않는다
- **자격증명이 없어도 애플리케이션은 기동한다.** 부재 시 기동이 아니라 발송만 실패한다 —
  POL-046이 메일에 대해 정한 것과 같은 판단이고, 알림은 그보다 더 실패 범위가 좁다
- 발송 어댑터 클래스명은 `FcmPushSender`다. FCM은 POL-027이 지정한 벤더라 이름에 담아도
  되지만, `PushSenderPort` 인터페이스는 벤더 중립으로 둔다

iOS는 APNs `.p8` 키를 Firebase 콘솔에 업로드해 FCM이 APNs로 중계한다. **서버 코드는
APNs를 직접 호출하지 않는다** — 서버가 아는 것은 FCM 하나뿐이다. `.p8` 업로드는 콘솔
작업이라 이 저장소의 산출물이 아니다.

## 9. 테스트

application · domain 계층 단위 테스트만이다. GraphQL 리졸버 · JPA · 실제 FCM 호출은
범위 밖이다(CLAUDE.md 테스트 범위표).

| 단위 | 검증 대상 |
|---|---|
| `DeviceTokenCommandService` | R1 업서트, R2 멱등 삭제, R20 언어 갱신, AC1.4 소유 이전 |
| 무효 토큰 판정 | R4 — 삭제하는 코드와 보존하는 코드의 구분. 포트가 돌려주는 결과 형태로 주입해 검증한다 |
| `PushDispatchService` | R5~R11 — 수신자 판정, 자기 행위 제외(R8), 차단(R9), 카테고리 게이팅(R10), 페이로드 구성(R11) |
| `NotificationPreferenceService` | R19 — 행이 없을 때 켜짐 기본값, 멱등 갱신 |
| 알림 본문 조립 | R13 — 무번역, 길이 상한 절단, 빈 본문 |

`PushSenderPort` · 리포지토리 포트 · 차단 조회 포트를 스텁으로 두면 위 전부가 프레임워크
없이 검증된다. FCM 응답 파싱은 어댑터의 일이라 무효 토큰 판정은 **포트가 돌려주는 결과
형태**로 표현해 application 계층에서 테스트한다.

`PushDispatchService`가 실제로 발송했는지는 `PushSenderPort` 스텁 호출 여부로 본다.
실제 전달 여부는 인프라라 검증하지 않으며, 그 사실을 여기 남긴다.

## 10. 슬라이스별로 클라이언트가 할 수 있게 되는 것

| 슬라이스 | 이것이 배포되면 클라이언트가 시작할 수 있는 것 |
|---|---|
| **S1** — `registerDeviceToken` · `deleteDeviceToken` 계약, 기기 토큰 저장, 탈퇴 연쇄 삭제 | **등록·해제 배선 전부.** Firebase 초기화, 권한 요청, 토큰 획득, 등록 호출, 로그아웃 해제까지 발송 없이도 완성된다. 토큰이 서버에 쌓이는지 확인 가능 |
| **S3** — 3종 이벤트 구독, FCM 발송, 페이로드 적재, 무효 토큰 제거 | **실제 푸시 수신과 딥링크 검증.** 이 시점까지는 클라이언트가 받을 알림 자체가 없어 수신 코드를 확인할 방법이 없다 |
| **S5** — `Me.notificationPreferences`, `updateNotificationPreference`, 발송 게이팅 | **설정 화면의 토글.** 읽을 필드와 쓸 뮤테이션이 생긴다 |

## 11. 먼저 존재해야 하는 데이터

**없다.** 시드 데이터가 필요한 스펙이 아니다.

수신 설정은 행이 없을 때 켜짐으로 읽으므로 백필이 필요 없고(§7), 기기 토큰은 사용자가
앱을 켤 때 생긴다.

**콘솔 선행 작업은 있다.** Firebase `ieuri` 프로젝트에 앱을 등록하고 iOS APNs `.p8` 키를
업로드해야 발송이 성립한다(POL-027). 코드가 아니라 콘솔 설정이며, 서버 자격증명(§8)과
클라이언트 설정 파일(`google-services.json` · `GoogleService-Info.plist`)이 그 결과물이다.

## 12. 변경 이력

| 날짜 | 변경 | 이유 |
|---|---|---|
| 2026-08-19 | 최초 작성 | 플래너 |
| 2026-08-19 | DM도 새 도메인 이벤트를 발행하도록 변경(§2). 초안은 기존 `NewMessageStored` 구독이었다 | 그 타입이 `application/event`에 있어 도메인 간 구독이 `LayeredArchitectureTest`에 막힌다. 구현 중 발견 |
| 2026-08-19 | 트리거 이벤트가 본문을 싣기로 결정(§2) | DM 본문이 암호화 저장이라 되읽으려면 알림 전용 복호화 포트가 필요하다. Kafka 전환 시 재검토 |
| 2026-08-19 | `alarm` 스키마 신설로 확정(§6) | 설계 문서가 서버 하네스에 위임한 결정 |
| 2026-08-19 | R12에 지연 전가 금지를 명시하고 `@Async`·전용 executor·트랜잭션 분리를 추가(§3) | 리뷰 지적. 없으면 FCM 왕복이 사용자 요청 스레드와 DB 커넥션을 붙든다 |
| 2026-08-19 | 발송 라이브러리를 firebase-admin SDK로 확정(§8) | `sendEach()`가 토큰별 결과와 `MessagingErrorCode`를 돌려줘 §4의 판정이 응답 파싱 없이 성립한다. 사용자 결정 |
