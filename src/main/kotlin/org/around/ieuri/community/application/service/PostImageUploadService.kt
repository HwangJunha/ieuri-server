package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.ConfirmPostImageUploadCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlCommand
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.UnsupportedContentTypeException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.command.PostImageUploadUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.query.PostImageUploadUrlView
import org.around.ieuri.community.domain.vo.ImageFormat
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 사진 업로드 발급·확인. **서버는 이미지 바이트를 중계하지 않는다.**
 *
 * 흐름은 네 단계다. 앱이 크기·타입을 알리면 서버가 검증 후 presigned URL과 임시 키를 주고,
 * 앱이 그 URL로 스토리지에 직접 올린 뒤 완료를 알리면, 서버가 실제 크기·매직바이트를
 * 확인하고 통과한 것만 확정 위치로 옮긴다.
 *
 * **검증은 여러 층에 나눠 건다** — 어느 한 층도 단독으로는 완전하지 않다.
 *  1. 발급 시 선언된 contentType·contentLength 검사
 *  2. 그 두 값을 **서명 항목에 포함** — 선언과 다르게 올리면 스토리지가 거부한다
 *  3. 확인 시 실제 크기 재확인 (서명을 우회한 경로 대비)
 *  4. 확인 시 매직바이트 (선언한 형식과 내용이 맞는지)
 *
 * 트랜잭션이 없다 — 이 유스케이스는 DB를 건드리지 않는다.
 */
@Service
class PostImageUploadService(
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
    private val policy: CommunityPolicy,
) : PostImageUploadUseCase {

    override fun issueUploadUrl(
        command: IssuePostImageUploadUrlCommand,
        memberId: Long,
    ): PostImageUploadUrlView {
        // 지원 형식이 아니면 URL 자체를 발급하지 않는다 — 올린 뒤 거부하면 대역폭만 쓴다.
        if (command.contentType.trim().lowercase() !in policy.allowedContentTypes) {
            throw UnsupportedContentTypeException()
        }
        if (command.contentLength <= 0 || command.contentLength > policy.maxUploadBytes) {
            throw UnsupportedContentTypeException("이미지 크기가 허용 범위를 벗어났습니다.")
        }

        // 키는 서버가 정하고 클라이언트가 지정할 수 없다. 키에 memberId를 넣는 것이
        // 나중에 소유권 판정의 근거가 된다.
        val key = PhotoObjectKey.issue(memberId, UUID.randomUUID())
        val expiresAt = clockPort.now().plus(policy.uploadUrlTtl)

        val uploadUrl = objectStoragePort.presignPut(
            objectKey = key.value,
            contentType = command.contentType.trim().lowercase(),
            contentLength = command.contentLength,
            expiresAt = expiresAt,
        )
        return PostImageUploadUrlView(
            uploadUrl = uploadUrl,
            objectKey = key.value,
            expiresAt = expiresAt,
        )
    }

    /**
     * 업로드 완료 확인. 소유권을 **먼저** 본다 — 남의 키인지 확인하는 데 저장소를 왕복할
     * 이유가 없고, 저장소를 먼저 물으면 타인의 키가 존재하는지를 응답 시간으로 알아낼 수 있다.
     *
     * 소유권 실패·부재·형식 위반이 모두 같은 코드다. 구분해 알려 주면 남의 오브젝트 키가
     * 존재하는지 캐내는 수단이 된다.
     */
    override fun confirmUpload(command: ConfirmPostImageUploadCommand, memberId: Long): String {
        val key = PhotoObjectKey.parse(command.objectKey) ?: throw PhotoNotUploadedException()
        // 확인 대상은 임시 키뿐이다 — 이미 확정된 키를 다시 확인하면 원본이 없어 실패한다.
        if (key.confirmed || !key.ownedBy(memberId)) throw PhotoNotUploadedException()

        val size = objectStoragePort.findSize(key.value) ?: throw PhotoNotUploadedException()
        if (size <= 0 || size > policy.maxUploadBytes) throw PhotoNotUploadedException()

        val prefix = objectStoragePort.readPrefix(key.value, ImageFormat.MAGIC_BYTES_LENGTH)
            ?: throw PhotoNotUploadedException()
        if (ImageFormat.detect(prefix) == null) throw PhotoNotUploadedException()

        val confirmed = key.toConfirmed()
        objectStoragePort.move(sourceKey = key.value, targetKey = confirmed.value)
        return confirmed.value
    }
}
