package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.required.SpotQueryPort
import org.around.ieuri.tourism.application.catalog.required.dto.SpotIdentity
import org.around.ieuri.tourism.application.category.provided.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.spot.provided.SpotDetailReadUseCase
import org.around.ieuri.tourism.application.spot.provided.dto.KnownIntro
import org.around.ieuri.tourism.application.spot.provided.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.spot.provided.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.spot.required.SpotDetailQueryPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 상세 조회의 DB 단계 구현. 캐시·업스트림은 모른다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotDetailReadService(
    private val spotQueryPort: SpotQueryPort,
    private val detailQueryPort: SpotDetailQueryPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
) : SpotDetailReadUseCase {

    override fun resolve(catalogId: Long, locale: DomainLocale): SpotIdentity = spotQueryPort.findIdentity(catalogId, locale) ?: throw absenceOf(catalogId)

    override fun requireKnownCategory(category: SpotCategory) {
        if (!appCategoryQueryPort.exists(category.code)) {
            throw UnknownCategoryException("알 수 없는 카테고리입니다: ${category.code}")
        }
    }

    override fun storedDetail(spotId: Long): SpotDetail? = detailQueryPort.findDetail(spotId)

    override fun storedGallery(spotId: Long): List<SpotImage>? = detailQueryPort.findGallery(spotId)

    override fun storedIntro(spotId: Long, category: SpotCategory): KnownIntro? = detailQueryPort.findIntro(spotId, category)

    override fun storedInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>? = detailQueryPort.findInfoItems(spotId, category)

    override fun absentDetail(catalogId: Long, locale: DomainLocale): SpotDetail {
        // findSpotBasics는 카탈로그 id로 묻는다.
        val basics = spotQueryPort.findSpotBasics(catalogId, locale) ?: throw absenceOf(catalogId)
        return SpotDetail.absent(basics.title, basics.category, basics.thumbnailImageUrl, basics.address, basics.copyright)
    }

    /** 해소 실패 예외. 카탈로그가 있으면 "이 언어에 없음", 없으면 "그런 장소 없음". */
    private fun absenceOf(catalogId: Long): SpotQueryException = if (spotQueryPort.existsCatalog(catalogId)) {
        SpotNotFoundInLocaleQueryException()
    } else {
        SpotDetailNotFoundException()
    }
}
