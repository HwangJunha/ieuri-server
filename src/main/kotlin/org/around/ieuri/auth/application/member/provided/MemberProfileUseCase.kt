package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.ProfileImageUploadUrlView
import org.around.ieuri.auth.application.member.provided.dto.RemoveProfileImageCommand
/** 프로필 수정 유스케이스(SPEC-0011 R5·R6). 전부 본인 데이터 대상이다. */
interface MemberProfileUseCase {
    /** 닉네임 변경. 반영된 값(공백 제거 후)을 돌려준다. */
    fun changeNickname(command: ChangeNicknameCommand): String

    fun issueProfileImageUploadUrl(command: IssueProfileImageUploadUrlCommand): ProfileImageUploadUrlView

    /** 업로드 확인 후 회원에 반영. 반영된 확정 키를 돌려준다. */
    fun confirmProfileImageUpload(command: ConfirmProfileImageUploadCommand): String

    /** 이미 없어도 성공한다(멱등). */
    fun removeProfileImage(command: RemoveProfileImageCommand)
}
