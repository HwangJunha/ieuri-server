package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.command.DeletePostImageCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.exception.UnsupportedContentTypeException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.command.PostImageUploadUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.query.PostImageConfirmFailure
import org.around.ieuri.community.application.query.PostImageConfirmResult
import org.around.ieuri.community.application.query.PostImageUploadUrlView
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.ImageFormat
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 사진 업로드 발급·확인·삭제. **서버는 이미지 바이트를 중계하지 않는다.**
 *
 * 흐름은 네 단계다. 앱이 크기·타입을 알리면 서버가 검증 후 presigned URL과 임시 키를 주고,
 * 앱이 그 URL로 스토리지에 직접 올린 뒤 완료를 알리면, 서버가 실제 크기·매직바이트를
 * 확인하고 통과한 것만 확정 위치로 옮긴다.
 *
 * **발급과 확인은 여러 장을 한 번에 다룬다** — 5장에 왕복 15회는 과하다.
 * 다만 성패 처리가 다르다: 발급은 하나라도 어긋나면 전부 거부하고(부분 발급은 클라이언트가
 * 추적하기 어렵다), 확인은 장별로 갈린다(한 장 실패로 나머지를 버릴 이유가 없다).
 *
 * **검증은 여러 층에 나눠 건다** — 어느 한 층도 단독으로는 완전하지 않다.
 *  1. 발급 시 선언된 contentType·contentLength 검사
 *  2. 그 두 값을 **서명 항목에 포함** — 선언과 다르게 올리면 스토리지가 거부한다
 *  3. 확인 시 실제 크기 재확인 (서명을 우회한 경로 대비)
 *  4. 확인 시 매직바이트 (선언한 형식과 내용이 맞는지)
 */
@Service
class PostImageUploadService(
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
    private val policy: CommunityPolicy,
) : PostImageUploadUseCase {

    /**
     * **하나라도 어긋나면 전부 발급하지 않는다.** 일부만 발급하면 클라이언트가 어느 장이
     * 실패했는지 추적하며 나머지를 이어 붙여야 하는데, 그 복잡도가 이득보다 크다.
     */
    override fun issueUploadUrls(
        command: IssuePostImageUploadUrlsCommand,
        memberId: Long,
    ): List<PostImageUploadUrlView> {
        if (command.images.isEmpty()) return emptyList()
        if (command.images.size > Post.MAX_PHOTOS) throw TooManyPhotosException()

        command.images.forEach { image ->
            if (image.contentType.trim().lowercase() !in policy.allowedContentTypes) {
                throw UnsupportedContentTypeException()
            }
            if (image.contentLength <= 0 || image.contentLength > policy.maxUploadBytes) {
                throw UnsupportedContentTypeException("이미지 크기가 허용 범위를 벗어났습니다.")
            }
        }

        // 만료는 요청 전체가 같다 — 장마다 다르면 어느 것이 먼저 죽는지 클라이언트가 알기 어렵다.
        val expiresAt = clockPort.now().plus(policy.uploadUrlTtl)

        return command.images.map { image ->
            // 키는 서버가 정하고 클라이언트가 지정할 수 없다. 키에 memberId를 넣는 것이
            // 나중에 소유권 판정의 근거가 된다.
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
     * **장별로 성패가 갈린다.** 한 장이 깨졌다고 나머지를 버리면 유저가 전부 다시 올려야 한다.
     *
     * 각 장의 판정 순서는 소유권 → 존재 → 크기 → 매직바이트다. 소유권을 먼저 보는 이유는
     * 남의 키인지 확인하는 데 저장소를 왕복할 이유가 없고, 저장소를 먼저 물으면 타인의 키가
     * 존재하는지를 응답 시간으로 알아낼 수 있기 때문이다.
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
                // 실패 사유를 구분해 알려 주면 남의 오브젝트 키가 존재하는지 캐낼 수 있다.
                failed += PostImageConfirmFailure(objectKey = raw, code = PHOTO_NOT_UPLOADED)
            }
        }
        return PostImageConfirmResult(confirmed = confirmed, failed = failed)
    }

    /** 통과하면 확정 키, 아니면 null. 실패 사유는 호출자가 하나로 뭉뚱그린다. */
    private fun confirmOne(raw: String, memberId: Long): String? {
        val key = PhotoObjectKey.parse(raw) ?: return null
        // 확인 대상은 임시 키뿐이다 — 이미 확정된 키를 다시 확인하면 원본이 없어 실패한다.
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
     * 본인이 발급받은 키만 지울 수 있다. 임시·확정 둘 다 지운다 — 작성을 취소했거나
     * 글에서 뺀 사진을 정리하는 통로다.
     *
     * 이미 없는 오브젝트를 지워도 오류가 아니다(멱등). 다만 남의 키·위조 키는 거부한다.
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
