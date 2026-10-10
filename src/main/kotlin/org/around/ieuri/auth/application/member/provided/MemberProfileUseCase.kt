package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand

/** 프로필의 DB 단계(SPEC-0011 R5·R6). 전부 본인 데이터 대상이다. */
interface MemberProfileUseCase {
    /** 닉네임 변경. 반영된 값(공백 제거 후)을 돌려준다. */
    fun changeNickname(command: ChangeNicknameCommand): String

    /** 확인이 끝난 임시 키를 회원의 프로필 이미지로 삼고, 이전 키를 돌려준다(없었으면 null). */
    fun attachProfileImage(memberId: Long, pendingObjectKey: String): String?

    /** 프로필 이미지를 떼고 이전 키를 돌려준다. 이미 없어도 성공한다. */
    fun detachProfileImage(memberId: Long): String?
}
