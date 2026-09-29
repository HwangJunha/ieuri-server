package org.around.ieuri.tourism.domain.specification

/**
 * spot 제목의 매칭 키 추출과 표시용 정리(SPEC-0019 R-2).
 * - JA·ZH는 전각 괄호 `（）`를 쓴다 — 반각으로 접은 뒤 처리한다.
 */
object SpotTitleNormalizer {

    /**
     * 매칭 키 — 한글만 남긴다. `한화리조트 / 용인`과 `한화리조트 용인`이 같은 키가 된다.
     * - 한글이 없으면 null이다.
     */
    fun matchKey(title: String): String? = title.filter { it in HANGUL_SYLLABLES || it in HANGUL_JAMO }.takeIf { it.isNotEmpty() }

    /**
     * 비한국어 제목이 KO를 찾을 때 쓰는 키. [matchKey]와 같은 규칙이다.
     * - 인증·태그 대괄호(`[한국관광 품질인증]`)는 먼저 걷어낸다.
     */
    fun lookupKey(title: String): String? = matchKey(stripBracketTags(toHalfWidthParens(title)))

    /**
     * 표시용 제목 — 한글을 지운다. `Marine City (마린시티)` → `Marine City`.
     * - 한글이 든 괄호 구간은 통째로, 빈 괄호와 꼬리 구분자도 함께 정리한다.
     * - 한글만으로 이루어진 제목은 null이다 — 호출자가 그 spot을 미배정으로 남긴다.
     */
    fun displayTitle(title: String): String? {
        // 한글이 든 괄호 구간을 통째로 지운 뒤, 괄호 밖에 남은 한글을 지운다.
        val working = stripEnclosuresWithHangul(toHalfWidthParens(title))
            .filterNot { it in HANGUL_SYLLABLES || it in HANGUL_JAMO }

        return working
            .replace(MULTI_SPACE, " ")
            .trim()
            .trim('-', '/', ',', '·', '~')
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * 한글이 든 최상위 괄호/대괄호 구간을 통째로 지운다. 중첩은 바깥 구간 단위로 지운다.
     * - 한글이 없는 구간과 짝이 맞지 않는 구간은 남긴다.
     */
    private fun stripEnclosuresWithHangul(value: String): String {
        val result = StringBuilder()
        var depth = 0
        var segmentStart = -1
        var opener = ' '

        value.forEachIndexed { index, char ->
            when {
                (char == '(' || char == '[') && depth == 0 -> {
                    segmentStart = index
                    opener = char
                    depth = 1
                }

                char == opener && depth > 0 -> depth++

                depth > 0 && char == closerOf(opener) -> {
                    depth--
                    if (depth == 0) {
                        val segment = value.substring(segmentStart, index + 1)
                        if (!containsHangul(segment)) result.append(segment)
                        segmentStart = -1
                    }
                }

                depth == 0 -> result.append(char)
            }
        }
        // 닫히지 않은 구간은 원문 그대로 되돌린다.
        if (segmentStart >= 0) result.append(value.substring(segmentStart))
        return result.toString()
    }

    private fun closerOf(opener: Char): Char = if (opener == '[') ']' else ')'

    /** 한글 음절·자모가 하나라도 있는가. */
    fun containsHangul(value: String): Boolean = value.any { it in HANGUL_SYLLABLES || it in HANGUL_JAMO }

    /** 전각 괄호를 반각으로. 괄호만 바꾸고 전각 영숫자는 건드리지 않는다. */
    private fun toHalfWidthParens(value: String): String = value
        .replace('（', '(').replace('）', ')')
        .replace('［', '[').replace('］', ']')

    /** 인증·면세 태그를 제거한다 — `[한국관광 품질인증]`, `[유네스코 세계문화유산]`, `[백년가게]`. */
    private fun stripBracketTags(value: String): String = value.replace(BRACKET_TAG, "")

    /** 대괄호 태그 통째. 안의 내용은 보지 않는다. */
    private val BRACKET_TAG = Regex("\\[[^\\[\\]]*]")

    private val MULTI_SPACE = Regex("\\s{2,}")

    private val HANGUL_SYLLABLES = '가'..'힣'
    private val HANGUL_JAMO = 'ㄱ'..'ㆎ'
}
