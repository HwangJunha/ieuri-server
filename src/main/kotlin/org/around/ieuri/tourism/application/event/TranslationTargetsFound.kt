package org.around.ieuri.tourism.application.event

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 번역이 필요한 카탈로그를 찾았다(SPEC-0019 R-4).
 *
 * **tourism은 이 사실만 알린다.** 어디로 어떻게 보낼지는 구독자가 정하며, 그래서 이 도메인이
 * 메일·SMTP·수신자를 알지 않는다.
 *
 * [targets]가 비어 있으면 발행하지 않는다 — 구독자가 빈 알림을 거르지 않아도 되게 한다.
 */
data class TranslationTargetsFound(
    val targets: List<TranslationTarget>,
)

/**
 * 번역 대상 하나. [koreanTitle]·[koreanAddress]가 번역 원문이고, [missingLanguages]가
 * 채워야 할 언어다.
 *
 * 원문에 주소가 없는 spot이 실재해 [koreanAddress]는 nullable이다.
 */
data class TranslationTarget(
    val catalogId: Long,
    val koreanTitle: String,
    val koreanAddress: String?,
    val missingLanguages: Set<DomainLocale>,
)
