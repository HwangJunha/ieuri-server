package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.AreaFilterRequiredException
import org.around.ieuri.tourism.application.exception.BboxTooLargeException
import org.around.ieuri.tourism.application.exception.CongestionAreaNotFoundException
import org.around.ieuri.tourism.application.exception.InvalidZoomException
import org.around.ieuri.tourism.application.exception.TooManyAreaIdsException
import org.around.ieuri.tourism.application.policy.MapViewportPolicy
import org.around.ieuri.tourism.application.policy.SeoulCitydataPolicy
import org.around.ieuri.tourism.application.port.out.cache.CachedCitydata
import org.around.ieuri.tourism.application.port.out.cache.SeoulCitydataCachePort
import org.around.ieuri.tourism.application.port.out.client.SeoulCitydataPort
import org.around.ieuri.tourism.application.port.out.client.SeoulCitydataSnapshot
import org.around.ieuri.tourism.application.port.out.query.CongestionAreaQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapClusterSearch
import org.around.ieuri.tourism.application.port.out.query.MapQueryPort
import org.around.ieuri.tourism.application.port.out.query.MapViewportSearch
import org.around.ieuri.tourism.application.query.CulturalEventView
import org.around.ieuri.tourism.application.query.MapSpotView
import org.around.ieuri.tourism.application.query.MapViewportQuery
import org.around.ieuri.tourism.domain.vo.AreaName
import org.around.ieuri.tourism.domain.vo.CommercialActivityLevel
import org.around.ieuri.tourism.domain.vo.CommercialStatus
import org.around.ieuri.tourism.domain.vo.CongestionForecast
import org.around.ieuri.tourism.domain.vo.CongestionLevel
import org.around.ieuri.tourism.domain.vo.CongestionSnapshot
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapBounds
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotCluster
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MapQueryService — SPEC-0014 R2·R3·R5·R6·R9·R11·R13·R14.
 *
 * 포트를 전부 스텁해 **서비스가 무엇을 호출하고 결과를 어떻게 조립하는지**만 본다. PostGIS
 * 공간 질의의 실제 결과, Valkey 직렬화 왕복, 실제 HTTP는 인프라 영역이라 다루지 않는다.
 */
class MapQueryServiceTest {

    private val mapQueryPort = mockk<MapQueryPort>()
    private val congestionAreaQueryPort = mockk<CongestionAreaQueryPort>()
    private val seoulCitydataPort = mockk<SeoulCitydataPort>()
    private val cachePort = mockk<SeoulCitydataCachePort>(relaxUnitFun = true)

    /** 캐시 신선도 판정(R13)의 경계를 고정하기 위해 '지금'을 못 박는다. */
    private val now = Instant.parse("2026-08-10T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val viewportPolicy = MapViewportPolicy(
        minZoom = 1,
        maxZoom = 14,
        clusterZoomThreshold = 10,
        maxAreaInSquareDegrees = 4.0,
        maxSpotMarkers = 3,
        maxClusters = 2,
        maxEventMarkers = 2,
        clusterGridSizeAtMinZoom = 1.0,
    )
    private val citydataPolicy = SeoulCitydataPolicy(
        minRefetchInterval = Duration.ofMinutes(5),
        maxAreaIds = 3,
    )

    private val service = MapQueryService(
        mapQueryPort = mapQueryPort,
        congestionAreaQueryPort = congestionAreaQueryPort,
        seoulCitydataPort = seoulCitydataPort,
        cachePort = cachePort,
        viewportPolicy = viewportPolicy,
        citydataPolicy = citydataPolicy,
        clock = clock,
    )

    private val seoulBounds = MapBounds(minLat = 37.4, minLng = 126.9, maxLat = 37.6, maxLng = 127.1)

    private fun spot(id: Long) = MapSpotView(
        id = id,
        title = "spot$id",
        thumbnailImageUrl = "https://img/$id",
        category = SpotCategory.TOURIST_SPOT,
        lng = 127.0,
        lat = 37.5,
    )

