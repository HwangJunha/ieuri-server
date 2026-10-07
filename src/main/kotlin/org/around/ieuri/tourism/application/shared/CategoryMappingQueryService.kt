package org.around.ieuri.tourism.application.shared

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.provided.CategoryMappingQueryUseCase
import org.around.ieuri.tourism.application.shared.required.CategoryExternalMappingPort
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class CategoryMappingQueryService(
    private val mappingPort: CategoryExternalMappingPort,
) : CategoryMappingQueryUseCase {

    override fun externalCode(category: SpotCategory, provider: String, codeset: String): String? = mappingPort.externalCode(category, provider, codeset)

    override fun categoryOf(externalCode: String, provider: String, codeset: String): SpotCategory? = mappingPort.categoryOf(externalCode, provider, codeset)
}
