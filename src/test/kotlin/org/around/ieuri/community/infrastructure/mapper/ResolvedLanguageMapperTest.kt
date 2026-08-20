package org.around.ieuri.community.infrastructure.mapper

import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toContentLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * tourism 언어 코드 → [ContentLanguage] 변환(SPEC-0029 R-15, POL-033).
 *
 * 값이 하나뿐인 변환이라 "필드 대조"가 곧 **코드마다 다른 결과가 나오는지**다 — 넷을 한꺼번에
 * 넣어 서로 뒤바뀌지 않았음을 본다.
 */
class ResolvedLanguageMapperTest {

    @Test
    fun `네 코드가 각각 제 언어로 간다`() {
        // 한 번에 넣어야 매핑이 서로 뒤바뀐 경우를 잡는다 — 하나씩 넣으면 통과할 수 있다.
        val converted = setOf("EN", "KO", "JA", "ZH").toContentLanguages()

        assertEquals(
            setOf(ContentLanguage.EN, ContentLanguage.KO, ContentLanguage.JA, ContentLanguage.ZH),
            converted,
        )
    }

    @Test
    fun `코드 하나는 그 언어 하나로만 간다`() {
        assertEquals(setOf(ContentLanguage.KO), setOf("KO").toContentLanguages())
        assertEquals(setOf(ContentLanguage.JA), setOf("JA").toContentLanguages())
        assertEquals(setOf(ContentLanguage.ZH), setOf("ZH").toContentLanguages())
        assertEquals(setOf(ContentLanguage.EN), setOf("EN").toContentLanguages())
    }

    /**
     * **모르는 코드는 EN으로 폴백하지 않고 버린다.** 폴백하면 tourism에 언어가 하나 늘었을 때
     * 그 언어의 카탈로그가 전부 "영어에서 해소된다"로 기록되어, 영어 사용자에게 읽을 수 없는
     * 글이 뜬다.
     */
    @Test
    fun `모르는 코드는 버린다`() {
        assertEquals(setOf(ContentLanguage.KO), setOf("KO", "DE", "FR").toContentLanguages())
    }

    /** 소문자·공백은 정규화하지 않는다 — tourism은 enum 이름을 그대로 싣는다. */
    @Test
    fun `정확히 일치하지 않는 표기는 버린다`() {
        assertTrue(setOf("ko", " KO", "KO ").toContentLanguages().isEmpty())
    }

    /** 빈 입력은 빈 결과다 — 어느 언어에서도 해소되지 않는 카탈로그가 이 경우다. */
    @Test
    fun `빈 집합은 빈 집합으로 간다`() {
        assertTrue(emptySet<String>().toContentLanguages().isEmpty())
    }
}
