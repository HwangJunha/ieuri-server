package org.around.ieuri.community.application.port.out.client

import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.vo.ContentLanguage
import java.time.Instant

/**
 * 오브젝트 스토리지. **서버는 이미지 바이트를 중계하지 않는다** — 발급과 확인만 하고
 * 업로드는 클라이언트가 스토리지로 직접 PUT한다.
 */
interface ObjectStoragePort {
    /**
     * 단일 키에만 유효한 presigned PUT URL을 발급한다.
     *
     * [contentType]·[contentLength]를 **서명 항목에 포함한다** — 선언한 것과 다른 형식이나
     * 크기로 올리면 스토리지가 거부하므로, 발급 시점의 선언이 업로드 시점에 강제된다.
     */
    fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiresAt: Instant,
    ): String

    /** 오브젝트의 실제 크기. 없으면 null. */
    fun findSize(objectKey: String): Long?

    /**
     * 오브젝트 앞부분 바이트. 매직바이트 검증용이며 [length]는 몇 바이트를 읽을지다.
     * 없으면 null. 이미지를 **파싱하지 않는다** — 파서 취약점을 피하려는 선택이다.
     */
    fun readPrefix(objectKey: String, length: Int): ByteArray?

    /** 검증을 통과한 오브젝트를 확정 위치로 옮긴다(복사 후 원본 삭제). */
    fun move(sourceKey: String, targetKey: String)

    /** 오브젝트를 지운다. 이미 없어도 오류가 아니다(멱등). */
    fun delete(objectKey: String)
}

/**
 * 글이 붙은 spot을 조회한다. `tourism`이 구현한다 — 의존 방향을 이렇게 두면 tourism은
 * 커뮤니티를 계속 모른 채로 있을 수 있다.
 *
 * 글이 상품·경매 등 다른 대상에도 붙게 되면 그때 종류별 포트를 나눈다. 지금은 SPOT뿐이라
 * 고르는 계층을 두지 않는다 — 쓰지 않는 분기는 읽는 사람에게 비용이다.
 */
interface SpotLookupPort {
    /**
     * 피드 카드용 요약. **[language]에서 해소되지 않으면 null이며, 그것이 대상 유효성 판정이다**
     * (SPEC-0029 R-25). 활성 언어에 없는 장소는 존재하지 않는 것과 같게 다뤄진다 — 그래야
     * 영어 사용자가 한국어에만 있는 spot을 첨부하지 못한다.
     *
     * 인자는 **카탈로그 id다**(SPEC-0019 R-12) — 언어별 `spot.id`를 받으면 같은 장소인데도
     * 요청 언어에 따라 다른 대상으로 판정된다.
     */
    fun findSummary(catalogId: Long, language: ContentLanguage): PostSpotView?

    /**
     * 여러 장소의 요약을 한 번에. 카테고리 칩 필터가 쓴다 — 행마다 물으면 조회가 20번 붙는다.
     * [language]에서 해소되지 않는 id는 결과에 키가 없다.
     */
    fun findSummaries(catalogIds: List<Long>, language: ContentLanguage): Map<Long, PostSpotView>
}

/**
 * 회원 조회. `auth`가 구현한다 — SpotLookupPort와 같은 이유로 의존 방향을 뒤집어 둔다.
 */
interface MemberLookupPort {
    /** 글·댓글에 실을 최소 작성자 정보. 존재하지 않으면 null이며 그것이 존재 확인이다. */
    fun findAuthor(memberId: Long): PostAuthorView?

    /**
     * 여러 작성자를 **한 번에**. 피드 카드마다 물으면 20장에 조회가 20번 붙는다(N+1).
     * 탈퇴 등으로 없는 회원은 결과에 키가 없다.
     */
    fun findAuthors(memberIds: List<Long>): Map<Long, PostAuthorView>
}

/**
 * 시각. 만료 시각 계산을 단위 테스트로 고정하기 위해 주입 가능하게 둔다 —
 * `Instant.now()`를 직접 부르면 "발급 시각 + 5분"을 검증할 수 없다.
 */
interface ClockPort {
    fun now(): Instant
}
