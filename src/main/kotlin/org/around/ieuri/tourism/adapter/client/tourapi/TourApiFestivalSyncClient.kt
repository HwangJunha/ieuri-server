package org.around.ieuri.tourism.adapter.client.tourapi

import org.around.ieuri.tourism.adapter.client.tourapi.mapper.toSyncFestivalCommandOrNull
import org.around.ieuri.tourism.adapter.config.TourApiProperties
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPage
import org.around.ieuri.tourism.application.spot.required.FestivalSyncPort
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 축제 수집용 TourAPI 어댑터(searchFestival2). TourAPI 필드명·날짜 형식을 여기 가둔다.
 * - 구필드(`areaCode`·`cat1~3` 등) 필터와 `showflag`를 보내지 않는다 — 에러 없이 0건이 온다.
 * - `arrange`는 `spot` 적재와 같은 값이어야 한다 — 어긋나면 부모 `spot`이 없는 축제만 나온다(R-2).
 */
@Component
class TourApiFestivalSyncClient(
    private val properties: TourApiProperties,
    private val tourApiRestClient: RestClient,
) : FestivalSyncPort {

    override fun fetchPage(
        language: DomainLocale,
        eventStartDate: LocalDate,
        pageNo: Int,
        numOfRows: Int,
    ): FestivalSyncPage {
        val uri = UriComponentsBuilder.fromUriString(properties.baseUrls.getValue(language.name))
            .path("/searchFestival2")
            .queryParam("serviceKey", properties.serviceKey)
            .queryParam("MobileOS", properties.mobileOs)
            .queryParam("MobileApp", properties.mobileApp)
            .queryParam("_type", "json")
            .queryParam("numOfRows", numOfRows)
            .queryParam("pageNo", pageNo)
            .queryParam("arrange", properties.arrange)
            // 필수 파라미터. "그 날짜 기준 유효한 행사"를 뜻한다(AC-8).
            .queryParam("eventStartDate", eventStartDate.format(YYYYMMDD))
            .build(true)
            .toUri()

        val response: TourApiFestivalResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapPage(response, language, eventStartDate)
    }

    /**
     * 응답 → 적재 레코드. HTTP·역직렬화와 분리해 단위 테스트 가능하게 둔다.
     * - [asOf] 기준 종료된 행사는 업스트림과 별개로 여기서도 거른다(AC-9).
     */
    fun mapPage(
        response: TourApiFestivalResponse,
        language: DomainLocale,
        asOf: LocalDate,
    ): FestivalSyncPage {
        val body = response.response.body
        val records = body.items.item.mapNotNull { it.toSyncFestivalCommandOrNull(language, asOf) }

        return FestivalSyncPage(
            records = records,
            totalCount = body.totalCount,
            // 날짜 해석 실패로 드랍된 건수(AC-41).
            droppedCount = body.items.item.size - records.size,
        )
    }

    private companion object {
        val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
