package org.around.ieuri.tourism.domain.specification

import org.around.ieuri.tourism.domain.vo.CatalogMatch
import org.around.ieuri.tourism.domain.vo.CatalogCandidate
import org.around.ieuri.tourism.domain.vo.UnresolvedReason

/**
 * 비한국어 spot을 KO spot에 잇는 동일성 판정(SPEC-0019 R-2). 순수 함수다.
 *
 * **조정 가능한 임계값이 없다.** 좌표는 완전일치만 인정하므로 설정으로 뺄 계수가 존재하지
 * 않는다. 근사 비교(거리 계산, 허용 오차, 반올림)를 넣지 않는다 — 넣는 순간 스펙이 배제한
 * 오판정 여지가 되살아난다.
 *
 * 판정 순서가 곧 우선순위이며, 각 단계는 실패 시 다음으로 내려간다.
 */
object CatalogMatchRule {

    /**
     * 이 비한국어 spot이 어느 KO spot과 같은 장소인가.
     *
     * @param candidatesByKey 이름 정규화 키가 일치하는 카탈로그 후보. 순위 1·2가 쓴다.
     * @param candidatesByThumbnail 썸네일 URL이 일치하는 카탈로그 후보. 순위 3이 쓴다.
     *
     * 호출자가 두 후보 집합을 **미리 좁혀서** 넘긴다 — 어느 인덱스로 좁힐지는 조회의
     * 책임이고, 좁혀진 뒤 무엇을 채택할지가 이 규칙의 책임이다.
     */
    fun match(
        title: String,
        coordinates: SpotPoint,
        candidatesByKey: List<CatalogCandidate>,
        candidatesByThumbnail: List<CatalogCandidate>,
    ): CatalogMatch {
        val koreanKeys = SpotTitleNormalizer.extractKoreanKeys(title)

        // 순위 1·2 — 괄호 한글 키가 있을 때. 키가 있으면 썸네일로 내려가지 않는다.
        if (koreanKeys.isNotEmpty()) {
            return matchByKey(candidatesByKey, coordinates, koreanKeys.first())
        }

        // 순위 3 — 키가 없을 때만 썸네일 URL을 본다.
        //
        // **키가 있을 때 썸네일을 보지 않는 것이 R-2의 "1과 3이 충돌하면 1을 채택"이다.**
        // 충돌 201건에서 키 측 카테고리 일치율이 97%, URL 측이 26%였다 — 여행 코스가 개별
        // 명소의 대표 사진을 재사용하기 때문이며 매칭 로직이 아니라 원본 데이터의 성질이다.
        return when (candidatesByThumbnail.size) {
            1 -> CatalogMatch.Matched(candidatesByThumbnail.single().catalogId)
            0 -> CatalogMatch.Unresolved(UnresolvedReason.NO_KOREAN_KEY, extractedKey = null, candidateCatalogIds = emptyList())
            else -> CatalogMatch.Unresolved(
                UnresolvedReason.URL_AMBIGUOUS,
                extractedKey = null,
                candidateCatalogIds = candidatesByThumbnail.map { it.catalogId },
            )
        }
    }

    /**
     * 순위 1: 키 일치 후보가 유일하면 채택.
     * 순위 2: 후보가 여럿이면 **좌표가 완전히 일치하는** 후보가 유일할 때만 채택.
     *
     * 좌표가 단독 기준이 아닌 이유는 같은 좌표에 여러 spot이 존재하기 때문이다 —
     * 롯데프리미엄아울렛 동부산점 한 좌표에 207행이 매달려 있다. 그래서 좌표는
     * **제목 키가 이미 좁혀 놓은 후보들 사이에서만** 쓰인다.
     */
    private fun matchByKey(
        candidates: List<CatalogCandidate>,
        coordinates: SpotPoint,
        extractedKey: String,
    ): CatalogMatch {
        candidates.singleOrNull()?.let { return CatalogMatch.Matched(it.catalogId) }

        if (candidates.isEmpty()) {
            return CatalogMatch.Unresolved(UnresolvedReason.KEY_AMBIGUOUS, extractedKey, emptyList())
        }

        val exactLocationMatches = candidates.filter { it.matchesExactly(coordinates) }
        return exactLocationMatches.singleOrNull()
            ?.let { CatalogMatch.Matched(it.catalogId) }
            ?: CatalogMatch.Unresolved(
                UnresolvedReason.KEY_AMBIGUOUS,
                extractedKey,
                candidates.map { it.catalogId },
            )
    }

    /**
     * 좌표 완전일치. **저장된 값을 그대로 등치 비교한다** — 비교 전에 반올림하거나 자릿수를
     * 줄이지 않는다.
     *
     * 근사 허용은 미해결을 509건에서 177건으로 줄이지만, 좌표가 미세하게 다른 두 spot이
     * 실제로 다른 가게인 경우와 같은 가게의 좌표가 갱신된 경우를 거리로는 구분할 수 없다.
     */
    private fun CatalogCandidate.matchesExactly(point: SpotPoint): Boolean =
        mapX == point.mapX && mapY == point.mapY
}

/**
 * 매칭 판정에 쓰는 좌표. 도메인 [org.around.ieuri.tourism.domain.vo.Coordinates]와 달리
 * **저장된 원값 그대로**를 담는다 — 판정이 등치 비교라 어떤 보정도 끼어들면 안 된다.
 */
data class SpotPoint(val mapX: Double, val mapY: Double)
