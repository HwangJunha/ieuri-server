package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.catalog.spec.TranslationVisible

/**
 * 모든 번역의 노출 여부를 [rule]에 맞추는 일괄 명령(Specification + Command — Khorikov, *DDD and bulk operations*).
 * - 번역 8만여 행을 카탈로그 단위로 읽어 맞추는 대신, 저장소가 [rule]을 한 문장의 SQL로 옮겨 실행한다.
 * - 규칙의 정의는 [rule] 하나다. 저장소는 그 정의를 SQL로 번역할 뿐 따로 규칙을 적지 않는다.
 */
data class TranslationVisibilitySweep(val rule: TranslationVisible = TranslationVisible)
