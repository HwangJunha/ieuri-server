package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.CatalogAssignmentResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.`in`.command.TranslationResult
import org.around.ieuri.tourism.application.port.out.client.SpotTitleTranslationPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
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
 * 판정 규칙 자체는 도메인이 소유한다([CatalogMatchRule]) — 이 서비스는 후보를 모아 규칙에
 * 넘기고 결과를 저장하는 순서만 담당한다.
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
                        candidateSpotIds = outcome.candidateSpotIds,
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
        // 찾으면 KO의 `마린시티`와 결코 일치하지 않아 매칭이 전량 실패한다.
        val match = CatalogMatchRule.match(
            title = spot.title,
            coordinates = SpotPoint(spot.mapX, spot.mapY),
            candidatesByKey = SpotTitleNormalizer.lookupKey(spot.title)
                ?.let { storePort.findKoreanCandidatesByTitleKey(it) }
                ?: emptyList(),
            candidatesByThumbnail = storePort.findKoreanCandidatesByThumbnail(spot.thumbnailImageUrl),
        )

        return when (match) {
            is CatalogMatch.Matched -> {
                storePort.assign(spot.spotId, match.catalogId)
                // 이 언어의 제목은 TourAPI가 발급한 공식 표기다 — 나중에 번역 대상이 되지 않게 지금 심는다.
                storePort.upsertTranslation(match.catalogId, spot.language, spot.title, TranslationSource.OFFICIAL)
                Outcome.Joined
            }
            is CatalogMatch.Unresolved -> createStandaloneCatalog(spot, match)
        }
    }

    /**
     * KO 행의 배정. **KO끼리도 접는다**(spec R-2) — 제목 정규화 키와 좌표가 **둘 다 완전히
     * 일치**할 때만 한 클러스터로 본다. 실측 51,992행이 51,907개 클러스터가 되며, 접히는
     * 85행이 이 경로다.
     *
     * 접지 않으면 같은 장소가 KO 안에서만 둘로 갈려 좋아요가 따로 쌓인다 — 이 스펙이
     * 없애려는 결함과 같은 종류다. 시드 적재도 `koSpotIds`의 여러 행이 같은 카탈로그에
     * 속한다고 전제하므로(§5.4) 접히지 않으면 일부 행이 번역을 받지 못한다.
     */
    private fun assignKorean(spot: UnassignedSpot): Outcome {
        val existing = spot.titleKey
            ?.let { storePort.findKoreanCandidatesByTitleKey(it) }
            ?.singleOrNull { it.mapX == spot.mapX && it.mapY == spot.mapY }

        if (existing != null) {
            storePort.assign(spot.spotId, existing.catalogId)
            return Outcome.Joined
        }

        val catalogId = storePort.save(SpotCatalog.fromKoreanSpot(spot.title))
        storePort.assign(spot.spotId, catalogId)
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
        // 이 그룹 전체의 제목이 필요하다(R-3). 자기 제목만 보면 ZH만 있는 장소에서 ZH가 채택된다.
        val siblingTitles = queryPort.findForeignTitlesByThumbnail(spot.thumbnailImageUrl)
            .ifEmpty { mapOf(spot.language to spot.title) }

        val derived = SpotCatalog.deriveFromForeignTitles(siblingTitles)

        if (derived == null) {
            // 어느 언어에서도 한국어를 못 뽑았다. 대표명이 없으면 카탈로그를 만들 수 없으므로
            // 원문 제목을 대표명 자리에 둔다 — 사람이 CSV를 보고 고칠 값이다.
            val catalogId = storePort.save(SpotCatalog.fromKoreanSpot(spot.title))
            storePort.assign(spot.spotId, catalogId)
            storePort.upsertTranslation(catalogId, spot.language, spot.title, TranslationSource.OFFICIAL)
            return Outcome.CreatedUnresolved(UnresolvedReason.NO_KOREAN_KEY, match.extractedKey, match.candidateSpotIds)
        }

        val catalogId = storePort.save(derived)
        storePort.assign(spot.spotId, catalogId)
        return Outcome.CreatedUnresolved(match.reason, match.extractedKey, match.candidateSpotIds)
    }

    /**
     * **번역이 비어 있는 (카탈로그, 언어) 쌍만** 대상이다(R-4).
     *
     * 제목 해시 컬럼도, 변경 감지도 두지 않는다 — 재번역 경로가 존재하지 않으므로 필요 없다(R-7).
     * 기존 `content_id`의 제목이 상류에서 바뀌어도 번역은 그대로 둔다. 의도된 동작이다.
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

            val results = translationPort.translate(catalog.representativeNameKo, wanted)
            results.forEach { (language, title) ->
                // MACHINE은 기존 값을 덮지 않는다 — 포트 계약이 그것을 보장한다.
                storePort.upsertTranslation(catalog.catalogId, language, title, TranslationSource.MACHINE)
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
            val candidateSpotIds: List<Long>,
        ) : Outcome
    }

    private companion object {
        /**
         * 한 번의 배정에서 처리할 최대 행 수. 증분 처리라 평상시에는 신규분뿐이지만, 최초 실행은
         * 115,217행 전량이 대상이므로 배치가 여러 번 나눠 돈다.
         */
        const val ASSIGNMENT_BATCH_SIZE = 5_000
    }
}
