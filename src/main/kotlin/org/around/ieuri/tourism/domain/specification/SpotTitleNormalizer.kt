package org.around.ieuri.tourism.domain.specification

/**
 * spot 제목의 매칭 키 추출과 표시용 정리(SPEC-0019 R-2). 순수 함수이며 설정을 받지 않는다.
 *
 * TourAPI 비한국어 제목은 괄호로 한국어 명칭을 병기한다 — `Marine City (마린시티)`.
 * 그 괄호 안의 한국어가 KO 제목과 이어 주는 유일한 신호다.
 *
 * **JA·ZH는 전각 괄호 `（）`를 쓴다.** 반각만 처리하면 커버리지가 53%로 무너진다.
 */
object SpotTitleNormalizer {

    /**
     * **매칭 키 — 한글만 남긴다.**
     *
     * 공백·기호·영문·숫자·한자·가나를 전부 버리고 한글 음절만 이어 붙인다. 그래서
     * `한화리조트 / 용인`과 `한화리조트 용인`, `제주알(R)호텔`과 `제주알호텔`이 같은 키가 된다.
     *
     * 규칙이 이것 하나뿐인 것이 요점이다. 구분자 목록을 관리하거나 괄호 안팎을 따지면
     * 표기 변형이 나올 때마다 예외가 늘어난다 — 실제로 그렇게 새는 케이스를 계속 만났다.
     *
     * 한글이 없으면 null이다. 빈 키는 서로 전부 일치하므로 매칭에 쓸 수 없다.
     */
    fun matchKey(title: String): String? =
        title.filter { it in HANGUL_SYLLABLES || it in HANGUL_JAMO }.takeIf { it.isNotEmpty() }

    /**
     * **비한국어 제목이 KO를 찾을 때 쓰는 키.** [matchKey]와 같은 규칙이며, 제목 어디에 있든
     * 한글만 뽑으므로 괄호·슬래시·중첩을 따로 다루지 않는다.
     *
     * **인증·태그 대괄호는 먼저 걷어낸다** — `생각속의집[한국관광 품질인증]`을 그대로 두면
     * 태그의 한글이 키에 섞여 무관한 장소가 한 카탈로그로 뭉친다(실측 179건).
     */
    fun lookupKey(title: String): String? = matchKey(stripBracketTags(toHalfWidthParens(title)))

    /**
     * **표시용 제목 — 한글을 지운다.** `Marine City (마린시티)` → `Marine City`.
     *
     * 병기된 한국어는 매칭 단서일 뿐 표시할 값이 아니다. 떼지 않으면 일본어 사용자에게
     * 한글이 그대로 노출된다.
     *
     * **규칙이 "한글 제거" 하나뿐인 것이 요점이다.** 괄호·슬래시·대괄호를 각각 다루면
     * TourAPI 표기 변형이 나올 때마다 예외가 늘어난다 — 실제로 그렇게 새는 케이스를
     * 계속 만났다(실측 778건이 괄호도 슬래시도 아닌 형태였다).
     *
     * 한글을 지우고 나면 남은 기호가 흉해지므로(`Marine City ()`) 빈 괄호와 꼬리 구분자를
     * 함께 정리한다.
     *
     * **한글만으로 이루어진 제목은 null이다** — TourAPI가 그 언어 제목 자리에 한국어를
     * 넣은 것이며, 저장하면 영어 사용자가 `초려공방`을 보게 된다. 게다가 행이 있으면
     * 번역 대상에서도 빠져 영영 고쳐지지 않는다. 호출자가 그 spot을 미배정으로 남긴다.
     */
    fun displayTitle(title: String): String? {
        // 1단계: **한글이 든 괄호·대괄호 구간은 통째로 지운다.** 한글만 골라내면
        // `(서울한옥스테이(jnpstay))`에서 `((jnpstay))` 같은 찌꺼기가 남는다.
        // 2단계: 괄호 밖에 남은 한글을 지운다 — `Hue Hotel 휴호텔`처럼 괄호를 안 쓰는 형태다.
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
     * 한글이 든 **최상위** 괄호/대괄호 구간을 통째로 지운다. 중첩은 바깥이 이기므로
     * `(호텔 더 에이치(Hotel The H))`가 통으로 사라진다.
     *
     * 한글이 없는 구간은 원문의 일부이므로 남긴다 — `Gyeongbokgung (Palace)`.
     * 짝이 맞지 않는 구간도 남긴다 — 깨진 제목이 실재하고, 임의로 자르면 더 훼손된다.
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

    /**
     * 전각 괄호를 반각으로. **이 처리가 없으면 JA 커버리지가 53%로 무너진다.**
     *
     * 괄호만 바꾼다 — 전각 영숫자까지 접으면 원문 표기가 달라져 제목 값 자체가 훼손된다.
     */
    private fun toHalfWidthParens(value: String): String =
        value
            .replace('（', '(').replace('）', ')')
            .replace('［', '[').replace('］', ']')

    /** 인증·면세 태그를 제거한다 — `[한국관광 품질인증]`, `[유네스코 세계문화유산]`, `[백년가게]`. */
    private fun stripBracketTags(value: String): String = value.replace(BRACKET_TAG, "")

    /** 대괄호 태그 통째. 안의 내용은 보지 않는다 — 인증 문구가 언어마다 다르기 때문이다. */
    private val BRACKET_TAG = Regex("\\[[^\\[\\]]*]")

    private val MULTI_SPACE = Regex("\\s{2,}")

    private val HANGUL_SYLLABLES = '가'..'힣'
    private val HANGUL_JAMO = 'ㄱ'..'ㆎ'
}
