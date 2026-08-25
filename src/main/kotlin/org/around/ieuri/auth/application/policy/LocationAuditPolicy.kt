package org.around.ieuri.auth.application.policy

/**
 * 위치 조회 감사 설정(SPEC-0032 R21). 인프라 @ConfigurationProperties에서 뽑아 온
 * 프레임워크 무의존 데이터로, 서비스가 이것만 주입받는다.
 *
 * @param enabled 꺼져 있으면 기록을 만들지 않는다. 위치정보사업 신고 완료 전까지 기본값은 꺼짐이다.
 */
data class LocationAuditPolicy(
    val enabled: Boolean,
)
