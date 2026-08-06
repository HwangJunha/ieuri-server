package org.around.ieuri.auth.domain.entity

import org.around.ieuri.auth.domain.vo.Nickname

/**
 * 회원 프로필. Member 애그리거트에 소유되는 엔티티.
 * 가입 시 자동 생성된 닉네임으로 시작하며(SPEC-0011 R20) 표시용이라 평문으로 다룬다.
 *
 * 변경은 애그리거트 루트(Member)를 거친다 — 여기의 setter는 private이다.
 */
class Profile(
    nicknameInitial: Nickname,
    profileImageUrlInitial: String?,
) {
    var nickname: Nickname = nicknameInitial
        private set

    /** 확정된 오브젝트 키. 클라이언트가 CDN 베이스 URL과 조합해 쓴다. 없으면 null. */
    var profileImageUrl: String? = profileImageUrlInitial
        private set

    internal fun changeNickname(value: Nickname) {
        nickname = value
    }

    internal fun changeProfileImage(objectKey: String?) {
        profileImageUrl = objectKey
    }
}
