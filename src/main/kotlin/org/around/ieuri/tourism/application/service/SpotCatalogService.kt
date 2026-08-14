package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.CatalogAssignmentResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.`in`.command.TranslationResult
import org.around.ieuri.tourism.application.port.out.client.SpotTitleTranslationPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.domain.aggregate.CatalogTranslation
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.specification.CatalogMatchRule
import org.around.ieuri.tourism.domain.specification.SpotPoint
import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.CatalogMatch
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.domain.vo.UnresolvedReason
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정·번역 유스케이스 구현(SPEC-0019).
 *
 * 배정은 두 가지를 한다:
 *  1. 이 spot이 **어느 장소인가** 판정해 카탈로그에 붙인다(R-2)
 *  2. 카탈로그의 **표준값을 확정한다** — 좌표·썸네일·분류·축제기간. 전부 KO 우선이다
 *
 * 판정 규칙은 도메인이 소유한다([CatalogMatchRule]) — 이 서비스는 후보를 모아 규칙에 넘기고
 * 결과를 저장하는 순서만 담당한다.
 */
@Service
class SpotCatalogService(
    private val queryPort: SpotCatalogQueryPort,
    private val storePort: SpotCatalogStorePort,
    private val translationPort: SpotTitleTranslationPort,
) : SpotCatalogUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun assignCatalogs(): CatalogAssignmentResult {
        val targets = queryPort.findUnassigned(ASSIGNMENT_BATCH_SIZE)

        var assigned = 0
        var created = 0
        var unresolved = 0

        // KO가 먼저 온다(포트 계약) — KO가 카탈로그를 만들고 나머지 언어가 거기 붙는다.
        targets.forEach { spot ->
            when (val outcome = assignOne(spot)) {
                is Outcome.Joined -> assigned++
                is Outcome.Created -> {
                    assigned++
                    created++
                }
                is Outcome.CreatedUnresolved -> {
                    assigned++
                    created++
                    unresolved++
                    storePort.recordUnresolved(
                        spotId = spot.spotId,
                        reason = outcome.reason,
                        extractedKey = outcome.extractedKey,
                        candidateCatalogIds = outcome.candidateCatalogIds,
                    )
                }
            }
        }

        log.info(
            "catalog assignment completed: assigned={} created={} unresolved={}",
            assigned, created, unresolved,
        )
        return CatalogAssignmentResult(assigned = assigned, catalogsCreated = created, unresolved = unresolved)
    }

    /**
     * spot 한 행을 배정한다. **어떤 경로로 끝나든 카탈로그를 갖는다**(R-1) — 매칭은 여러 행을
     * 접는 작업이지 자격 심사가 아니다.
     */
    private fun assignOne(spot: UnassignedSpot): Outcome {
        if (spot.language.isKorean) return assignKorean(spot)

        // **조회 키는 제목 전체가 아니라 괄호에서 뽑은 한국어다.** `MarineCity(마린시티)`로
        // 찾으면 카탈로그의 `마린시티`와 결코 일치하지 않아 매칭이 전량 실패한다.
        val match = CatalogMatchRule.match(
            title = spot.title,
            coordinates = SpotPoint(spot.mapX, spot.mapY),
            candidatesByKey = SpotTitleNormalizer.lookupKey(spot.title)
                ?.let { storePort.findCatalogCandidatesByNameKey(it) }
                ?: emptyList(),
            candidatesByThumbnail = storePort.findCatalogCandidatesByThumbnail(spot.thumbnailImageUrl),
        )

        return when (match) {
            is CatalogMatch.Matched -> {
                storePort.assign(spot.spotId, match.catalogId)
                // 이 언어의 값은 TourAPI가 발급한 공식 표기다 — 나중에 번역 대상이 되지 않게 지금 심는다.
                storePort.upsertTranslation(
                    match.catalogId, spot.language, spot.title, spot.address, TranslationSource.OFFICIAL,
                )
                // 표준값을 다시 확정한다 — 이 행이 KO보다 나은 값을 가질 수 있다
                // (KO 좌표가 (0,0)인데 이 행은 유효한 경우).
                storePort.refreshStandardValues(match.catalogId)
                Outcome.Joined
            }
            is CatalogMatch.Unresolved -> createStandaloneCatalog(spot, match)
        }
    }

    /**
     * KO 행의 배정. **KO끼리도 접는다**(R-2) — 제목 정규화 키와 좌표가 **둘 다 완전히 일치**할
     * 때만 한 클러스터로 본다. 실측 51,992행이 51,907개 클러스터가 되며 접히는 85행이 이 경로다.
     *
     * 접지 않으면 같은 장소가 KO 안에서만 둘로 갈려 좋아요가 따로 쌓인다 — 이 스펙이 없애려는
     * 결함과 같은 종류다.
     */
    private fun assignKorean(spot: UnassignedSpot): Outcome {
        // **firstOrNull이다.** singleOrNull이면 같은 키·같은 좌표의 후보가 둘 이상일 때 null이
        // 되어 새 카탈로그가 생긴다 — 접으려던 중복이 오히려 갈라진다. 한 좌표에 207행이
        // 매달린 사례가 실재하므로(롯데프리미엄아울렛) 이 경우가 드물지 않다.
        // 후보 순서는 id 오름차순이라 재실행해도 같은 카탈로그를 고른다.
        val existing = SpotTitleNormalizer.matchKey(spot.title)
            ?.let { storePort.findCatalogCandidatesByNameKey(it) }
            ?.firstOrNull { it.mapX == spot.mapX && it.mapY == spot.mapY }

        if (existing != null) {
            storePort.assign(spot.spotId, existing.catalogId)
            storePort.refreshStandardValues(existing.catalogId)
            return Outcome.Joined
        }

        val catalogId = storePort.save(SpotCatalog.fromKoreanSpot(spot.title, spot.address))
        storePort.assign(spot.spotId, catalogId)
        storePort.refreshStandardValues(catalogId)
        return Outcome.Created
    }

    /**
     * 매칭되지 않은 비한국어 spot의 독립 카탈로그.
     *
     * 괄호에서 한국어를 뽑을 수 있으면 파생 카탈로그이고(R-3), 뽑을 수 없으면 미해결이다.
     * **미해결이어도 카탈로그는 만든다** — 감추면 `SM Entertainment` 같은 유효한 spot이 사라진다.
     */
    private fun createStandaloneCatalog(spot: UnassignedSpot, match: CatalogMatch.Unresolved): Outcome {
        // 같은 썸네일을 쓰는 다른 언어 행들을 함께 본다 — EN→JA→ZH 순으로 대표명을 고르려면
        // 이 그룹 전체의 값이 필요하다(R-3). 자기 값만 보면 ZH만 있는 장소에서 ZH가 채택된다.
        val siblings = queryPort.findForeignCardsByThumbnail(spot.thumbnailImageUrl)
            .ifEmpty {
                mapOf(spot.language to CatalogTranslation(spot.title, spot.address, TranslationSource.OFFICIAL))
            }

        val derived = SpotCatalog.deriveFromForeign(siblings)

        if (derived == null) {
            // 어느 언어에서도 한국어를 못 뽑았다. 대표명이 없으면 카탈로그를 만들 수 없으므로
            // 원문 제목을 대표명 자리에 둔다 — 사람이 CSV를 보고 고칠 값이다.
            val catalogId = storePort.save(SpotCatalog.fromKoreanSpot(spot.title, spot.address))
            storePort.assign(spot.spotId, catalogId)
            storePort.upsertTranslation(
                catalogId, spot.language, spot.title, spot.address, TranslationSource.OFFICIAL,
            )
            storePort.refreshStandardValues(catalogId)
            return Outcome.CreatedUnresolved(
                UnresolvedReason.NO_KOREAN_KEY, match.extractedKey, match.candidateCatalogIds,
            )
        }

        val catalogId = storePort.save(derived)
        storePort.assign(spot.spotId, catalogId)
        storePort.refreshStandardValues(catalogId)
        return Outcome.CreatedUnresolved(match.reason, match.extractedKey, match.candidateCatalogIds)
    }

    /**
     * **번역이 비어 있는 (카탈로그, 언어) 쌍만** 대상이다(R-4).
     *
     * 제목 해시 컬럼도, 변경 감지도 두지 않는다 — 재번역 경로가 존재하지 않으므로 필요 없다(R-7).
     * 기존 `content_id`의 값이 상류에서 바뀌어도 번역은 그대로 둔다. 의도된 동작이다.
     */
    @Transactional
    override fun translateMissing(languages: Set<DomainLocale>, limit: Int): TranslationResult {
        val targets = queryPort.findCatalogsMissingTranslation(languages, limit)

        var translated = 0
        var skipped = 0

        targets.forEach { catalog ->
            val wanted = catalog.missingLanguages intersect languages
            if (wanted.isEmpty()) {
                skipped++
                return@forEach
            }

            val results = translationPort.translate(
                catalog.representativeNameKo, catalog.representativeAddressKo, wanted,
            )
            results.forEach { (language, card) ->
                // MACHINE은 기존 값을 덮지 않는다 — 포트 계약이 그것을 보장한다.
                storePort.upsertTranslation(
                    catalog.catalogId, language, card.title, card.address, TranslationSource.MACHINE,
                )
                translated++
            }
            // 번역기가 돌려주지 않은 언어는 다음 실행이 다시 대상으로 삼는다.
            skipped += wanted.size - results.size
        }

        log.info("catalog translation completed: translated={} skipped={}", translated, skipped)
        return TranslationResult(translated = translated, skipped = skipped)
    }

    /** 배정 한 건의 결말. 카운터를 세는 것 외의 의미는 없다. */
    private sealed interface Outcome {
        /** 기존 카탈로그에 합류. */
        data object Joined : Outcome

        /** 새 카탈로그 생성(KO 행 또는 파생). */
        data object Created : Outcome

        /** 새 카탈로그 생성 + 미해결 기록. */
        data class CreatedUnresolved(
            val reason: UnresolvedReason,
            val extractedKey: String?,
            val candidateCatalogIds: List<Long>,
        ) : Outcome
    }

    private companion object {
        /**
         * 한 번의 배정(= 한 트랜잭션)에서 처리할 최대 행 수.
         *
         * **작게 잡는다.** 한 행마다 후보 조회·배정·번역 upsert·표준값 확정으로 6~8개 문장이
         * 나가므로, 5,000행이면 한 트랜잭션이 4만 문장을 붙들고 커넥션 하나를 오래 점유한다.
         * 그리고 마지막 한 건이 실패하면 앞의 전부가 롤백되어 처음부터 다시 한다.
         *
         * Spring Batch의 `CONTINUABLE`이 회차를 이어 주므로 작게 끊어도 전량 처리된다 —
         * 최초 실행 119,960행은 여러 회차로 나뉘고, 중간에 멈춰도 거기서 재개한다.
         */
        const val ASSIGNMENT_BATCH_SIZE = 500
    }
}
