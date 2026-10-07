package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail

/**
 * 확보할 원본 1건. [language]가 어느 언어 서비스를 부를지 정한다(AC-004).
 * - [title]·[thumbnailImageUrl]·[copyright]·[address]는 본문 부재 행의 NOT NULL 컬럼을 채운다(SPEC-0008 R-2).
 */
data class PrefetchTarget(
    val spotId: Long,
    val contentId: String,
    val language: DomainLocale,
    val category: SpotCategory,
    val title: String,
    val thumbnailImageUrl: String,
    val copyright: CopyrightType,
    val address: String?,
) {
    /** 상류에 본문이 없을 때 남길 상세. */
    fun absentDetail(): SpotDetail = SpotDetail.absent(title, category, thumbnailImageUrl, address, copyright)
}
