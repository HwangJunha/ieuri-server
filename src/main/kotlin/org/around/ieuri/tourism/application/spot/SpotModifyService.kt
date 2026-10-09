package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.around.ieuri.tourism.application.spot.provided.exception.SpotOriginNotFoundException
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 상세 적재. 원본을 읽고, 원본이 갈래별 자식을 만들고, 그 자식을 쓴다. 원본이 없으면 [SpotOriginNotFoundException]. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotModifyService(
    private val spotRepository: SpotRepository,
) : SpotCommandUseCase {

    override fun saveDetail(spotId: Long, detail: SpotDetail) = spotRepository.saveBody(find(spotId).describe(detail))

    override fun saveDetailAbsence(spotId: Long, absent: SpotDetail) = spotRepository.saveBody(find(spotId).describeAbsence(absent))

    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) = spotRepository.saveGallery(find(spotId).showcase(gallery))

    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) = spotRepository.saveIntro(find(spotId).introduce(category, intro))

    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) = spotRepository.saveInfoItems(find(spotId).inform(category, items))

    private fun find(spotId: Long): Spot = spotRepository.findById(spotId) ?: throw SpotOriginNotFoundException(spotId)
}
