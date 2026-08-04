package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * TourAPI 접근 설정. serviceKey는 4개 언어 공용 단일 키이며 **환경변수로만 주입한다**
 * (응답·로그·GraphQL 어디에도 노출 금지). 사전 인코딩된 상태로 보관될 수 있다.
 *
 * @param baseUrls       언어(KO/EN/JA/ZH)→base-url.
 * @param categoryCodes  코드셋 이름(KO/FOREIGN)→(도메인 카테고리→contentTypeId).
 * @param excludeLclssystm2 비관광 제외 lclsSystm2 코드 목록(다국어 공통 적용).
 * @param arrange        정렬/필터 코드. Q=이미지 보유 항목만 + 수정일순.
 * @param maxRadiusMeters 반경 상한. 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize    `spots` size 상한. 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` first 상한.
 * @param detailAbsenceTtl 상세 본문 부재 행의 신선도 기준. 부재 행에만 적용되고 정상 행에는 만료가 없다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi")
data class TourApiProperties(
    val serviceKey: String = "",
    val baseUrls: Map<String, String> = emptyMap(),
    val categoryCodes: Map<String, Map<SpotCategory, Int>> = emptyMap(),
    val excludeLclssystm2: List<String> = emptyList(),
    val arrange: String = "Q",
    val mobileOs: String = "ETC",
    val mobileApp: String = "ieuri",
    val maxRadiusMeters: Int = 100_000,
    val maxPageSize: Int = 100,
    val maxLikedPageSize: Int = 100,
    val detailAbsenceTtl: Duration = Duration.ofDays(7),
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 30_000,
) {
    companion object {
        const val CODESET_KO = "KO"
        const val CODESET_FOREIGN = "FOREIGN"
    }
}
