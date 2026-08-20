package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi

import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPage
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper.toSyncFestivalCommandOrNull
import org.around.ieuri.tourism.infrastructure.config.TourApiProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.requiredBody
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 축제 수집용 TourAPI 어댑터(searchFestival2). [TourApiSyncClient]와 같은 규율을 따른다 —
 * TourAPI 필드명·날짜 형식을 여기 가두고 밖으로는 도메인 언어([FestivalPeriod])로만 내보낸다.
 *
 * **기록된 함정 두 가지를 코드로 지킨다**(스펙 §기록된 함정):
 *  - 구필드(`areaCode`·`districtCode`·`cat1~3`)를 요청 필터로 쓰면 **에러 없이 0건**을 반환한다.
 *    그래서 지역·카테고리 필터를 아예 보내지 않는다.
 *  - `arrange`는 `spot` 적재와 **같은 값**을 쓴다. 대표이미지 필수 조건이 어긋나면 부모 `spot` 행이
 *    없는 축제만 잔뜩 나와 전부 건너뛰어진다(R-2).
 *
 * `showflag`도 같은 이유로 `spot` 적재와 같은 값을 쓴다 — 표출이 중단된 항목은 부모 `spot` 행이
 * 적재되지 않으므로, 여기서만 넓게 가져오면 부모 없는 축제가 쌓인다(SPEC-0029 R-2).
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
            .queryParam("showflag", properties.showflag)
            // 필수 파라미터. "그 날짜에 시작하는 행사"가 아니라 **"그 날짜 기준 유효한 행사"** 를
            // 뜻하므로, 실행일을 넣는 것만으로 별도의 기간 윈도우 설계가 필요 없다(AC-8).
            .queryParam("eventStartDate", eventStartDate.format(YYYYMMDD))
            .build(true)
            .toUri()

        val response: TourApiFestivalResponse = tourApiRestClient.get().uri(uri).retrieve().requiredBody()
        return mapPage(response, language, eventStartDate)
    }

    /**
     * 응답 → 적재 레코드. HTTP·역직렬화와 분리해 단위 테스트 가능하게 둔다
     * ([TourApiSyncClient.mapPage]와 같은 관례).
     *
     * [asOf]는 종료 판정 기준일이다. 업스트림이 유효한 것만 준다는 보장에만 기대지 않고 여기서도
     * 한 번 더 거른다(AC-9) — `eventStartDate` 파라미터의 의미가 문서와 실제로 어긋나면 끝난 행사가
     * 적재되고, 그것을 같은 실행의 정리 단계가 다시 지우는 왕복이 생긴다.
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
            // 날짜 해석 실패로 드랍된 건수를 적재 건수와 구분해 남긴다(AC-41). [TBD-2]는 이 값으로만
            // 해소된다 — 드랍이 유의미한 규모면 R-4의 정책 자체를 재검토해야 한다.
            droppedCount = body.items.item.size - records.size,
        )
    }

    private companion object {
        val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
