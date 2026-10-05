package org.around.ieuri.community.infrastructure

import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsQuery
import org.around.ieuri.community.adapter.graphql.CommunityQueryResolver
import org.around.ieuri.community.adapter.graphql.ConversationQueryResolver
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 커넥션 반환 타입의 이름이 `Connection`으로 끝나는지 고정한다.
 * - 아니면 Spring for GraphQL이 런타임에 `No ConnectionAdapter for: ...`로 조회를 깬다.
 */
class ConnectionNamingTest {

    /** 스키마에서 커넥션을 반환하는 필드 이름. 늘어나면 여기에 추가한다. */
    private val connectionReturningFields = setOf(
        "posts",
        "comments",
        "blockedUsers",
    )

    /** DM 커넥션(SPEC-0017). */
    private val conversationConnectionReturningFields = setOf(
        "conversations",
        "conversationMessages",
    )

    @Test
    fun `커넥션을 반환하는 리졸버의 반환 타입은 이름이 Connection으로 끝난다`() {
        assertConnectionNaming(CommunityQueryResolver::class.java, connectionReturningFields)
    }

    @Test
    fun `대화 커넥션의 반환 타입도 이름이 Connection으로 끝난다`() {
        assertConnectionNaming(ConversationQueryResolver::class.java, conversationConnectionReturningFields)
    }

    private fun assertConnectionNaming(resolver: Class<*>, fields: Set<String>) {
        val checked = resolver.declaredMethods
            .filter { it.name in fields }
            .filter { it.isAnnotationPresent(DgsQuery::class.java) || it.isAnnotationPresent(DgsData::class.java) }

        // 목록의 메서드가 실제로 있는지 확인한다.
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
