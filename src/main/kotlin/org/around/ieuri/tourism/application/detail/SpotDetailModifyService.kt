package org.around.ieuri.tourism.application.detail

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.detail.provided.SpotDetailCommandUseCase
import org.around.ieuri.tourism.application.detail.required.SpotDetailRepository
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 상세 DB 캐시 적재. 갈래마다 한 트랜잭션이다 — 한 갈래의 실패가 다른 갈래 적재를 막지 않는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotDetailModifyService(
    private val detailRepository: SpotDetailRepository,
) : SpotDetailCommandUseCase {

    override fun saveDetail(spotId: Long, detail: SpotDetail) = detailRepository.saveDetail(spotId, detail)

    override fun saveDetailAbsence(spotId: Long, fallback: SpotDetail) = detailRepository.saveDetailAbsence(spotId, fallback)

    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) = detailRepository.saveGallery(spotId, gallery)

    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) = detailRepository.saveIntro(spotId, category, intro)

    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) = detailRepository.saveInfoItems(spotId, category, items)
}
