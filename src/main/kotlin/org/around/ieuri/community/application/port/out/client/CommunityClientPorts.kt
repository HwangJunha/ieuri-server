package org.around.ieuri.community.application.port.out.client

import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.vo.ContentLanguage
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

/** 글이 붙은 spot 조회(tourism). */
interface SpotLookupPort {
    /**
     * 피드 카드용 요약. 인자는 카탈로그 id다(SPEC-0019).
     * - [language]에서 해소되지 않으면 null이며, 그것이 대상 유효성 판정이다(SPEC-0029).
     */
    fun findSummary(catalogId: Long, language: ContentLanguage): PostSpotView?

    /** 여러 장소의 요약을 한 번에. [language]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun findSummaries(catalogIds: List<Long>, language: ContentLanguage): Map<Long, PostSpotView>
}

/** 회원 조회(auth). */
interface MemberLookupPort {
    /** 글·댓글에 실을 최소 작성자 정보. 존재하지 않으면 null이며 그것이 존재 확인이다. */
    fun findAuthor(memberId: Long): PostAuthorView?

    /** 여러 작성자를 한 번에. 탈퇴 등으로 없는 회원은 결과에 키가 없다. */
    fun findAuthors(memberIds: List<Long>): Map<Long, PostAuthorView>
}

/** 현재 시각. 테스트에서 시각을 고정하려고 주입한다. */
interface ClockPort {
    fun now(): Instant
}