    private fun snapshot(areaCode: String, observedAt: LocalDateTime) = SeoulCitydataSnapshot(
        areaCode = areaCode,
        congestion = CongestionSnapshot(
            level = CongestionLevel.RELAXED,
            populationMin = 3000,
            populationMax = 3500,
            observedAt = observedAt,
        ),
        forecast = null,
        commercial = null,
        events = emptyList(),
    )

    // ---------- mapViewport ----------

    @Test
    fun `줌이 임계값 이상이면 개별 마커만 채워진다`() {
        // AC R2. spots와 clusters는 상호배타적이다.
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns emptyList()
        every { mapQueryPort.findSpotsWithin(any()) } returns listOf(spot(1), spot(2))

        val result = service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 12, category = null),
            DomainLocale.KO,
        )

        assertEquals(2, result.spots.size)
        assertTrue(result.clusters.isEmpty())
        assertFalse(result.truncated)
        verify(exactly = 0) { mapQueryPort.findClustersWithin(any()) }
    }

    @Test
    fun `줌이 임계값 미만이면 클러스터만 채워진다`() {
        // AC R2.
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns emptyList()
        every { mapQueryPort.findClustersWithin(any()) } returns
            listOf(SpotCluster(127.0, 37.5, 12, SpotCategory.SHOPPING))

        val result = service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 9, category = null),
            DomainLocale.KO,
        )

        assertEquals(1, result.clusters.size)
        assertTrue(result.spots.isEmpty())
        verify(exactly = 0) { mapQueryPort.findSpotsWithin(any()) }
    }

    @Test
    fun `상한을 초과하면 잘리고 truncated가 선다`() {
        // AC R3. 포트는 상한 + 1건을 돌려주고 서비스가 상한까지만 싣는다.
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns emptyList()
        every { mapQueryPort.findSpotsWithin(any()) } returns
            listOf(spot(1), spot(2), spot(3), spot(4))

        val result = service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 12, category = null),
            DomainLocale.KO,
        )

        assertEquals(3, result.spots.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `상한을 넘겨 조회해 잘림을 판정한다`() {
        // AC R3. COUNT(*)를 따로 세지 않고 상한 + 1로 판정한다는 것이 계약이다.
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns emptyList()
        val search = slot<MapViewportSearch>()
        every { mapQueryPort.findSpotsWithin(capture(search)) } returns emptyList()

        service.mapViewport(
            MapViewportQuery(bounds = seoulBounds, zoom = 12, category = null),
            DomainLocale.KO,
        )

        assertEquals(viewportPolicy.maxSpotMarkers + 1, search.captured.limit)
    }

    @Test
    fun `줌이 유효 범위 밖이면 거절된다`() {
        // AC R4.
        assertFailsWith<InvalidZoomException> {
            service.mapViewport(MapViewportQuery(seoulBounds, zoom = 0, category = null), DomainLocale.KO)
        }
        assertFailsWith<InvalidZoomException> {
            service.mapViewport(MapViewportQuery(seoulBounds, zoom = 15, category = null), DomainLocale.KO)
        }
    }

    @Test
    fun `면적 상한을 초과하면 거절된다`() {
        // AC R5. 전 지구 bbox 한 번으로 전체를 긁어가는 것을 막는다.
        val huge = MapBounds(minLat = 0.0, minLng = 0.0, maxLat = 80.0, maxLng = 170.0)

        assertFailsWith<BboxTooLargeException> {
            service.mapViewport(MapViewportQuery(huge, zoom = 12, category = null), DomainLocale.KO)
        }
    }

    @Test
    fun `문화행사는 캐시에서만 모으고 업스트림을 부르지 않는다`() {
        // AC R12. 지도를 움직일 때마다 행사 때문에 쿼터를 태우면 혼잡도를 볼 호출이 남지 않는다.
        val event = CulturalEventView("e1", "축제", "광장", "2026-08-01~2026-08-10", 127.0, 37.5, null)
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns listOf("POI009")
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot = snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")).copy(events = listOf(event)),
            storedAt = now,
        )
        every { mapQueryPort.findSpotsWithin(any()) } returns emptyList()

        val result = service.mapViewport(
            MapViewportQuery(seoulBounds, zoom = 12, category = null),
            DomainLocale.KO,
        )

        assertEquals(listOf(event), result.events)
        verify(exactly = 0) { seoulCitydataPort.fetch(any(), any()) }
    }

    // ---------- seoulCongestion ----------

    @Test
    fun `구역 지정이 없으면 거절된다`() {
        // AC R6. 범위 없는 전체 조회는 일일 쿼터를 한 번에 소진시킨다.
        assertFailsWith<AreaFilterRequiredException> {
            service.seoulCongestion(areaIds = null, bounds = null, locale = DomainLocale.KO)
        }
    }

    @Test
    fun `없는 구역 id는 거절된다`() {
        // AC R6.
        every { congestionAreaQueryPort.findNamesByCodes(listOf("POI999")) } returns emptyMap()

        assertFailsWith<CongestionAreaNotFoundException> {
            service.seoulCongestion(areaIds = listOf("POI999"), bounds = null, locale = DomainLocale.KO)
        }
    }

    @Test
    fun `구역 id 개수가 상한을 넘으면 거절된다`() {
        // AC R6. 구역 수가 곧 호출 수라 클램프가 아니라 거절이다.
        assertFailsWith<TooManyAreaIdsException> {
            service.seoulCongestion(
                areaIds = listOf("POI001", "POI002", "POI003", "POI004"),
                bounds = null,
                locale = DomainLocale.KO,
            )
        }
    }

    @Test
    fun `구역 id와 bbox가 함께 오면 구역 id가 우선한다`() {
        // AC R6. bbox는 무시되므로 bbox 경로가 호출되지 않아야 한다.
        val names = mapOf("POI009" to AreaName("광화문", "Gwanghwamun", null, null))
        every { congestionAreaQueryPort.findNamesByCodes(listOf("POI009")) } returns names
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")),
            storedAt = now,
        )

        val result = service.seoulCongestion(
            areaIds = listOf("POI009"),
            bounds = seoulBounds,
            locale = DomainLocale.KO,
        )

        assertEquals(listOf("POI009"), result.areas.map { it.id })
        verify(exactly = 0) { congestionAreaQueryPort.findAreaCodesWithin(any()) }
    }

    @Test
    fun `bbox에 걸치는 구역이 없으면 빈 결과이며 오류가 아니다`() {
        // AC R6. 지도가 서울 밖이면 호출이 0이다.
        every { congestionAreaQueryPort.findAreaCodesWithin(seoulBounds) } returns emptyList()

        val result = service.seoulCongestion(areaIds = null, bounds = seoulBounds, locale = DomainLocale.KO)

        assertTrue(result.areas.isEmpty())
        assertFalse(result.degraded)
    }

    @Test
    fun `캐시가 신선하면 업스트림을 부르지 않는다`() {
        // AC R13. 저장 시각 기준으로 판정한다 — 데이터가 30분 지연 도착하므로 5분 경계 TTL은
        // 같은 데이터를 반복 호출한다.
        stubArea("POI009")
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")),
            storedAt = now.minus(Duration.ofMinutes(3)),
        )

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertEquals(1, result.areas.size)
        assertFalse(result.degraded)
        verify(exactly = 0) { seoulCitydataPort.fetch(any(), any()) }
    }

    @Test
    fun `최소 재호출 간격이 지나면 업스트림을 다시 부른다`() {
        // AC R13.
        stubArea("POI009")
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:15")),
            storedAt = now.minus(Duration.ofMinutes(10)),
        )
        val fresh = snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45"))
        every { seoulCitydataPort.fetch("POI009", "광화문") } returns fresh

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertEquals(LocalDateTime.parse("2026-08-10T05:45"), result.areas.single().congestion.observedAt)
        verify { cachePort.put("POI009", fresh, now) }
    }

    @Test
    fun `업스트림이 실패하면 낡은 캐시를 주고 degraded를 세운다`() {
        // AC R11. 만료된 캐시라도 돌려주고 그 시점을 staleSince에 담는다.
        stubArea("POI009")
        val staleAt = LocalDateTime.parse("2026-08-10T04:30")
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", staleAt),
            storedAt = now.minus(Duration.ofHours(1)),
        )
        every { seoulCitydataPort.fetch("POI009", "광화문") } returns null

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertTrue(result.degraded)
        assertEquals(staleAt, result.staleSince)
        assertEquals(1, result.areas.size)
    }

    @Test
    fun `업스트림이 실패하고 캐시도 없으면 그 구역은 빠진다`() {
        // AC R11. 예외로 던지지 않는다 — 지도 전체가 실패한 것처럼 다뤄지지 않게.
        stubArea("POI009")
        every { cachePort.get("POI009") } returns null
        every { seoulCitydataPort.fetch("POI009", "광화문") } returns null

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertTrue(result.degraded)
        assertTrue(result.areas.isEmpty())
        assertNull(result.staleSince)
    }

    @Test
    fun `예보 부재는 오류가 아니라 null이다`() {
        // AC R9.
        stubArea("POI009")
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")),
            storedAt = now,
        )

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertNull(result.areas.single().forecast)
        assertFalse(result.degraded)
    }

    @Test
    fun `예보가 있으면 그대로 실린다`() {
        // AC R9.
        stubArea("POI009")
        val forecast = List(12) {
            CongestionForecast(
                forecastTime = LocalDateTime.parse("2026-08-10T07:00").plusHours(it.toLong()),
                level = CongestionLevel.NORMAL,
                populationMin = 100,
                populationMax = 200,
            )
        }
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")).copy(forecast = forecast),
            storedAt = now,
        )

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertEquals(12, result.areas.single().forecast?.size)
    }

    @Test
    fun `상권 3상태가 그대로 전달된다`() {
        // AC R10. 부재는 null, 기본값은 isDefaulted=true로 구분된다.
        stubArea("POI009")
        val defaulted = CommercialStatus(
            level = CommercialActivityLevel.NORMAL,
            isDefaulted = true,
            observedAt = LocalDateTime.parse("2026-08-10T06:00"),
        )
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")).copy(commercial = defaulted),
            storedAt = now,
        )

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.KO)

        assertEquals(true, result.areas.single().commercial?.isDefaulted)
        assertEquals(CommercialActivityLevel.NORMAL, result.areas.single().commercial?.level)
    }

    @Test
    fun `구역명은 활성 언어로 해소된다`() {
        // AC R31. 서비스가 AreaName.resolve를 거쳐 담는다.
        every { congestionAreaQueryPort.findNamesByCodes(listOf("POI009")) } returns
            mapOf("POI009" to AreaName("광화문", "Gwanghwamun", "光化門", null))
        every { cachePort.get("POI009") } returns CachedCitydata(
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45")),
            storedAt = now,
        )

        val result = service.seoulCongestion(listOf("POI009"), null, DomainLocale.JA)

        assertEquals("光化門", result.areas.single().name)
    }

    @Test
    fun `업스트림은 활성 언어와 무관하게 국문명으로 조회한다`() {
        // 업스트림이 국문 구역명으로만 조회를 받는다.
        every { congestionAreaQueryPort.findNamesByCodes(listOf("POI009")) } returns
            mapOf("POI009" to AreaName("광화문", "Gwanghwamun", "光化門", null))
        every { cachePort.get("POI009") } returns null
        every { seoulCitydataPort.fetch("POI009", "광화문") } returns
            snapshot("POI009", LocalDateTime.parse("2026-08-10T05:45"))

        service.seoulCongestion(listOf("POI009"), null, DomainLocale.JA)

        verify { seoulCitydataPort.fetch("POI009", "광화문") }
    }

    private fun stubArea(code: String) {
        every { congestionAreaQueryPort.findNamesByCodes(listOf(code)) } returns
            mapOf(code to AreaName("광화문", "Gwanghwamun", null, null))
    }
}
