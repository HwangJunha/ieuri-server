package org.around.ieuri.auth.application.port.out.client

import java.time.Instant

/** 프로필 이미지 오브젝트 스토리지(SPEC-0011 R6). 서버는 바이트를 중계하지 않고 발급·확인만 한다. */
interface ProfileImageStoragePort {
    /** 단일 키 presigned PUT URL. [contentType]·[contentLength]가 서명에 포함돼 선언과 다르게 올리면 스토리지가 거부한다. */
    fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiresAt: Instant,
    ): String

    /** 오브젝트의 실제 크기. 없으면 null이며 그것이 미업로드 판정이다. */
    fun findSize(objectKey: String): Long?

    /** 매직바이트 검증용 앞부분 바이트. 없으면 null. 이미지를 파싱하지 않는다. */
    fun readPrefix(objectKey: String, length: Int): ByteArray?

    /** 검증을 통과한 오브젝트를 확정 위치로 옮긴다(복사 후 원본 삭제). */
    fun move(sourceKey: String, targetKey: String)

    /** 오브젝트를 지운다. 이미 없어도 오류가 아니다(멱등). */
    fun delete(objectKey: String)
}
