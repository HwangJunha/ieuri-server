package org.around.ieuri.tourism.infrastructure.config

import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * TourAPI 접근 설정. **serviceKey는 4개 언어 공용 단일 키**(실측 B-4) — 언어는 base-url 선택에만 영향.
 * 언어→(도메인 enum→contentTypeId) 매핑을 설정으로 두어 코드 수정 없이 값 보정이 가능하게 한다.
 *
 * serviceKey는 환경변수로만 주입한다(응답·로그·GraphQL 어디에도 노출 금지).
 *
 * @param serviceKey     TourAPI 서비스 키(환경변수). 사전 인코딩 상태로 보관될 수 있음(§4.8 함정 1 참조).
 * @param baseUrls       언어(KO/EN/JA/ZH)→base-url.
 * @param categoryCodes  코드셋 이름(KO/FOREIGN)→(도메인 카테고리→contentTypeId).
 * @param excludeLclssystm2 비관광 제외 lclsSystm2 코드 목록(다국어 공통 적용).
 * @param arrange        정렬/필터 코드. Q=이미지 보유 항목만 + 수정일순(실측 확정).
 * @param mobileOs       MobileOS 파라미터.
 * @param mobileApp      MobileApp 파라미터.
 * @param maxRadiusMeters 서버 UX/성능 반경 상한(초과 시 RADIUS_TOO_LARGE).
 * @param maxPageSize    `spots` size 상한(POL-018 잠정 100). 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` first 상한. maxPageSize와 값은 같지만 다른 계약이라 분리한다.
 * @param detailAbsenceTtl 상세 본문 부재 행의 신선도 기준(SPEC-0008 [TBD-1] = 7일). 정상 행에는
 *   만료가 없고 부재 행에만 적용된다 — 업스트림에 나중에 데이터가 생길 수 있기 때문이다.
 * @param connectTimeoutMillis / readTimeoutMillis  HTTP 타임아웃.
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
