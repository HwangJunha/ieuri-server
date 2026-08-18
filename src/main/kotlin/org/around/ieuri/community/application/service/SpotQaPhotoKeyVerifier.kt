package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Component

/**
 * 질문·답변·답글이 공유하는 사진 키 검증(SPEC-0026 R9).
 *
 * 셋의 검증이 완전히 같아 한 곳에 둔다 — `PostCommandService.verifiedPhotoKey`와 같은 판정이며,
 * 세 서비스에 복사하면 한 곳만 고쳐지는 날이 온다.
 *
 * **두 검사는 계층이 다르다:**
 *  - 키 소유권(이 키가 호출자에게 발급된 것인가) → 키 문자열만 보며 포트 호출이 없다.
 *  - 업로드 여부(그 키에 실제 객체가 있는가)     → ObjectStoragePort에 묻는다.
 *
 * 소유권을 먼저 본다. 세 실패를 한 코드로 뭉뚱그리는 이유는 구분해 알려 주면 남의 오브젝트
 * 키가 존재하는지 캐내는 수단이 되기 때문이다.
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
