package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPage
import org.around.ieuri.tourism.application.port.out.client.PetFriendlySyncPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncPetFriendlyCommandOrNull
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder

/**
 * 반려동물 동반 가능 관광지 수집 어댑터(detailPetTour2 · SPEC-0015 · V48).
 *
 * **국문 서비스에만 존재하는 오퍼레이션이다.** EN/JA/ZH base-url로 부르면
 * `NO_OPENAPI_SERVICE_ERROR`가 온다(실측 2026-08-11). 그래서 [TourApiSyncClient]와 달리 언어를
 * 인자로 받지 않고 KO base-url을 고정해 쓴다 — 언어 인자를 두면 부를 수 없는 조합이 호출자에게
 * 열려 보인다.
 *
 * 실측 totalCount 9,701건이며 응답 봉투는 다른 오퍼레이션과 같은 형태다.
 */
@Component
class TourApiPetTourSyncClient(
    private val properties: TourApiProperties,
    private val tourApiRestClient: RestClient,
) : PetFriendlySyncPort {

    override fun fetchPage(pageNo: Int, numOfRows: Int): PetFriendlySyncPage {
        val uri = UriComponentsBuilder.fromUriString(properties.baseUrls.getValue(DomainLocale.KO.name))
            .path("/detailPetTour2")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")
            .queryParam("numOfRows", numOfRows)
            .queryParam("pageNo", pageNo)
            .build(true)
            .toUri()

        val response: TourApiPetTourResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapPage(response)
    }

    /** 응답 → 적재 레코드. HTTP·역직렬화와 분리해 단위 테스트 가능(다른 클라이언트와 같은 관례). */
    fun mapPage(response: TourApiPetTourResponse): PetFriendlySyncPage {
        val body = response.response.body
        val records = body.items.item.mapNotNull { it.toSyncPetFriendlyCommandOrNull() }

        return PetFriendlySyncPage(
            records = records,
            totalCount = body.totalCount,
            droppedCount = body.items.item.size - records.size,
        )
    }
}
