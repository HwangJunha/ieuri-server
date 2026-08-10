package org.around.ieuri.tourism.infrastructure

import org.around.ieuri.tourism.infrastructure.adapter.out.query.SpotSearchJdbcAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 검색어를 pg_search 질의 문자열로 감싸는 규칙(SPEC-0013 R1·R9).
 *
 * **`@@@`의 오른쪽은 값이 아니라 질의 언어다.** 바인딩 파라미터로 넘겨도 내용이 파서에
 * 그대로 들어가므로, 괄호·콜론 같은 문자가 든 장소명을 검색하면 질의가 통째로 깨졌다:
 *
 *   "(주)뮬라프렌즈 가로수길점" → could not parse query string 'title:((주)뮬라프렌즈 …)'
 *
 * 큰따옴표로 감싸면 그 안이 전부 리터럴이 되어 어떤 입력에도 파서가 깨지지 않는다.
 * 실제 DB에서 확인했다 — 감싼 뒤에도 정상 질의 결과는 같고(롯데 12,227건 · 경복궁 42건),
 * 문제의 질의는 3건을 정상 반환한다.
 */
class SpotSearchQueryEscapingTest {

    @Test
    fun `평범한 질의는 큰따옴표만 둘러진다`() {
        assertEquals("\"롯데\"", SpotSearchJdbcAdapter.toPhrase("롯데"))
        assertEquals("\"롯데월드\"", SpotSearchJdbcAdapter.toPhrase("롯데월드"))
    }

    @Test
    fun `공백이 있어도 하나의 구문으로 감싼다`() {
        assertEquals("\"경복궁 근정전\"", SpotSearchJdbcAdapter.toPhrase("경복궁 근정전"))
    }

    @Test
    fun `괄호가 든 상호명이 그대로 담긴다`() {
        // 이 입력이 실제로 서버 오류를 냈다.
        assertEquals(
            "\"(주)뮬라프렌즈 가로수길점\"",
            SpotSearchJdbcAdapter.toPhrase("(주)뮬라프렌즈 가로수길점"),
        )
    }

    @Test
    fun `pg_search 특수문자가 이스케이프 없이 리터럴이 된다`() {
        // 구문 안에서는 전부 리터럴이라 문자별로 처리할 필요가 없다 — 목록을 우리가
        // 관리하지 않는 것이 이 방식을 택한 이유다.
        val specials = "a:b (c) [d] {e} ^f ~g !h +i *j ?k"

        assertEquals("\"$specials\"", SpotSearchJdbcAdapter.toPhrase(specials))
    }

    @Test
    fun `입력에 든 큰따옴표는 제거한다`() {
        // 감싸는 따옴표와 충돌해 파서를 깨뜨린다. 장소명에서 따옴표는 검색어로서 의미가 없다.
        val quoted = SpotSearchJdbcAdapter.toPhrase("25\"TV 매장")

        assertEquals("\"25 TV 매장\"", quoted)
        // 바깥 두 개를 뺀 내부에는 따옴표가 남지 않아야 한다.
        assertFalse(quoted.substring(1, quoted.length - 1).contains('"'))
    }

    @Test
    fun `빈 문자열도 깨지지 않는다`() {
        // 서비스가 공백 질의를 앞서 거르지만(EMPTY_QUERY), 어댑터 단독으로도 안전해야 한다.
        assertEquals("\"\"", SpotSearchJdbcAdapter.toPhrase(""))
    }
}
