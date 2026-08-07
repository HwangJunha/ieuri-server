package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.ChangeNicknameCommand
import org.around.ieuri.auth.application.command.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.command.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.command.RemoveProfileImageCommand
import org.around.ieuri.auth.application.query.ProfileImageUploadUrlView

/**
 * 프로필 수정 유스케이스(SPEC-0011 R5·R6). 전부 본인 데이터 대상이라 별도 권한 계층이 없다.
 */
interface MemberProfileUseCase {
    /** 닉네임 변경. 반영된 값(공백 제거 후)을 돌려준다. */
    fun changeNickname(command: ChangeNicknameCommand): String

    fun issueProfileImageUploadUrl(command: IssueProfileImageUploadUrlCommand): ProfileImageUploadUrlView

    /** 업로드 확인 후 회원에 반영. 반영된 확정 키를 돌려준다. */
    fun confirmProfileImageUpload(command: ConfirmProfileImageUploadCommand): String

    /** 이미 없어도 성공한다(멱등). */
    fun removeProfileImage(command: RemoveProfileImageCommand)
}
