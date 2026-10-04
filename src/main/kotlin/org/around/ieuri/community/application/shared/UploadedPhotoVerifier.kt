package org.around.ieuri.community.application.shared

import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.domain.shared.PhotoNotUploadedException
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Component

/**
 * 클라이언트가 보낸 사진 키를 파싱하고 실제로 올라가 있는지 확인한다. 실패는 모두 PHOTO_NOT_UPLOADED다.
 * - 오브젝트 스토리지 호출이라 트랜잭션 안에서는 첫 쿼리 전에 부른다.
 */
@Component
class UploadedPhotoVerifier(
    private val objectStoragePort: ObjectStoragePort,
) {
    /** 형식이 어긋난 키는 미업로드와 같은 코드로 다룬다(키 존재 여부를 노출하지 않는다). */
    fun parse(raw: List<String>): List<PhotoObjectKey> = raw.map { PhotoObjectKey.parse(it) ?: throw PhotoNotUploadedException() }

    /** 하나라도 없으면 전체를 거부한다 — 부분 생성은 없다. */
    fun requireUploaded(keys: List<PhotoObjectKey>) {
        if (keys.any { objectStoragePort.findSize(it.value) == null }) throw PhotoNotUploadedException()
    }
}
