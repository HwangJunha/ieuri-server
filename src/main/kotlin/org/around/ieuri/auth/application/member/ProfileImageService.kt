package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.ProfileImageUseCase
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.ProfileImageUploadUrlView
import org.around.ieuri.auth.application.member.provided.exception.ImageTooLargeException
import org.around.ieuri.auth.application.member.provided.exception.PhotoNotUploadedException
import org.around.ieuri.auth.application.member.provided.exception.UnsupportedImageTypeException
import org.around.ieuri.auth.application.member.required.ProfileImageStoragePort
import org.around.ieuri.auth.domain.member.ImageFormat
import org.around.ieuri.auth.domain.member.ProfileImageObjectKey
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/**
 * 프로필 이미지(SPEC-0011 R6): 발급 → 클라이언트 직접 PUT → 확인.
 * - 트랜잭션을 갖지 않는다. 스토리지 호출 사이의 DB 단계만 [MemberCommandUseCase]가 한 트랜잭션으로 한다.
 */
@Service
class ProfileImageService(
    private val members: MemberCommandUseCase,
    private val storage: ProfileImageStoragePort,
    private val policy: ProfileImagePolicy,
    private val clock: Clock,
) : ProfileImageUseCase {

    /** 형식·크기가 어긋나면 키를 발급하지 않는다. */
    override fun issueProfileImageUploadUrl(command: IssueProfileImageUploadUrlCommand, memberId: Long): ProfileImageUploadUrlView {
        val contentType = command.contentType.trim().lowercase()
        if (contentType !in policy.allowedContentTypes) {
            throw UnsupportedImageTypeException()
        }
        if (command.contentLength <= 0 || command.contentLength > policy.maxUploadBytes) {
            throw ImageTooLargeException()
        }

        val expiresAt = clock.instant().plus(policy.uploadUrlTtl)
        val key = ProfileImageObjectKey.issue(memberId, UUID.randomUUID())
        val uploadUrl = storage.presignPut(
            objectKey = key.value,
            contentType = contentType,
            contentLength = command.contentLength,
            expiresAt = expiresAt,
        )
        return ProfileImageUploadUrlView(uploadUrl = uploadUrl, objectKey = key.value, expiresAt = expiresAt)
    }

    /**
     * 판정 순서는 소유권 → 존재 → 크기 → 매직바이트다. 확인 대상은 임시 키뿐이다.
     * - 교체 시 이전 오브젝트 정리 실패는 반영을 막지 않는다.
     */
    override fun confirmProfileImageUpload(command: ConfirmProfileImageUploadCommand, memberId: Long): String {
        val key = ProfileImageObjectKey.pendingOf(command.objectKey, memberId)

        val size = storage.findSize(key.value)
            ?: throw PhotoNotUploadedException()
        if (size <= 0 || size > policy.maxUploadBytes) {
            throw ImageTooLargeException()
        }
        val prefix = storage.readPrefix(key.value, ImageFormat.MAGIC_BYTES_LENGTH)
            ?: throw PhotoNotUploadedException()
        if (ImageFormat.detect(prefix) == null) {
            throw UnsupportedImageTypeException()
        }

        val confirmed = key.toConfirmed()
        storage.move(sourceKey = key.value, targetKey = confirmed.value)
        val previousKey = members.attachProfileImage(memberId, key.value)
        deleteQuietly(previousKey, confirmed.value)
        return confirmed.value
    }

    override fun removeProfileImage(memberId: Long) {
        deleteQuietly(members.detachProfileImage(memberId), newKey = null)
    }

    /** 이전 오브젝트 정리. 실패해도 삼킨다 — 남은 오브젝트는 스토리지 lifecycle rule이 걷어 간다. */
    private fun deleteQuietly(previousKey: String?, newKey: String?) {
        if (previousKey == null || previousKey == newKey) return
        runCatching { storage.delete(previousKey) }
    }
}
