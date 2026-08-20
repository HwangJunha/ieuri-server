package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.around.ieuri.tourism.application.port.out.persistence.DistrictVisitStorePort
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
) : MemberReactionEraseUseCase {

    /**
     * **REQUIRES_NEW여야 한다.** 이 유스케이스는 `AFTER_COMMIT` 리스너가 부르는데, 그 시점의
     * 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 **묶여 있다.** 기본 전파(REQUIRED)로 두면
     * 커밋할 수 없는 그 트랜잭션에 조용히 합류해 삭제가 흘러가지 않는다 —
     * 예외도 나지 않아 성공한 것처럼 보인다(실제로 탈퇴 후 글·댓글이 남았다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberReactions(memberId: Long) {
        val deleted = spotReactionPort.deleteByMember(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        log.info("tourism reactions erased for withdrawn member {}: reactions={}", memberId, deleted)
    }

    /**
     * 전국 지도 데이터 삭제(SPEC-0030 §9.1). 전파 규약은 위와 같다.
     *
     * **방문 기록을 먼저 지운다.** 순서가 결과를 바꾸지는 않지만, 둘 중 개인정보 성격이 짙은
     * 쪽(어디를 다녔는가)을 먼저 없앤다 — 중간에 실패해도 남는 것이 링크 쪽이 되게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberMapData(memberId: Long) {
        val visits = districtVisitStorePort.removeAll(memberId)
        val links = mapShareLinkPort.deleteAll(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다. 시군구 코드는 남기지 않는다.
        log.info("tourism map data erased for withdrawn member {}: visits={} shareLinks={}", memberId, visits, links)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberReactionEraseService::class.java)
    }
}
