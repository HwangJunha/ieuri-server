package org.around.ieuri.auth.domain.entity

import org.around.ieuri.auth.domain.vo.Nickname

/**
 * 회원 프로필. Member 애그리거트에 소유되는 엔티티.
 * provider가 준 닉네임/프로필이미지로 초기화되며 표시용이라 평문으로 다룬다.
 */
class Profile(
    val nickname: Nickname,
    val profileImageUrl: String?,
)
