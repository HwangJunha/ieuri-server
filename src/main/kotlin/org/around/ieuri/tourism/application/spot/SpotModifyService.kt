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

/**
 * 상세 적재. 원본을 읽고, 원본이 갈래별 자식을 만들고, 그 자식을 쓴다.
 * - 원본이 없으면(조회와 적재 사이에 수집 정리가 지웠다) [SpotOriginNotFoundException]이다.
 * - 단, 빈 갤러리·빈 이용안내는 원본이 없어도 조용히 끝난다 — 쓸 행이 없어 예전에도 실패하지 않았다(D-123).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotModifyService(
    private val spotRepository: SpotRepository,
) : SpotCommandUseCase {

    override fun saveDetail(spotId: Long, detail: SpotDetail) = spotRepository.saveBody(find(spotId).describe(detail))

    override fun saveDetailAbsence(spotId: Long, absent: SpotDetail) = spotRepository.saveBody(find(spotId).describeAbsence(absent))

    override fun saveGallery(spotId: Long, gallery: List<SpotImage>) {
        val spot = findOrNull(spotId) ?: return requireNothingToWrite(spotId, gallery)
        spotRepository.saveGallery(spot.showcase(gallery))
    }

    override fun saveIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?) = spotRepository.saveIntro(find(spotId).introduce(category, intro))

    override fun saveInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>) {
        val spot = findOrNull(spotId) ?: return requireNothingToWrite(spotId, items)
        spotRepository.saveInfoItems(spot.inform(category, items))
    }

    private fun find(spotId: Long): Spot = findOrNull(spotId) ?: throw SpotOriginNotFoundException(spotId)

    private fun findOrNull(spotId: Long): Spot? = spotRepository.findById(spotId)

    /** 원본이 없을 때 전량 교체할 목록이 비었으면 아무것도 하지 않고, 남았으면 [SpotOriginNotFoundException]이다. */
    private fun requireNothingToWrite(spotId: Long, items: List<*>) {
        if (items.isNotEmpty()) throw SpotOriginNotFoundException(spotId)
    }
}
