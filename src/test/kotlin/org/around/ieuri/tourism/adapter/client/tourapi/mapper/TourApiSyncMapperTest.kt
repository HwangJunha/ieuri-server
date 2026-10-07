package org.around.ieuri.tourism.adapter.client.tourapi.mapper

import org.around.ieuri.tourism.adapter.client.tourapi.TourApiFestivalResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiResponse
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
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
        showflag: String? = "1",
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
        showflag = showflag,
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

    // ── toSpotOrNull ─────────────────────────────────────

    @Test
    fun `모든 필드가 채워진 item을 SyncSpotCommand로 옮긴다`() {
        val command = assertNotNull(
            item().toSpotOrNull(DomainLocale.JA, SpotCategory("SHOPPING")),
        )

        assertEquals("3001", command.contentId)
        assertEquals(DomainLocale.JA, command.language)
        assertEquals("남산타워", command.title)
        assertEquals("서울특별시 용산구 남산공원길 105", command.address)
        assertEquals("https://tong.visitkorea.or.kr/3001.jpg", command.thumbnailImageUrl)
        assertEquals(37.551169, command.coordinates.latitude)
        assertEquals(126.988205, command.coordinates.longitude)
        assertEquals(SpotCategory("SHOPPING"), command.category)
        assertEquals(12, command.contentTypeId)
        assertEquals(CopyrightType.TYPE1, command.copyright)
    }

    @Test
    fun `SyncSpotCommand는 nullable 필드가 모두 null이어도 예외 없이 옮긴다`() {
        val command = assertNotNull(
            item(addr1 = null, addr2 = null, cpyrhtDivCd = null)
                .toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT),
        )

        assertNull(command.address)
        assertEquals(CopyrightType.UNSPECIFIED, command.copyright)
        assertEquals("3001", command.contentId)
    }

    @Test
    fun `SyncSpotCommand도 위경도를 mapy·mapx 순으로 옮긴다`() {
        // 한국 범위 안의 값을 쓰되 경도·위도의 자릿수를 달리 둔다 — 범위 필터가 아니라 순서를 검증한다.
        val command = assertNotNull(
            item(mapx = 128.5, mapy = 35.25)
                .toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT),
        )

        assertEquals(35.25, command.coordinates.latitude)
        assertEquals(128.5, command.coordinates.longitude)
    }

    @Test
    fun `SyncSpotCommand는 필수 필드가 공백뿐이면 null이다`() {
        assertNull(item(contentid = "  ").toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNull(item(title = "  ").toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNull(item(firstimage = "  ").toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `showflag가 1이 아닌 항목은 적재하지 않는다`() {
        // AC-2.2. 요청의 showflag=1에만 기대지 않는다.
        assertNull(item(showflag = "0").toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `showflag가 없는 항목은 적재하지 않는다`() {
        // AC-2.3. 결측을 `"1"`로 간주하면 안 된다.
        assertNull(item(showflag = null).toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNull(item(showflag = "  ").toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `한국 범위를 벗어난 좌표는 적재하지 않는다`() {
        // `Coordinates`는 지구 전체를 유효로 보므로 (0,0)과 상류 플레이스홀더를 여기서 막는다.
        assertNull(item(mapx = 0.0, mapy = 0.0).toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        // 상류가 좌표를 모를 때 넣는 값 — 남중국해다.
        assertNull(
            item(mapx = 117.9925662504, mapy = 19.69442748)
                .toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT),
        )
        // 경도만 벗어나도, 위도만 벗어나도 드랍이다.
        assertNull(item(mapx = 139.7, mapy = 35.6).toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `한국 범위 경계값은 적재한다`() {
        // 경계는 포함이다.
        assertNotNull(item(mapx = 124.0, mapy = 33.0).toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
        assertNotNull(item(mapx = 132.0, mapy = 39.0).toSpotOrNull(DomainLocale.KO, SpotCategory.TOURIST_SPOT))
    }

    // ── toSpotFestivalOrNull ─────────────────────────────────

    @Test
    fun `모든 필드가 채워진 축제 item을 SyncFestivalCommand로 옮긴다`() {
        val command = assertNotNull(festivalItem().toSpotFestivalOrNull(DomainLocale.ZH, asOf))

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
                .toSpotFestivalOrNull(DomainLocale.KO, asOf),
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
                .toSpotFestivalOrNull(DomainLocale.KO, asOf),
        )

        assertEquals(LocalDate.of(2026, 2, 1), command.period.start)
        assertEquals(LocalDate.of(2026, 12, 31), command.period.end)
    }
}
