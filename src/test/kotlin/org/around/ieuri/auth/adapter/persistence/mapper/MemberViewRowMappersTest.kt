package org.around.ieuri.auth.adapter.persistence.mapper

import org.around.ieuri.auth.adapter.persistence.table.MemberProfileTable
import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.around.ieuri.auth.adapter.persistence.table.RoleTable
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 회원 읽기 모델의 행 변환(POL-033). 프로필 행이 없는 회원(LEFT JOIN의 null)은 기본 닉네임으로 보인다. */
class MemberViewRowMappersTest {

    private val createdAt = OffsetDateTime.of(2026, 10, 5, 3, 0, 0, 0, ZoneOffset.UTC)
    private val created = Instant.parse("2026-10-05T03:00:00Z")
    private val suspendedAt = OffsetDateTime.of(2026, 10, 6, 4, 0, 0, 0, ZoneOffset.UTC)
    private val suspendedUntil = OffsetDateTime.of(2026, 10, 7, 5, 0, 0, 0, ZoneOffset.UTC)

    /** member ⟕ member_profile 한 행. [overrides]로 일부 열만 바꾼다. */
    private fun joinedRow(vararg overrides: Pair<Expression<*>, Any?>): ResultRow = ResultRow.createAndFillValues(
        mapOf<Expression<*>, Any?>(
            MemberTable.id to 11L,
            MemberTable.provider to "GOOGLE",
            MemberTable.status to "SUSPENDED",
            MemberTable.emailVerified to true,
            MemberTable.suspendedAt to suspendedAt,
            MemberTable.suspendedUntil to suspendedUntil,
            MemberTable.suspensionReason to "사유-값",
            MemberTable.createdAt to createdAt,
            MemberProfileTable.nickname to "닉네임-값",
            MemberProfileTable.profileImageUrl to "images/profile/11/a",
        ) + overrides,
    )

    private fun withoutProfile(vararg overrides: Pair<Expression<*>, Any?>) = joinedRow(
        MemberProfileTable.nickname to null,
        MemberProfileTable.profileImageUrl to null,
        *overrides,
    )

    @Test
    fun `본인 정보 행이 읽기 모델로 옮겨진다`() {
        val view = joinedRow().toMeView(email = "me@example.com", roles = listOf("ADMIN", "USER"), permissions = listOf("role:assign"))

        assertEquals(11L, view.memberId)
        assertEquals("me@example.com", view.email)
        assertEquals("닉네임-값", view.nickname)
        assertEquals("images/profile/11/a", view.profileImageUrl)
        assertEquals(MemberStatus.SUSPENDED, view.status)
        assertEquals(true, view.emailVerified)
        assertEquals(Provider.GOOGLE, view.provider)
        assertEquals(listOf("ADMIN", "USER"), view.roles)
        assertEquals(listOf("role:assign"), view.permissions)
        assertEquals(created, view.createdAt)
    }

    @Test
    fun `프로필 행이 없는 본인 정보는 기본 닉네임과 이미지 없음으로 보인다`() {
        val view = withoutProfile(MemberTable.emailVerified to false, MemberTable.status to "ACTIVE", MemberTable.provider to "EMAIL")
            .toMeView(email = "me@example.com", roles = emptyList(), permissions = emptyList())

        assertEquals("여행자", view.nickname)
        assertNull(view.profileImageUrl)
        assertEquals(false, view.emailVerified)
        assertEquals(MemberStatus.ACTIVE, view.status)
        assertEquals(Provider.EMAIL, view.provider)
        assertEquals(emptyList(), view.roles)
        assertEquals(emptyList(), view.permissions)
    }

    @Test
    fun `관리자 단건 상세 행이 읽기 모델로 옮겨진다 - 정지 시작과 만료가 섞이지 않는다`() {
        val view = joinedRow().toMemberDetailView(email = "detail@example.com", roles = listOf("USER"))

        assertEquals(11L, view.memberId)
        assertEquals("detail@example.com", view.email)
        assertEquals("닉네임-값", view.nickname)
        assertEquals("images/profile/11/a", view.profileImageUrl)
        assertEquals(MemberStatus.SUSPENDED, view.status)
        assertEquals(true, view.emailVerified)
        assertEquals(Instant.parse("2026-10-06T04:00:00Z"), view.suspendedAt)
        assertEquals(Instant.parse("2026-10-07T05:00:00Z"), view.suspendedUntil)
        assertEquals("사유-값", view.suspensionReason)
        assertEquals(listOf("USER"), view.roles)
        assertEquals(created, view.createdAt)
    }

    @Test
    fun `정지 정보와 프로필이 없는 상세 행은 null과 기본 닉네임으로 옮겨진다`() {
        val view = withoutProfile(
            MemberTable.status to "ACTIVE",
            MemberTable.suspendedAt to null,
            MemberTable.suspendedUntil to null,
            MemberTable.suspensionReason to null,
        ).toMemberDetailView(email = "detail@example.com", roles = emptyList())

        assertEquals("여행자", view.nickname)
        assertNull(view.profileImageUrl)
        assertEquals(MemberStatus.ACTIVE, view.status)
        assertNull(view.suspendedAt)
        assertNull(view.suspendedUntil)
        assertNull(view.suspensionReason)
        assertEquals(emptyList(), view.roles)
    }

    @Test
    fun `표시용 최소 정보 행이 읽기 모델로 옮겨진다`() {
        val view = joinedRow().toMemberPublicProfileView()
        val bare = withoutProfile().toMemberPublicProfileView()

        assertEquals(11L, view.memberId)
        assertEquals("닉네임-값", view.nickname)
        assertEquals("images/profile/11/a", view.profileImageUrl)
        assertEquals(11L, bare.memberId)
        assertEquals("여행자", bare.nickname)
        assertNull(bare.profileImageUrl)
    }

    @Test
    fun `관리자 목록 항목 행이 읽기 모델로 옮겨진다`() {
        val view = joinedRow().toMemberSummaryView(roles = listOf("ADMIN"))
        val bare = withoutProfile().toMemberSummaryView(roles = emptyList())

        assertEquals(11L, view.memberId)
        assertEquals("닉네임-값", view.nickname)
        assertEquals(MemberStatus.SUSPENDED, view.status)
        assertEquals(listOf("ADMIN"), view.roles)
        assertEquals(created, view.createdAt)
        assertEquals("여행자", bare.nickname)
        assertEquals(emptyList(), bare.roles)
    }

    @Test
    fun `역할 행이 부여 가능한 역할로 옮겨진다 - 코드와 이름과 설명이 섞이지 않는다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(RoleTable.id to 21L, RoleTable.code to "ADMIN", RoleTable.name to "관리자-이름", RoleTable.description to "설명-값"),
        ).toRoleInfoView()
        val bare = ResultRow.createAndFillValues(
            mapOf(RoleTable.id to 22L, RoleTable.code to "USER", RoleTable.name to "사용자-이름", RoleTable.description to null),
        ).toRoleInfoView()

        assertEquals("ADMIN", view.code)
        assertEquals("관리자-이름", view.name)
        assertEquals("설명-값", view.description)
        assertEquals("USER", bare.code)
        assertNull(bare.description)
    }
}
