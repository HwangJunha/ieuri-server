package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiPetTourResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncPetFriendlyCommandOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * detailPetTour2 매퍼의 필드별 매핑 검증(SPEC-0015 · V48) — 순수 단위테스트(HTTP/Spring 없음).
 *
 * **필드마다 값이 서로 다르다** — 같은 값을 쓰면 두 필드가 뒤바뀌어도 통과한다(POL-033).
 */
class TourApiPetTourMapperTest {

    private fun item(
        contentid: String? = "126508",
        acmpyTypeCd: String? = "전구역 동반가능",
        acmpyPsblCpam: String? = "전 견종 동반 가능",
        acmpyNeedMtr: String? = "목줄 착용",
        etcAcmpyInfo: String? = "실내 동반 불가\n주차장 이용 가능",
    ) = TourApiPetTourResponse.Item(
        contentid = contentid,
        acmpyTypeCd = acmpyTypeCd,
        acmpyPsblCpam = acmpyPsblCpam,
        acmpyNeedMtr = acmpyNeedMtr,
        etcAcmpyInfo = etcAcmpyInfo,
    )

    @Test
    fun `모든 필드가 각자의 자리로 매핑된다`() {
        val command = assertNotNull(item().toSyncPetFriendlyCommandOrNull())

        assertEquals("126508", command.contentId)
        assertEquals("전구역 동반가능", command.accompanyType)
        assertEquals("전 견종 동반 가능", command.accompanyScope)
        assertEquals("목줄 착용", command.requiredMatters)
        assertEquals("실내 동반 불가\n주차장 이용 가능", command.etcInfo)
    }

    @Test
    fun `contentid가 없으면 드랍한다 — PK가 될 값이다`() {
        assertNull(item(contentid = null).toSyncPetFriendlyCommandOrNull())
    }

    @Test
    fun `contentid가 공백뿐이어도 드랍한다`() {
        assertNull(item(contentid = "   ").toSyncPetFriendlyCommandOrNull())
    }

    @Test
    fun `contentid의 앞뒤 공백은 제거된다 — PK가 공백으로 갈리면 안 된다`() {
        val command = assertNotNull(item(contentid = " 126508 ").toSyncPetFriendlyCommandOrNull())
        assertEquals("126508", command.contentId)
    }

    @Test
    fun `nullable 필드가 전부 null이어도 예외 없이 매핑된다`() {
        val command = assertNotNull(
            item(
                acmpyTypeCd = null,
                acmpyPsblCpam = null,
                acmpyNeedMtr = null,
                etcAcmpyInfo = null,
            ).toSyncPetFriendlyCommandOrNull(),
        )

        assertEquals("126508", command.contentId)
        assertNull(command.accompanyType)
        assertNull(command.accompanyScope)
        assertNull(command.requiredMatters)
        assertNull(command.etcInfo)
    }

    @Test
    fun `빈 문자열과 공백뿐인 값은 null이 된다 — 결손을 빈 값으로 적재하지 않는다`() {
        val command = assertNotNull(
            item(
                acmpyTypeCd = "",
                acmpyPsblCpam = "   ",
                acmpyNeedMtr = "\n",
                etcAcmpyInfo = "",
            ).toSyncPetFriendlyCommandOrNull(),
        )

        assertNull(command.accompanyType)
        assertNull(command.accompanyScope)
        assertNull(command.requiredMatters)
        assertNull(command.etcInfo)
    }

    @Test
    fun `여러 줄 안내문은 줄바꿈과 앞뒤 공백까지 원문 그대로 보존한다`() {
        val raw = "  1. 목줄 필수\n2. 배변봉투 지참\n"
        val command = assertNotNull(item(etcAcmpyInfo = raw).toSyncPetFriendlyCommandOrNull())
        assertEquals(raw, command.etcInfo)
    }
}
