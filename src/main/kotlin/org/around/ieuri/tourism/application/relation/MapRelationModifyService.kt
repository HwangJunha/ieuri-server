package org.around.ieuri.tourism.application.relation

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.footprint.provided.FootprintQueryUseCase
import org.around.ieuri.tourism.application.footprint.provided.exception.ShareLinkBlockedException
import org.around.ieuri.tourism.application.footprint.provided.exception.ShareLinkNotFoundException
import org.around.ieuri.tourism.application.footprint.required.MapViewerBlockPort
import org.around.ieuri.tourism.application.relation.provided.MapRelationCommandUseCase
import org.around.ieuri.tourism.application.relation.required.MapRelationRepository
import org.around.ieuri.tourism.domain.footprint.ShareToken
import org.around.ieuri.tourism.domain.relation.MapRelation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 지도 관계 맺기·끊기(SPEC-0030 확장). 공유 링크 위에 양방향 관계를 얹는다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MapRelationModifyService(
    private val relationRepository: MapRelationRepository,
    private val footprintQueryUseCase: FootprintQueryUseCase,
    private val mapViewerBlockPort: MapViewerBlockPort,
) : MapRelationCommandUseCase {

    /** 판정 순서: 링크 유효 → 자기 자신 아님(도메인) → 차단 아님. 차단은 등록 시점에만 본다(§5). */
    override fun createRelation(memberId: Long, shareUrl: String): Long {
        val sharerId = footprintQueryUseCase.findSharerId(ShareToken.extractFrom(shareUrl)) ?: throw ShareLinkNotFoundException()

        val relation = MapRelation(ownerId = memberId, counterpartId = sharerId)
        if (mapViewerBlockPort.isBlockedBetween(memberId, sharerId)) throw ShareLinkBlockedException()

        relationRepository.relate(relation)
        return sharerId
    }

    override fun removeRelation(memberId: Long, counterpartId: Long): Boolean = relationRepository.removeFromMyList(memberId, counterpartId)

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = relationRepository.deleteAllInvolving(memberId)
}
