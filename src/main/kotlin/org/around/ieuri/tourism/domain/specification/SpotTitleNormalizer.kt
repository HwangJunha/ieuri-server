package org.around.ieuri.tourism.domain.specification

/**
 * spot 제목의 정규화와 한국어 키 추출(SPEC-0019 R-2). 순수 함수이며 설정을 받지 않는다.
 *
 * TourAPI 비한국어 제목은 끝에 괄호로 한국어 명칭을 병기한다 — `Marine City (마린시티)`.
 * 그 괄호 안의 한국어가 KO 제목과 이어 주는 1차 신호다.
 *
 * **JA는 전각 괄호 `（）`를 쓴다.** 반각만 처리하면 커버리지가 53%로 무너진다(전각 반영 시 86%).
 */
object SpotTitleNormalizer {

    /**
     * 비교용 정규화 키. 대괄호 태그를 걷어내고 공백·구분자를 지운다.
     *
     * 정규화 후 빈 문자열이 될 수 있다 — 제목이 태그뿐인 경우다. 호출자가 빈 키를
     * 매칭에 쓰면 모든 행이 서로 일치하므로, [matchKey]가 빈 값을 null로 접는다.
     */
    fun normalize(title: String): String =
        title
            .let(::toHalfWidthParens)
            .let(::stripBracketTags)
            .replace(SEPARATORS, "")

    /**
     * **카탈로그가 저장하는** 정규화 키(`spot_catalog.name_key`). 빈 키는 null이다 —
     * 빈 문자열끼리는 전부 일치하므로 키로 쓰면 무관한 장소가 한 카탈로그로 뭉친다.
     *
     * 비한국어 제목에 그대로 쓰면 안 된다 — `MarineCity(마린시티)`는 카탈로그의 `마린시티`와
     * 결코 같지 않다. 비한국어 쪽이 대조에 쓸 값은 [lookupKey]가 만든다.
     */
    fun matchKey(title: String): String? = normalize(title).takeIf { it.isNotEmpty() }

    /**
     * **비한국어 제목이 KO 후보를 찾을 때 쓰는 키.** 괄호에서 뽑은 한국어를 [matchKey]와 같은
     * 규칙으로 정규화한 값이며, 그래야 카탈로그의 `name_key`와 대조된다.
     *
     * `Marine City (마린시티)` → `마린시티`. 제목 전체를 정규화한 `MarineCity(마린시티)`를
     * 그대로 쓰면 **어떤 KO 행과도 일치하지 않아 매칭이 전량 실패한다.**
     *
     * 한국어를 뽑지 못하면 null이다 — 대조할 키가 없다는 뜻이며 썸네일 판정으로 내려간다.
     */
    fun lookupKey(title: String): String? = extractKoreanKeys(title).firstOrNull()

    /**
     * 제목에서 한국어 키 후보를 뽑는다. 앞에 오는 것이 더 유력한 후보다.
     *
     * 괄호 추출은 **중첩을 허용한다** — `A (B (마린시티))`에서 안쪽까지 본다.
     * 대괄호 안 한글과 `/` 뒤 한글도 후보로 본다.
     *
     * 한국어를 하나도 뽑지 못하면 빈 목록이며, 그것이 `NO_KOREAN_KEY` 미해결 사유다.
     */
    fun extractKoreanKeys(title: String): List<String> {
        val normalizedParens = toHalfWidthParens(title)

        val candidates = buildList {
            addAll(bracketedSegments(normalizedParens, '(', ')'))
            addAll(bracketedSegments(normalizedParens, '[', ']'))
            // `/` 뒤의 꼬리. `Seoul Museum / 서울박물관` 형태를 잡는다.
            normalizedParens.substringAfterLast('/', "").takeIf { it.isNotBlank() }?.let(::add)
        }

        return candidates
            .filter(::containsHangul)
            .map { stripBracketTags(it).replace(SEPARATORS, "") }
            .filter { it.isNotEmpty() && containsHangul(it) }
            .distinct()
    }

    /** 한글 음절·자모가 하나라도 있는가. 한국어 키 후보의 자격 조건이다. */
    fun containsHangul(value: String): Boolean = value.any { it in HANGUL_SYLLABLES || it in HANGUL_JAMO }

    /**
     * 전각 괄호를 반각으로. **이 처리가 없으면 JA 커버리지가 53%로 무너진다.**
     *
     * 괄호만 바꾼다 — 전각 영숫자까지 접으면 원문 표기가 달라져 제목 값 자체가 훼손된다.
     */
    private fun toHalfWidthParens(value: String): String =
        value
            .replace('（', '(').replace('）', ')')
            .replace('［', '[').replace('］', ']')

    /**
     * 인증·면세 태그를 제거한다 — `[한국관광 품질인증]`, `[Tax Refund Shop]`, `[事後免税店]`.
     *
     * 대괄호 안에 한국어 키가 들어 있는 경우가 있어([extractKoreanKeys]가 그것을 후보로
     * 본다) **추출이 먼저이고 제거는 정규화 쪽에서만 한다.** 순서가 뒤집히면 키를 잃는다.
     */
    private fun stripBracketTags(value: String): String = value.replace(BRACKET_TAG, "")

    /**
     * 짝이 맞는 괄호 안의 조각들. 중첩을 허용하며 안쪽 조각도 따로 돌려준다.
     *
     * 여는 괄호 없이 닫는 괄호만 나오는 깨진 제목이 실재하므로 depth가 음수로 가지 않게 막는다.
     */
    private fun bracketedSegments(value: String, open: Char, close: Char): List<String> {
        val segments = mutableListOf<String>()
        val openIndices = ArrayDeque<Int>()

        value.forEachIndexed { index, char ->
            when (char) {
                open -> openIndices.addLast(index)
                close -> openIndices.removeLastOrNull()?.let { start ->
                    segments += value.substring(start + 1, index)
                }
            }
        }
        // 안쪽 괄호가 먼저 닫히므로 수집 순서가 안쪽→바깥쪽이다. 바깥쪽(더 큰 조각)을 앞에 둔다.
        return segments.asReversed()
    }

    /** 공백·중점·하이픈 등 표기 흔들림을 흡수한다. 언어마다 구분자 습관이 달라 비교 전에 지운다. */
    private val SEPARATORS = Regex("[\\s·・‧∙•・\\-–—_,]")

    /** 대괄호 태그 통째. 안의 내용은 보지 않는다 — 인증 문구가 언어마다 다르기 때문이다. */
    private val BRACKET_TAG = Regex("\\[[^\\[\\]]*]")

    private val HANGUL_SYLLABLES = '가'..'힣'
    private val HANGUL_JAMO = 'ㄱ'..'ㆎ'
}
