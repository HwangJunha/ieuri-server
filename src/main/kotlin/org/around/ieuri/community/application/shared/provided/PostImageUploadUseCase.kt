package org.around.ieuri.community.application.shared.provided

import org.around.ieuri.community.application.shared.dto.PostImageConfirmResult
import org.around.ieuri.community.application.shared.dto.PostImageUploadUrlView
import org.around.ieuri.community.application.shared.provided.dto.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.shared.provided.dto.DeletePostImageCommand
import org.around.ieuri.community.application.shared.provided.dto.IssuePostImageUploadUrlsCommand

/** 사진 업로드. 서버는 바이트를 중계하지 않고 URL 발급과 업로드 확인만 한다. */
interface PostImageUploadUseCase {
    /** 여러 장을 한 번에 발급한다. 하나라도 어긋나면 전부 거부한다. */
    fun issueUploadUrls(
        command: IssuePostImageUploadUrlsCommand,
        memberId: Long,
    ): List<PostImageUploadUrlView>

    /** 여러 장을 한 번에 확인한다. 장별로 성패가 갈린다. */
    fun confirmUploads(
        command: ConfirmPostImageUploadsCommand,
        memberId: Long,
    ): PostImageConfirmResult

    /** 업로드한 사진을 지운다. 글에 붙이기 전에도, 붙인 뒤에도 쓴다. */
    fun deleteImage(command: DeletePostImageCommand, memberId: Long)
}
