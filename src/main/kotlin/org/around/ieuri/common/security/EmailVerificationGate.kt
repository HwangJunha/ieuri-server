package org.around.ieuri.common.security

/**
 * 이메일 미인증 회원의 오퍼레이션 접근 판정(SPEC-0024 R9).
 *
 * **허용 목록은 화이트리스트다.** 블랙리스트로 두면 새 오퍼레이션이 추가될 때마다 조용히
 * 열린다 — 목록에 없는 이름은 언제나 거부다(R9-5).
 *
 * 순수 함수로 분리한 이유는 어댑터 안에만 있으면 시험할 수 없기 때문이다. 어댑터는 이 판정을
 * 부르고 결과에 따라 예외를 던지는 역할만 한다.
 */
object EmailVerificationGate {

    /**
     * 인증을 마치지 않은 회원도 호출할 수 있는 오퍼레이션.
     *
     * `withdrawMember`·`logout`은 **인증 화면에 갇힌 회원의 탈출구**다. 없으면 잘못된 주소로
     * 가입한 회원이 계정을 버릴 수도, 쓸 수도 없는 상태가 된다.
     *
     * `refreshToken`은 세션 유지 때문이다. accessToken 만료가 5분이라(POL-008) 갱신을 막으면
     * 인증 코드를 입력하는 도중 세션이 끊긴다.
     */
    val ALLOWED_OPERATIONS: Set<String> = setOf(
        "me",
        "verifyEmail",
        "resendVerification",
        "retargetEmailVerification",
        "withdrawMember",
        "refreshToken",
        "logout",
    )

    /**
     * [rootFieldNames]의 오퍼레이션을 인증 상태 [emailVerified]로 호출해도 되는지.
     *
     * **인증 완료 회원은 전면 통과한다**(R9-3). 미인증 회원은 요청에 담긴 루트 필드가 **전부**
     * 허용 목록에 있을 때만 통과한다 — 한 요청에 허용·비허용을 섞어 보내 비허용을 통과시키는
     * 길을 막는다.
     *
     * **주체가 없는(비로그인) 요청은 이 판정에 오지 않는다**(R9-4). 그쪽은 기존 인증 처리가
     * 맡으며, 순서가 뒤집히면 비로그인 요청이 `EMAIL_NOT_VERIFIED`를 받아 클라이언트가 잘못된
     * 화면으로 보낸다. 호출자가 지켜야 하는 전제다.
     *
     * 루트 필드가 하나도 없는 요청(빈 셀렉션)은 통과시킨다 — 판정할 대상이 없고, 그런 문서는
     * 검증 단계에서 이미 걸린다.
     */
    fun isAllowed(rootFieldNames: Collection<String>, emailVerified: Boolean): Boolean =
        emailVerified || rootFieldNames.all { it in ALLOWED_OPERATIONS }
}
