package org.around.ieuri.auth.application.member.provided.dto
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import java.time.Instant

/** 본인 정보(me). 이메일은 평문이며 권한/역할은 클라이언트 메뉴 렌더에 쓴다. */
data class MeView(
    val memberId: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val status: MemberStatus,
    val emailVerified: Boolean,
    val provider: Provider,
    val roles: List<String>,
    val permissions: List<String>,
    val createdAt: Instant,
) {
    /** 이메일 변경 가능 여부(SPEC-0024 R6). 저장하지 않고 provider에서 파생한다. */
    val canChangeEmail: Boolean get() = provider == Provider.EMAIL
}
