package org.around.ieuri.tourism.infrastructure.adapter.out.tourapi

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPage
import org.around.ieuri.tourism.application.port.out.client.FestivalSyncPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FestivalPeriod
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
 *  - 구필드(`areaCode`·`sigunguCode`·`cat1~3`)를 요청 필터로 쓰면 **에러 없이 0건**을 반환한다.
 *    그래서 지역·카테고리 필터를 아예 보내지 않는다.
 *  - `arrange`는 `spot` 적재와 **같은 값**을 쓴다. 대표이미지 필수 조건이 어긋나면 부모 `spot` 행이
 *    없는 축제만 잔뜩 나와 전부 건너뛰어진다(R-2).
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
        val records = body.items.item.mapNotNull { it.toRecord(language, asOf) }

        return FestivalSyncPage(
            records = records,
            totalCount = body.totalCount,
            // 날짜 해석 실패로 드랍된 건수를 적재 건수와 구분해 남긴다(AC-41). [TBD-2]는 이 값으로만
            // 해소된다 — 드랍이 유의미한 규모면 R-4의 정책 자체를 재검토해야 한다.
            droppedCount = body.items.item.size - records.size,
        )
    }

    /**
     * 한 항목의 매핑. **드랍은 예외가 아니라 null 반환이다** — 한 건의 이상 데이터가 페이지 전체를
     * 실패시킬 이유가 없다([TourApiSyncClient]의 좌표 처리와 같은 판단).
     */
    private fun TourApiFestivalResponse.Item.toRecord(
        language: DomainLocale,
        asOf: LocalDate,
    ): SyncFestivalCommand? {
        val contentId = contentid?.takeIf { it.isNotBlank() } ?: return null       // AC-4
        val start = parseDate(eventstartdate) ?: return null                       // AC-13
        val end = parseDate(eventenddate) ?: return null                           // AC-14
        // AC-15. 도메인이 던지는 예외를 항목 드랍으로 흡수한다.
        val period = runCatching { FestivalPeriod(start = start, end = end) }.getOrNull() ?: return null
        if (period.isEndedAsOf(asOf)) return null                                  // AC-9

        return SyncFestivalCommand(
            contentId = contentId,
            language = language,
            period = period,
            // 해석하지 않는다(R-5·AC-17·AC-19). 공백만 null로 정규화한다(AC-18).
            progressType = progresstype?.takeIf { it.isNotBlank() },
            festivalType = festivaltype?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * `YYYYMMDD` 파싱. **엄격하게 해석한다** — `2026-08-10` 같은 다른 형식이나 존재하지 않는 날짜는
     * null이 되어 항목이 드랍된다(AC-14·R-4). 관대하게 보정하면 잘못 해석된 종료일로 정리 판정이
     * 조용히 틀린다.
     */
    private fun parseDate(raw: String?): LocalDate? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalDate.parse(trimmed, YYYYMMDD) }.getOrNull()
    }

    private companion object {
        val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
