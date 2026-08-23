package org.around.ieuri.tourism.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QAppCategoryEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QCategoryEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QCategoryTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.CategoryJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카테고리 구성·표시 정보 조회(SPEC-0020). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 *
 * 라벨은 **좌측 조인**이다 — 번역이 없는 카테고리도 목록에서 빠지지 않고 코드가 라벨이 된다.
 * 안 그러면 번역을 빠뜨린 신규 카테고리가 조용히 사라져, 데이터만 넣으면 된다는 전제가 깨진다.
 */
@Component
class AppCategoryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val categoryRepository: CategoryJpaRepository,
) : AppCategoryQueryPort {

    private val appCategory = QAppCategoryEntity.appCategoryEntity
    private val category = QCategoryEntity.categoryEntity
    private val translation = QCategoryTranslationEntity.categoryTranslationEntity

    /**
     * 이 앱·이 언어에 켜진 카테고리. **순서를 정하지 않는다**(V59) — 서버는 무엇을 보일지만
     * 정하고 배열 순서는 클라이언트가 정한다. 재현 가능하도록 코드 오름차순으로만 고정한다.
     */
    @Transactional(readOnly = true)
    override fun findCategories(appKey: AppKey, language: DomainLocale): List<AppCategoryView> =
        queryFactory
            .select(category.code, translation.label, category.iconUrl)
            .from(appCategory)
            .join(category).on(category.id.eq(appCategory.categoryId))
            .leftJoin(translation)
            .on(translation.categoryId.eq(category.id), translation.language.eq(language))
            .where(
                appCategory.appKey.eq(appKey.value),
                appCategory.language.eq(language),
                appCategory.active.isTrue,
            )
            .orderBy(category.code.asc())
            .fetch()
            .map {
                val code = requireNotNull(it.get(category.code))
                AppCategoryView(
                    category = SpotCategory(code),
                    // 번역이 없으면 코드가 라벨이다 — 빈 칩보다 낫다.
                    label = it.get(translation.label) ?: code,
                    iconUrl = it.get(category.iconUrl),
                )
            }

    /**
     * 코드별 표시 정보를 한 번에. 정의가 없는 코드는 결과에 키가 없다 — 호출자가 폴백을 택한다.
     */
    @Transactional(readOnly = true)
    override fun findDisplays(codes: Set<String>, language: DomainLocale): Map<String, CategoryDisplay> {
        if (codes.isEmpty()) return emptyMap()
        return queryFactory
            .select(category.code, translation.label, category.iconUrl)
            .from(category)
            .leftJoin(translation)
            .on(translation.categoryId.eq(category.id), translation.language.eq(language))
            .where(category.code.`in`(codes))
            .fetch()
            .associate {
                val code = requireNotNull(it.get(category.code))
                code to CategoryDisplay(
                    category = SpotCategory(code),
                    label = it.get(translation.label) ?: code,
                    iconUrl = it.get(category.iconUrl),
                )
            }
    }

    @Transactional(readOnly = true)
    override fun exists(code: String): Boolean = categoryRepository.existsByCode(code)
}
