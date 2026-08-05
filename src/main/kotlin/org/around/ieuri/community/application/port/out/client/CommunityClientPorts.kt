package org.around.ieuri.community.application.port.out.client

import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostTargetView
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
}

/**
 * 글이 붙은 spot을 조회한다. `tourism`이 구현한다 — 의존 방향을 이렇게 두면 tourism은
 * 커뮤니티를 계속 모른 채로 있을 수 있다.
 *
 * 글이 상품·경매 등 다른 대상에도 붙게 되면 그때 종류별 포트를 나눈다. 지금은 SPOT뿐이라
 * 고르는 계층을 두지 않는다 — 쓰지 않는 분기는 읽는 사람에게 비용이다.
 */
interface SpotLookupPort {
    /** 피드 카드용 요약. 존재하지 않으면 null이며 그것이 대상 유효성 판정이다. */
    fun findSummary(spotId: Long): PostTargetView?

    /**
     * 좌표 반경 안의 spot id. [limit]을 넘으면 **조용히 잘린다** — 넓은 반경에서 수만 건이
     * 나오면 이 결과를 받는 쪽의 조회 조건이 무너지기 때문이다.
     */
    fun findIdsWithin(latitude: Double, longitude: Double, radiusMeters: Int, limit: Int): List<Long>

    /** 여러 spot의 요약을 한 번에. 카테고리 칩 필터가 쓴다 — 행마다 물으면 조회가 20번 붙는다. */
    fun findSummaries(spotIds: List<Long>): Map<Long, PostTargetView>
}

/**
 * 회원 조회. `auth`가 구현한다 — SpotLookupPort와 같은 이유로 의존 방향을 뒤집어 둔다.
 */
interface MemberLookupPort {
    /** 글·댓글에 실을 최소 작성자 정보. 존재하지 않으면 null이며 그것이 존재 확인이다. */
    fun findAuthor(memberId: Long): PostAuthorView?
}

/**
 * 시각. 만료 시각 계산을 단위 테스트로 고정하기 위해 주입 가능하게 둔다 —
 * `Instant.now()`를 직접 부르면 "발급 시각 + 5분"을 검증할 수 없다.
 */
interface ClockPort {
    fun now(): Instant
}
