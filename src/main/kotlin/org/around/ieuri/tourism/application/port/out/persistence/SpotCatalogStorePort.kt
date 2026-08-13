package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.KoreanSpotCandidate
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason

/**
 * 카탈로그 쓰기 포트(SPEC-0019). 배정 배치와 번역 배치가 쓴다.
 *
 * 매칭 후보 조회가 여기 있는 이유는 **배정이 읽기와 쓰기를 한 트랜잭션에서 번갈아 하기
 * 때문이다** — 방금 만든 카탈로그가 다음 행의 후보가 되어야 한다. 조회 전용 CQRS 읽기 포트로
 * 빼면 그 순서를 표현할 수 없다.
 */
interface SpotCatalogStorePort {

    /**
     * 카탈로그를 저장하고 부여된 id를 돌려준다. 번역도 함께 쓴다.
     */
    fun save(catalog: SpotCatalog): Long

    /** spot 행을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다. */
    fun assign(spotId: Long, catalogId: Long)

    /**
     * 제목 정규화 키가 일치하는 KO 후보. 순위 1·2가 쓴다.
     *
     * **정규화 키로 조회한다** — 원문 제목으로 조회하면 전각 괄호·태그·공백 차이 때문에
     * 매칭률이 무너진다. 저장 시점에 정규화 키를 함께 적재해 두는 것이 이 조회의 전제다.
     */
    fun findKoreanCandidatesByTitleKey(titleKey: String): List<KoreanSpotCandidate>

    /** 썸네일 URL이 일치하는 KO 후보. 순위 3이 쓴다. */
    fun findKoreanCandidatesByThumbnail(thumbnailImageUrl: String): List<KoreanSpotCandidate>

    /**
     * 미해결로 기록한다. 같은 spot을 다시 기록해도 행이 늘지 않는다(UNIQUE upsert).
     *
     * 미해결이어도 spot은 독립 카탈로그를 갖는다 — 이 호출은 "사람이 봐야 한다"는 표시일 뿐
     * 배정을 대신하지 않는다.
     */
    fun recordUnresolved(
        spotId: Long,
        reason: UnresolvedReason,
        extractedKey: String?,
        candidateSpotIds: List<Long>,
    )

    /**
     * 번역 한 건을 쓴다. 같은 `(catalogId, language)`가 이미 있으면 [source]에 따라 갈린다:
     * `OFFICIAL`은 덮어쓰고, `MACHINE`은 기존 값을 남긴다(R-4·R-5).
     */
    fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, source: TranslationSource)
}
