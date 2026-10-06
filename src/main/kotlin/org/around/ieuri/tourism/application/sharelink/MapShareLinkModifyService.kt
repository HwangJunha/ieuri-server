package org.around.ieuri.tourism.application.sharelink

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.shared.NationwideMapPolicy
import org.around.ieuri.tourism.application.shared.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.application.sharelink.provided.MapShareLinkCommandUseCase
import org.around.ieuri.tourism.application.sharelink.required.MapShareLinkRepository
import org.around.ieuri.tourism.domain.sharelink.ShareToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 전국 지도 공유 링크 발급·폐기(SPEC-0030 R12·R14). */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MapShareLinkModifyService(
    private val shareLinkRepository: MapShareLinkRepository,
    private val policy: NationwideMapPolicy,
) : MapShareLinkCommandUseCase {

    /** 발급된 토큰을 설정된 앞부분에 붙여 완성한다 — 클라이언트는 조립하지 않는다. */
    override fun createShareLink(memberId: Long): MapShareLinkView {
        val record = shareLinkRepository.findOrCreate(memberId, ShareToken.generate())
        return MapShareLinkView(shareUrl = "${policy.shareUrlBase}/${record.token}", createdAt = record.createdAt)
    }

    /** 발급 때 내보낸 URL에서 토큰을 꺼낸다. URL 형태는 검사하지 않는다 — 엉뚱한 값이면 폐기 대상을 못 찾아 false다. */
    override fun revokeShareLink(memberId: Long, shareUrl: String): Boolean = shareLinkRepository.revoke(memberId, ShareToken.extractFrom(shareUrl))

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long): Int = shareLinkRepository.deleteAll(memberId)
}
