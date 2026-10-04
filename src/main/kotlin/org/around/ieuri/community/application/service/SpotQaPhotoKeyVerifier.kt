package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.domain.shared.PhotoNotUploadedException
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Component

/**
 * 질문·답변·답글이 공유하는 사진 키 검증(SPEC-0026).
 * - 소유권·확정 여부(키 문자열)를 먼저, 업로드 여부(저장소)를 나중에 본다. 실패는 모두 PhotoNotUploadedException이다.
 */
@Component
class SpotQaPhotoKeyVerifier(
    private val objectStoragePort: ObjectStoragePort,
) {
    fun verify(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotUploadedException()
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotUploadedException()
        if (objectStoragePort.findSize(key.value) == null) throw PhotoNotUploadedException()
        return key
    }
}
