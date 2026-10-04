package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.command.DeletePostImageCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.exception.UnsupportedContentTypeException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.command.PostImageUploadUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.query.PostImageConfirmFailure
import org.around.ieuri.community.application.query.PostImageConfirmResult
import org.around.ieuri.community.application.query.PostImageUploadUrlView
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.around.ieuri.community.domain.shared.PhotoNotUploadedException
import org.around.ieuri.community.domain.shared.TooManyPhotosException
import org.around.ieuri.community.domain.vo.ImageFormat
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 사진 업로드 발급·확인·삭제. 서버는 이미지 바이트를 중계하지 않는다 — presigned URL로 직접 올린다.
 * - 발급은 하나라도 어긋나면 전부 거부하고, 확인은 장별로 성패가 갈린다.
 * - 검증: 발급 시 선언값 검사와 서명 포함, 확인 시 실제 크기·매직바이트 재확인.
 */
@Service
class PostImageUploadService(
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
    private val policy: CommunityPolicy,
) : PostImageUploadUseCase {

    /** 하나라도 어긋나면 전부 발급하지 않는다. 만료 시각은 요청 전체가 같다. */
    override fun issueUploadUrls(
        command: IssuePostImageUploadUrlsCommand,
        memberId: Long,
    ): List<PostImageUploadUrlView> {
        if (command.images.isEmpty()) return emptyList()
        if (command.images.size > AttachedPhotos.MAX_PHOTOS) throw TooManyPhotosException()

        command.images.forEach { image ->
            if (image.contentType.trim().lowercase() !in policy.allowedContentTypes) {
                throw UnsupportedContentTypeException()
            }
            if (image.contentLength <= 0 || image.contentLength > policy.maxUploadBytes) {
                throw UnsupportedContentTypeException("이미지 크기가 허용 범위를 벗어났습니다.")
            }
        }

        val expiresAt = clockPort.now().plus(policy.uploadUrlTtl)

        return command.images.map { image ->
            // 키는 서버가 정한다. 키에 든 memberId가 소유권 판정의 근거다.
            val key = PhotoObjectKey.issue(memberId, UUID.randomUUID())
            val uploadUrl = objectStoragePort.presignPut(
                objectKey = key.value,
                contentType = image.contentType.trim().lowercase(),
                contentLength = image.contentLength,
                expiresAt = expiresAt,
            )
            PostImageUploadUrlView(uploadUrl = uploadUrl, objectKey = key.value, expiresAt = expiresAt)
        }
    }

    /**
     * 장별로 성패가 갈린다. 판정 순서는 소유권 → 존재 → 크기 → 매직바이트다.
     * - 소유권을 저장소 조회보다 먼저 본다 — 응답 시간으로 남의 키 존재가 새지 않게.
     */
    override fun confirmUploads(
        command: ConfirmPostImageUploadsCommand,
        memberId: Long,
    ): PostImageConfirmResult {
        val confirmed = mutableListOf<String>()
        val failed = mutableListOf<PostImageConfirmFailure>()

        for (raw in command.objectKeys.distinct()) {
            val finalKey = confirmOne(raw, memberId)
            if (finalKey != null) {
                confirmed += finalKey
            } else {
                // 실패 사유는 PHOTO_NOT_UPLOADED 하나로 뭉뚱그린다.
                failed += PostImageConfirmFailure(objectKey = raw, code = PHOTO_NOT_UPLOADED)
            }
        }
        return PostImageConfirmResult(confirmed = confirmed, failed = failed)
    }

    /** 통과하면 확정 키, 아니면 null. */
    private fun confirmOne(raw: String, memberId: Long): String? {
        val key = PhotoObjectKey.parse(raw) ?: return null
        // 확인 대상은 임시 키뿐이다.
        if (key.confirmed || !key.ownedBy(memberId)) return null

        val size = objectStoragePort.findSize(key.value) ?: return null
        if (size <= 0 || size > policy.maxUploadBytes) return null

        val prefix = objectStoragePort.readPrefix(key.value, ImageFormat.MAGIC_BYTES_LENGTH) ?: return null
        if (ImageFormat.detect(prefix) == null) return null

        val confirmed = key.toConfirmed()
        objectStoragePort.move(sourceKey = key.value, targetKey = confirmed.value)
        return confirmed.value
    }

    /**
     * 본인 키만 지운다(임시·확정 모두).
     * - 이미 없는 오브젝트는 오류가 아니다(멱등). 남의 키·위조 키는 PhotoNotUploadedException이다.
     */
    override fun deleteImage(command: DeletePostImageCommand, memberId: Long) {
        val key = PhotoObjectKey.parse(command.objectKey) ?: throw PhotoNotUploadedException()
        if (!key.ownedBy(memberId)) throw PhotoNotUploadedException()

        objectStoragePort.delete(key.value)
    }

    private companion object {
        const val PHOTO_NOT_UPLOADED = "PHOTO_NOT_UPLOADED"
    }
}
