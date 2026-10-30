package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.ProfileImageUploadUrlView

/** 프로필 이미지(SPEC-0011 R6): 업로드 URL 발급 → 클라이언트 직접 PUT → 확인. */
interface ProfileImageUseCase {
    fun issueProfileImageUploadUrl(command: IssueProfileImageUploadUrlCommand, memberId: Long): ProfileImageUploadUrlView

    /** 업로드 확인 후 회원에 반영. 반영된 확정 키를 돌려준다. */
    fun confirmProfileImageUpload(command: ConfirmProfileImageUploadCommand, memberId: Long): String

    /** 이미 없어도 성공한다(멱등). */
    fun removeProfileImage(memberId: Long)
}
