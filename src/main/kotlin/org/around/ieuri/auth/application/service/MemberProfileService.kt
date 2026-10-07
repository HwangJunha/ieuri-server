package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.ChangeNicknameCommand
import org.around.ieuri.auth.application.command.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.command.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.command.RemoveProfileImageCommand
import org.around.ieuri.auth.application.exception.ImageTooLargeException
import org.around.ieuri.auth.application.exception.InvalidNicknameException
import org.around.ieuri.auth.application.exception.ObjectKeyNotFoundException
import org.around.ieuri.auth.application.exception.PhotoNotUploadedException
import org.around.ieuri.auth.application.exception.UnsupportedImageTypeException
import org.around.ieuri.auth.application.policy.ProfileImagePolicy
import org.around.ieuri.auth.application.port.`in`.command.MemberProfileUseCase
import org.around.ieuri.auth.application.port.out.client.ProfileImageStoragePort
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.application.query.ProfileImageUploadUrlView
import org.around.ieuri.auth.domain.vo.ImageFormat
import org.around.ieuri.auth.domain.vo.Nickname
import org.around.ieuri.auth.domain.vo.ProfileImageObjectKey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** 프로필 수정(SPEC-0011 R5·R6): 닉네임과 프로필 이미지(발급 → 클라이언트 직접 PUT → 확인). */
@Service
class MemberProfileService(
    private val profileRepository: MemberProfileRepositoryPort,
    private val storage: ProfileImageStoragePort,
    private val policy: ProfileImagePolicy,
    private val clock: Clock,
) : MemberProfileUseCase {

    /** 닉네임 변경. 형식만 보고 중복은 보지 않는다(SPEC-0032 R31). */
    @Transactional
    override fun changeNickname(command: ChangeNicknameCommand): String {
        val nickname = runCatching { Nickname(command.nickname) }
            .getOrElse { throw InvalidNicknameException(it.message ?: "닉네임 형식이 올바르지 않습니다.") }

        profileRepository.updateNickname(command.memberId, nickname.value)
        return nickname.value
    }

    /** 업로드 URL 발급. 형식·크기가 어긋나면 키를 발급하지 않는다. */
    override fun issueProfileImageUploadUrl(
        command: IssueProfileImageUploadUrlCommand,
    ): ProfileImageUploadUrlView {
        val contentType = command.contentType.trim().lowercase()
        if (contentType !in policy.allowedContentTypes) {
            throw UnsupportedImageTypeException("지원하지 않는 이미지 형식입니다.")
        }
        if (command.contentLength <= 0 || command.contentLength > policy.maxUploadBytes) {
            throw ImageTooLargeException("이미지 크기가 허용 범위를 벗어났습니다.")
        }

        val expiresAt = clock.instant().plus(policy.uploadUrlTtl)
        val key = ProfileImageObjectKey.issue(command.memberId, UUID.randomUUID())
        val uploadUrl = storage.presignPut(
            objectKey = key.value,
            contentType = contentType,
            contentLength = command.contentLength,
            expiresAt = expiresAt,
        )
        return ProfileImageUploadUrlView(uploadUrl = uploadUrl, objectKey = key.value, expiresAt = expiresAt)
    }

    /**
     * 업로드 확인 후 회원에 반영. 판정 순서는 소유권 → 존재 → 크기 → 매직바이트다.
     * - 소유권을 먼저 본다. 저장소를 먼저 물으면 타인 키의 존재가 응답 시간으로 샌다.
     * - 교체 시 이전 오브젝트 정리 실패는 반영을 막지 않는다.
     */
    @Transactional
    override fun confirmProfileImageUpload(command: ConfirmProfileImageUploadCommand): String {
        val key = ProfileImageObjectKey.parse(command.objectKey)
            ?: throw ObjectKeyNotFoundException("발급된 적 없는 오브젝트 키입니다.")
        // 확인 대상은 임시 키뿐이다 — 이미 확정된 키를 다시 확인하면 원본이 없어 실패한다.
        if (key.confirmed || !key.ownedBy(command.memberId)) {
            throw ObjectKeyNotFoundException("발급된 적 없는 오브젝트 키입니다.")
        }

        val size = storage.findSize(key.value)
            ?: throw PhotoNotUploadedException("업로드가 확인되지 않았습니다.")
        if (size <= 0 || size > policy.maxUploadBytes) {
            throw ImageTooLargeException("이미지 크기가 허용 범위를 벗어났습니다.")
        }

        val prefix = storage.readPrefix(key.value, ImageFormat.MAGIC_BYTES_LENGTH)
            ?: throw PhotoNotUploadedException("업로드가 확인되지 않았습니다.")
        if (ImageFormat.detect(prefix) == null) {
            throw UnsupportedImageTypeException("지원하지 않는 이미지 형식입니다.")
        }

        val previousKey = profileRepository.findProfileImageKey(command.memberId)
        val confirmed = key.toConfirmed()
        storage.move(sourceKey = key.value, targetKey = confirmed.value)
        profileRepository.updateProfileImage(command.memberId, confirmed.value)

        deleteQuietly(previousKey, confirmed.value)
        return confirmed.value
    }

    /** 이미 없어도 성공한다(멱등). 오브젝트 정리 실패도 성공을 막지 않는다. */
    @Transactional
    override fun removeProfileImage(command: RemoveProfileImageCommand) {
        val previousKey = profileRepository.findProfileImageKey(command.memberId)
        profileRepository.updateProfileImage(command.memberId, null)
        deleteQuietly(previousKey, null)
    }

    /** 이전 오브젝트 정리. 실패해도 삼킨다 — 남은 오브젝트는 스토리지 lifecycle rule이 걷어 간다. */
    private fun deleteQuietly(previousKey: String?, newKey: String?) {
        if (previousKey == null || previousKey == newKey) return
        runCatching { storage.delete(previousKey) }
    }
}
