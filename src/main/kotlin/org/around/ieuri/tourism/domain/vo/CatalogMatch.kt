package org.around.ieuri.tourism.domain.vo

/**
 * 카탈로그 매칭 결과(SPEC-0019 R-2).
 *
 * **미해결도 결과다** — 매칭 실패는 오류가 아니라 "이 행은 자기만의 카탈로그를 갖고
 * 사람 판정 대상이 된다"는 판정이다. 매칭은 여러 행을 접는 작업이지 자격 심사가 아니므로
 * 어느 쪽이든 spot은 카탈로그를 갖는다(R-1).
 */
sealed interface CatalogMatch {

    /** 기존 카탈로그에 소속된다. */
    data class Matched(val catalogId: Long) : CatalogMatch

    /**
     * 자동 판정 불가. 독립 카탈로그를 만들고 미해결로 기록한다.
     *
     * [candidateSpotIds]는 CSV에 후보별 좌표·카테고리를 싣기 위한 것이다. 사람이 판정하려면
     * "무엇과 헷갈렸는가"를 알아야 한다.
     */
    data class Unresolved(
        val reason: UnresolvedReason,
        val extractedKey: String?,
        val candidateSpotIds: List<Long>,
    ) : CatalogMatch
}

/** 미해결 사유. **판정 방법이 사유마다 다르므로** 구분한다(R-8). */
enum class UnresolvedReason {
    /** 제목 키는 맞는 KO 후보가 여럿인데 좌표가 완전일치하는 것이 없거나 둘 이상. */
    KEY_AMBIGUOUS,

    /** 제목에서 한국어를 추출할 수 없음 — `SM Entertainment`, `Phil hotel`. */
    NO_KOREAN_KEY,

    /** 썸네일 URL을 여러 KO가 공유. */
    URL_AMBIGUOUS,
}

/** 매칭 후보로 좁혀진 KO spot 한 건. 판정에 필요한 값만 담는다. */
data class KoreanSpotCandidate(
    val spotId: Long,
    val catalogId: Long,
    /** 저장된 원값. 판정이 등치 비교라 보정하지 않는다. */
    val mapX: Double,
    val mapY: Double,
)

/** 카탈로그 대표 한국어명의 출처(R-3·R-5). */
enum class CatalogNameSource {
    /** KO spot 행의 title. TourAPI가 발급한 정식 명칭이다. */
    OFFICIAL,

    /** KO 행이 없어 외국어 제목의 괄호에서 추출한 것. 정식 명칭이 아니다. */
    DERIVED,
}

/** 카탈로그 언어별 제목의 출처(R-5). 공식 표기가 우리 번역보다 우선한다는 판단의 유일한 근거다. */
enum class TranslationSource {
    /** TourAPI가 그 언어로 발급한 제목. */
    OFFICIAL,

    /** 이 스펙이 KO 원문에서 생성한 번역. */
    MACHINE,
}
