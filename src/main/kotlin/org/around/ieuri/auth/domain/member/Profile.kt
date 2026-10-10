package org.around.ieuri.auth.domain.member

/** 회원 프로필. 회원마다 한 행이고 루트([Member])가 만든다. */
data class Profile(
    val nickname: Nickname,
    /** 확정된 오브젝트 키. 클라이언트가 CDN 베이스 URL과 조합해 쓴다. 없으면 null. */
    val profileImageUrl: String?,
)
