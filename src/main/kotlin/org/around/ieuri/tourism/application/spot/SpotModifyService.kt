package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 상세 적재. 상세는 원본에 딸린 값 객체라 스스로 검증되며, 원본을 읽지 않고 갈래별로 쓴다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotModifyService(
    private val spotRepository: SpotRepository,
) : SpotCommandUseCase {

    override fun saveDetail(spotId: Long, detail: SpotDetail) = spotRepository.saveDetail(spotId, detail)

    override fun saveDetailAbsence(spotId: Long, absent: SpotDetail) = spotRepository.saveDetailAbsence(spotId, absent)

    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) = spotRepository.saveGallery(spotId, gallery)

    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) = spotRepository.saveIntro(spotId, category, intro)

    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) = spotRepository.saveInfoItems(spotId, category, items)
}
