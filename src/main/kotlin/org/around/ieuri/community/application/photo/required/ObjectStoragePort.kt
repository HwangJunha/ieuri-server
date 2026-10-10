package org.around.ieuri.community.application.photo.required

import java.time.Instant

/** 오브젝트 스토리지. 서버는 발급과 확인만 하고, 업로드는 클라이언트가 직접 PUT한다. */
interface ObjectStoragePort {
    /**
     * 단일 키에만 유효한 presigned PUT URL을 발급한다.
     * - [contentType]·[contentLength]가 서명에 포함돼, 다르게 올리면 스토리지가 거부한다.
     */
    fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiresAt: Instant,
    ): String

    /** 오브젝트의 실제 크기. 없으면 null. */
    fun findSize(objectKey: String): Long?

    /** 오브젝트 앞부분 [length] 바이트(매직바이트 검증용). 없으면 null. 이미지를 파싱하지 않는다. */
    fun readPrefix(objectKey: String, length: Int): ByteArray?

    /** 검증을 통과한 오브젝트를 확정 위치로 옮긴다(복사 후 원본 삭제). */
    fun move(sourceKey: String, targetKey: String)

    /** 오브젝트를 지운다. 이미 없어도 오류가 아니다(멱등). */
    fun delete(objectKey: String)
}
