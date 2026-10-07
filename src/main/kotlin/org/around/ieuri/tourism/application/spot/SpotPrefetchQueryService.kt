package org.around.ieuri.tourism.application.spot

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.spot.provided.SpotPrefetchQueryUseCase
import org.around.ieuri.tourism.application.spot.provided.dto.PrefetchTarget
import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailPart
import org.around.ieuri.tourism.application.spot.required.SpotDetailQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class SpotPrefetchQueryService(
    private val detailQueryPort: SpotDetailQueryPort,
) : SpotPrefetchQueryUseCase {

    override fun findTargets(part: SpotDetailPart, afterSpotId: Long, limit: Int, staleBefore: Instant): List<PrefetchTarget> = detailQueryPort.findPrefetchTargets(part, afterSpotId, limit, staleBefore)
}
