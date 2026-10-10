package org.around.ieuri.community.application.photo.provided

import org.around.ieuri.community.domain.photo.PhotoObjectKey

/**
 * 올라간 사진의 확인과 삭제. 다른 애그리게이트는 사진 저장소를 직접 부르지 않고 이 유스케이스를 거친다.
 * - 오브젝트 스토리지 호출이라 트랜잭션 안에서는 첫 쿼리 전에 부른다.
 */
interface UploadedPhotoUseCase {
    /** 형식이 어긋난 키는 미업로드와 같은 코드(PHOTO_NOT_UPLOADED)로 다룬다 — 키 존재 여부를 노출하지 않는다. */
    fun parse(raw: List<String>): List<PhotoObjectKey>

    /** 하나라도 없으면 전체를 거부한다(PHOTO_NOT_UPLOADED) — 부분 생성은 없다. */
    fun requireUploaded(keys: List<PhotoObjectKey>)

    /** 오브젝트가 저장소에 있는가. */
    fun isUploaded(key: PhotoObjectKey): Boolean

    /** 오브젝트를 지운다. 이미 없어도 오류가 아니다(멱등). */
    fun delete(objectKey: String)
}
