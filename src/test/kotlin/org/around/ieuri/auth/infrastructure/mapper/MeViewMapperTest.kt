package org.around.ieuri.auth.infrastructure.mapper

import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.auth.domain.vo.MemberStatus
import org.around.ieuri.auth.domain.vo.Provider
import org.around.ieuri.auth.infrastructure.adapter.`in`.graphql.mapper.toGql
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.around.ieuri.common.graphql.generated.types.MemberStatus as GqlMemberStatus

/**
 * MeView → GraphQL `Me` 변환(POL-033). 필드마다 **서로 다른 값**을 줘 두 필드가 뒤바뀌면
 * 반드시 깨지게 한다.
 */
class MeViewMapperTest {

    private val createdAt = Instant.parse("2026-03-04T05:06:07Z")

    private fun meView(provider: Provider = Provider.EMAIL) = MeView(
        memberId = 42L,
        email = "me@example.com",
        nickname = "닉네임값",
        profileImageUrl = "https://cdn.example.com/a.png",
        status = MemberStatus.ACTIVE,
        emailVerified = true,
        provider = provider,
        roles = listOf("USER"),
        permissions = listOf("post:write"),
        createdAt = createdAt,
    )

    @Test
    fun `모든 필드가 제자리로 옮겨진다`() {
        val gql = meView().toGql()

        assertEquals(42L, gql.id)
        assertEquals("me@example.com", gql.email)
        assertEquals("닉네임값", gql.nickname)
        assertEquals("https://cdn.example.com/a.png", gql.profileImageUrl)
        assertEquals(GqlMemberStatus.ACTIVE, gql.status)
        assertTrue(gql.emailVerified)
        assertEquals(listOf("USER"), gql.roles)
        assertEquals(listOf("post:write"), gql.permissions)
        assertEquals(createdAt.toString(), gql.createdAt)
    }

    /** R6-3. */
    @Test
    fun `EMAIL 회원은 canChangeEmail이 참이다`() {
        assertTrue(meView(Provider.EMAIL).toGql().canChangeEmail)
    }

    /** R6-4. provider별로 갈리는지 전부 확인한다 — SNS 셋은 모두 거짓이다. */
    @Test
    fun `SNS 회원은 canChangeEmail이 거짓이다`() {
        listOf(Provider.KAKAO, Provider.GOOGLE, Provider.APPLE).forEach { provider ->
            assertFalse(meView(provider).toGql().canChangeEmail, "$provider 는 이메일을 변경할 수 없다")
        }
    }

    @Test
    fun `nullable 필드가 비어 있어도 변환된다`() {
        val gql = meView().copy(profileImageUrl = null, roles = emptyList(), permissions = emptyList()).toGql()

        assertNull(gql.profileImageUrl)
        assertEquals(emptyList<String>(), gql.roles)
        assertEquals(emptyList<String>(), gql.permissions)
    }
}
