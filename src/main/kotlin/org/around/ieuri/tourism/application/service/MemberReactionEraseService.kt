package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
import org.around.ieuri.tourism.application.port.out.persistence.MapRelationPort
import org.around.ieuri.tourism.application.port.out.persistence.MapShareLinkPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 좋아요/싫어요 삭제(SPEC-0011 R17).
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 삭제는 각자의 핸들러가 한다.
 */
@Service
class MemberReactionEraseService(
    private val spotReactionPort: SpotReactionPort,
    private val districtVisitStorePort: DistrictVisitStorePort,
    private val mapShareLinkPort: MapShareLinkPort,
    private val mapRelationPort: MapRelationPort,
) : MemberReactionEraseUseCase {

    /**
     * 탈퇴 회원의 좋아요/싫어요를 지운다.
     * - REQUIRES_NEW여야 한다 — `AFTER_COMMIT` 리스너가 부르므로, REQUIRED면 커밋된 트랜잭션에 합류해 삭제가 조용히 사라진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberReactions(memberId: Long) {
        val deleted = spotReactionPort.deleteByMember(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        log.info("tourism reactions erased for withdrawn member {}: reactions={}", memberId, deleted)
    }

    /**
     * 전국 지도 데이터 삭제(SPEC-0030 §9.1). 전파 규약은 위와 같다.
     * - 개인정보 성격이 짙은 방문 기록을 먼저 지운다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberMapData(memberId: Long) {
        val visits = districtVisitStorePort.removeAll(memberId)
        val links = mapShareLinkPort.deleteAll(memberId)
        // 관계는 양방향이라 상대 목록의 이 회원까지 지운다.
        val relations = mapRelationPort.deleteAllInvolving(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다. 시군구 코드는 남기지 않는다.
        log.info(
            "tourism map data erased for withdrawn member {}: visits={} shareLinks={} relations={}",
            memberId,
            visits,
            links,
            relations,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberReactionEraseService::class.java)
    }
}
