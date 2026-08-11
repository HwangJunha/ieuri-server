package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiFestivalResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toNearbySpotDataOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncFestivalCommandOrNull
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncSpotCommandOrNull
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** TourApiSyncMapper 필드별 매핑 검증 — 순수 단위테스트(HTTP/Spring 없음). */
class TourApiSyncMapperTest {

    private val asOf: LocalDate = LocalDate.of(2026, 8, 4)

    private fun item(
        contentid: String? = "3001",
        title: String? = "남산타워",
        addr1: String? = "서울특별시 용산구",
        addr2: String? = "남산공원길 105",
        firstimage: String? = "http://tong.visitkorea.or.kr/3001.jpg",
        mapx: Double? = 126.988205,
        mapy: Double? = 37.551169,
        contenttypeid: String? = "12",
        cpyrhtDivCd: String? = "Type1",
    ) = TourApiResponse.Item(
        contentid = contentid,
        title = title,
        addr1 = addr1,
        addr2 = addr2,
        firstimage = firstimage,
        mapx = mapx,
        mapy = mapy,
        contenttypeid = contenttypeid,
        cpyrhtDivCd = cpyrhtDivCd,
    )

    private fun festivalItem(
        contentid: String? = "4001",
        eventstartdate: String? = "20260901",
        eventenddate: String? = "20260910",
        progresstype: String? = "진행중",
        festivaltype: String? = "문화관광축제",
    ) = TourApiFestivalResponse.Item(
        contentid = contentid,
        eventstartdate = eventstartdate,
        eventenddate = eventenddate,
        progresstype = progresstype,
        festivaltype = festivaltype,
    )

    // ── toNearbySpotDataOrNull ──────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 item을 NearbySpotData로 옮긴다`() {
        val data = assertNotNull(item().toNearbySpotDataOrNull(SpotCategory.SHOPPING))

        assertEquals("3001", data.id)
        assertEquals("남산타워", data.title)
        assertEquals("서울특별시 용산구 남산공원길 105", data.address)
        assertEquals("https://tong.visitkorea.or.kr/3001.jpg", data.thumbnailImageUrl)
        assertEquals(37.551169, data.coordinates.latitude)
        assertEquals(126.988205, data.coordinates.longitude)
        assertEquals(SpotCategory.SHOPPING, data.category)
        assertEquals(CopyrightType.TYPE1, data.copyright)
    }

    @Test
    fun `nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        // 주소(addr1·addr2)와 저작권 코드만이 없어도 되는 필드다.
        val data = assertNotNull(
            item(addr1 = null, addr2 = null, cpyrhtDivCd = null)
                .toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT),
        )

        assertNull(data.address)
        assertEquals(CopyrightType.UNSPECIFIED, data.copyright)
        assertEquals("3001", data.id)
    }

    @Test
    fun `NearbySpotData는 위경도를 mapy·mapx 순으로 옮긴다`() {
        // mapy가 위도, mapx가 경도다 — 뒤바뀌면 지도상 위치가 통째로 달라진다.
        val data = assertNotNull(
            item(mapx = 100.0, mapy = 20.0).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT),
        )

        assertEquals(20.0, data.coordinates.latitude)
        assertEquals(100.0, data.coordinates.longitude)
    }

    @Test
    fun `NearbySpotData는 필수 필드가 없으면 null이다`() {
        assertNull(item(contentid = null).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT))
        assertNull(item(title = null).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT))
        assertNull(item(firstimage = null).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT))
        assertNull(item(mapx = null).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT))
        assertNull(item(mapy = null).toNearbySpotDataOrNull(SpotCategory.TOURIST_SPOT))
    }

    // ── toSyncSpotCommandOrNull ─────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 item을 SyncSpotCommand로 옮긴다`() {
        val command = assertNotNull(
            item().toSyncSpotCommandOrNull(DomainLocale.JA, SpotCategory.SHOPPING),
        )

        assertEquals("3001", command.contentId)
        assertEquals(DomainLocale.JA, command.language)
        assertEquals("남산타워", command.title)
        assertEquals("서울특별시 용산구 남산공원길 105", command.address)
        assertEquals("https://tong.visitkorea.or.kr/3001.jpg", command.thumbnailImageUrl)
        assertEquals(37.551169, command.coordinates.latitude)
        assertEquals(126.988205, command.coordinates.longitude)
        assertEquals(SpotCategory.SHOPPING, command.category)
        assertEquals(12, command.contentTypeId)
        assertEquals(CopyrightType.TYPE1, command.copyright)
    }

    @Test
    fun `SyncSpotCommand는 nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val command = assertNotNull(
            item(addr1 = null, addr2 = null, cpyrhtDivCd = null)
                .toSyncSpotCommandOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT),
        )

        assertNull(command.address)
        assertEquals(CopyrightType.UNSPECIFIED, command.copyright)
        assertEquals("3001", command.contentId)
    }

    @Test
    fun `SyncSpotCommand도 위경도를 mapy·mapx 순으로 옮긴다`() {
        val command = assertNotNull(
            item(mapx = 100.0, mapy = 20.0)
                .toSyncSpotCommandOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT),
        )

        assertEquals(20.0, command.coordinates.latitude)
        assertEquals(100.0, command.coordinates.longitude)
    }

    @Test
    fun `SyncSpotCommand는 필수 필드가 공백뿐이면 null이다`() {
        assertNull(item(contentid = "  ").toSyncSpotCommandOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNull(item(title = "  ").toSyncSpotCommandOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNull(item(firstimage = "  ").toSyncSpotCommandOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    // ── toSyncFestivalCommandOrNull ─────────────────────────────────

    @Test
    fun `모든 필드가 채워진 축제 item을 SyncFestivalCommand로 옮긴다`() {
        val command = assertNotNull(festivalItem().toSyncFestivalCommandOrNull(DomainLocale.ZH, asOf))

        assertEquals("4001", command.contentId)
        assertEquals(DomainLocale.ZH, command.language)
        assertEquals(LocalDate.of(2026, 9, 1), command.period.start)
        assertEquals(LocalDate.of(2026, 9, 10), command.period.end)
        assertEquals("진행중", command.progressType)
        assertEquals("문화관광축제", command.festivalType)
    }

    @Test
    fun `축제의 nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val command = assertNotNull(
            festivalItem(progresstype = null, festivaltype = null)
                .toSyncFestivalCommandOrNull(DomainLocale.KO, asOf),
        )

        assertNull(command.progressType)
        assertNull(command.festivalType)
        assertEquals("4001", command.contentId)
        assertEquals(LocalDate.of(2026, 9, 1), command.period.start)
    }

    @Test
    fun `축제 기간은 시작일과 종료일이 뒤바뀌지 않고 옮겨진다`() {
        val command = assertNotNull(
            festivalItem(eventstartdate = "20260201", eventenddate = "20261231")
                .toSyncFestivalCommandOrNull(DomainLocale.KO, asOf),
        )

        assertEquals(LocalDate.of(2026, 2, 1), command.period.start)
        assertEquals(LocalDate.of(2026, 12, 31), command.period.end)
    }
}
