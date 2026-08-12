package org.around.ieuri.community.infrastructure

import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsQuery
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.CommunityQueryResolver
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.FeedQueryResolver
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 커넥션 반환 타입의 **이름**을 고정한다.
 *
 * Spring for GraphQL의 `ConnectionFieldTypeVisitor`는 스키마가 Relay 커넥션으로 선언한 필드의
 * 반환값을 감싼다. 등록된 `ConnectionAdapter`(Spring Data의 Window/Slice 전용)가 지원하지 않는
 * 타입이면 `getClass().getName().endsWith("Connection")`으로 통과 여부를 판정하고, 실패하면
 * 런타임에 `No ConnectionAdapter for: ...`로 조회가 통째로 깨진다.
 *
 * **컴파일로는 잡히지 않는다.** 실제로 `PostConnectionResponse`라는 이름으로 배포됐다가
 * 첫 조회에서 500이 났고, 그래서 이 테스트를 둔다. 이름 규칙이 계약의 일부라는 뜻이다.
 *
 * DGS·Spring 컨텍스트를 띄우지 않는다 — 리플렉션으로 반환 타입 이름만 본다.
 */
class ConnectionNamingTest {

    /** 스키마에서 커넥션을 반환하는 필드 이름. 늘어나면 여기에 추가한다. */
    private val connectionReturningFields = setOf(
        "posts",
        "comments",
        "blockedUsers",
    )

    /** 피드·DM 커넥션(SPEC-0017). 리졸버가 달라 목록을 나눠 둔다. */
    private val feedConnectionReturningFields = setOf(
        "feed",
        "conversations",
        "conversationMessages",
    )

    @Test
    fun `커넥션을 반환하는 리졸버의 반환 타입은 이름이 Connection으로 끝난다`() {
        assertConnectionNaming(CommunityQueryResolver::class.java, connectionReturningFields)
    }

    @Test
    fun `피드와 대화 커넥션의 반환 타입도 이름이 Connection으로 끝난다`() {
        assertConnectionNaming(FeedQueryResolver::class.java, feedConnectionReturningFields)
    }

    private fun assertConnectionNaming(resolver: Class<*>, fields: Set<String>) {
        val checked = resolver.declaredMethods
            .filter { it.name in fields }
            .filter { it.isAnnotationPresent(DgsQuery::class.java) || it.isAnnotationPresent(DgsData::class.java) }

        // 메서드 이름이 바뀌었는데 위 목록만 남으면 아무것도 검사하지 않는 테스트가 된다.
        assertTrue(
            checked.size == fields.size,
            "${resolver.simpleName}에서 커넥션 반환 리졸버를 ${fields.size}개 기대했으나 ${checked.size}개를 찾았다. " +
                "메서드 이름이 바뀌었다면 목록도 함께 고칠 것.",
        )

        checked.forEach { method ->
            val returned = method.returnType.name
            assertTrue(
                returned.endsWith("Connection"),
                "${method.name}의 반환 타입 이름이 'Connection'으로 끝나지 않는다: $returned\n" +
                    "  Spring for GraphQL이 커넥션으로 감싸지 못해 런타임에 " +
                    "'No ConnectionAdapter for: $returned'로 실패한다.",
            )
        }
    }
}
