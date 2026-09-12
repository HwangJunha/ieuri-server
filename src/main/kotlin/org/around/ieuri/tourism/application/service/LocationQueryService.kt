package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.keysetPageOf
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.LocationQueryUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.query.NearbyKeysetSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.LocationView
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.validation.coordinatesOf
import org.around.ieuri.tourism.application.validation.requireRadiusWithin
import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.stereotype.Service

/**
 * `Query.location`과 `Location.spots`(SPEC-0035 R1~R3). 후보·제외·검증은 `Query.spots`와 같고
 * 페이지만 (거리, id) 키셋이다. 반경 상한·페이지 상한도 같은 [SpotQueryPolicy]를 쓴다.
 */
@Service
class LocationQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val eventPublisher: TourismEventPublisherPort,
    private val policy: SpotQueryPolicy,
) : LocationQueryUseCase {

    /**
     * 좌표 검증과 위치 조회 감사(SPEC-0032 R19)만 한다. **좌표는 이벤트에 넘기지 않는다**(POL-045).
     * 비로그인은 남길 "누구"가 없어 발행하지 않는다.
     */
    override fun location(latitude: Double, longitude: Double, memberId: Long?): LocationView {
        val center = coordinatesOf(latitude, longitude)
        memberId?.let { eventPublisher.publish(LocationAccessed(it, "location")) }
        return LocationView(latitude = center.latitude, longitude = center.longitude)
    }

    override fun spots(query: LocationViewQuery, locale: DomainLocale, memberId: Long?): CursorPage<SpotView> {
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, policy.maxRadiusMeters)
        val first = query.first.coerceIn(1, policy.maxPageSize)

        // 반응 제외는 DB 단계에서, 활성 언어 단위로 — `Query.spots`와 같다(AC-3.2).
        val excluded = memberId?.let { spotQueryPort.findReactedSpotIds(it, locale) }.orEmpty()
        val after = query.after?.let {
            try {
                DistanceCursor.decode(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidCursorException(cause = e)
            }
        }

        val rows = spotSpatialQueryPort.searchNearbyAfter(
            NearbyKeysetSearch(
                language = locale,
                center = center,
                radiusMeters = query.radiusMeters,
                categories = query.categories,
                excludedSpotIds = excluded,
                limit = first,
                after = after,
            ),
        )
        return keysetPageOf(rows = rows.map { it.toView() }, first = first, after = query.after) {
            DistanceCursor(it.distanceMeters, it.id).encode()
        }
    }
}
