package org.around.ieuri.community.application.photo

import org.around.ieuri.community.application.photo.provided.UploadedPhotoUseCase
import org.around.ieuri.community.application.photo.required.ObjectStoragePort
import org.around.ieuri.community.domain.photo.PhotoNotUploadedException
import org.around.ieuri.community.domain.photo.PhotoObjectKey
import org.springframework.stereotype.Service

/** 클라이언트가 보낸 사진 키를 파싱하고 실제로 올라가 있는지 확인한다. 실패는 모두 PHOTO_NOT_UPLOADED다. */
@Service
class UploadedPhotoService(
    private val objectStoragePort: ObjectStoragePort,
) : UploadedPhotoUseCase {
    override fun parse(raw: List<String>): List<PhotoObjectKey> = raw.map { PhotoObjectKey.parse(it) ?: throw PhotoNotUploadedException() }

    override fun requireUploaded(keys: List<PhotoObjectKey>) {
        if (keys.any { !isUploaded(it) }) throw PhotoNotUploadedException()
    }

    override fun isUploaded(key: PhotoObjectKey): Boolean = objectStoragePort.findSize(key.value) != null

    override fun delete(objectKey: String) = objectStoragePort.delete(objectKey)
}
