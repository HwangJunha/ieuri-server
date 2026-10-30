package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.domain.member.Provider

/** 회원가입 커맨드. 형식 검증만 하고 업무 불변식은 Member.register가 강제한다. */
data class RegisterMemberCommand(
    val provider: Provider,
    val idToken: String,
    /** 폼 제출 이메일(필수). */
    val submittedEmail: String,
    /** 만 14세 이상 확인 체크값. */
    val ageVerified: Boolean,
    val agreements: List<AgreementInput>,
    /** 탈퇴 시 연결 해제용 토큰을 확보하기 위한 provider authorization code. 확보 실패해도 가입은 성공한다. */
    val authorizationCode: String? = null,
    /** provider가 준 닉네임(SPEC-0032 R29). 형식을 검사하지 않는다 — 규칙을 벗어나면 서버가 자동 생성으로 대체한다. */
    val nickname: String? = null,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
        require(submittedEmail.isNotBlank()) { "이메일은 필수 입력값입니다." }
    }
}
