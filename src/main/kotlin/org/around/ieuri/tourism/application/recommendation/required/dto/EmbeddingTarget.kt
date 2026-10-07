package org.around.ieuri.tourism.application.recommendation.required.dto

import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 임베딩할 번역 행 1건. [categoryCode]는 라벨로 바꿔 텍스트에 넣는다. [overview]는 상세가 있을 때만.
 * - [sourceHash]는 제목·분류·주소·소개문의 지문이다. 저장한 지문과 달라지면 다시 대상이 된다.
 */
data class EmbeddingTarget(
    val translationId: Long,
    val sourceHash: String,
    val language: DomainLocale,
    val title: String,
    val categoryCode: String,
    val address: String?,
    val overview: String?,
)
