package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.out.client.SpotLikeQueryPort
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.springframework.stereotype.Component

/**
 * 작성 자격 판정(SPEC-0026 R3~R5). `tourism`의 **인바운드 포트**를 통해서만 접근한다 —
 * [SpotLookupAdapter]와 같은 근거로, 도메인 내부(애그리게이트·영속 어댑터)를 직접 끌어다
 * 쓰지 않아야 tourism이 커뮤니티를 계속 모른 채로 있을 수 있다.
 *
 * community의 application·domain은 SpotLikeQueryPort만 알고 tourism이라는 이름조차 모른다.
 *
 * 반응 코드를 문자열로 받아 여기서 판정한다 — tourism의 `ReactionType`을 community가
 * import하면 도메인을 따로 떼어낼 수 없게 된다.
 */
@Component
class SpotLikeQueryAdapter(
    private val spotQueryUseCase: SpotQueryUseCase,
) : SpotLikeQueryPort {

    /** 반응 없음(null)과 DISLIKE는 **둘 다 false다**(R5) — 자격은 LIKE에서만 성립한다. */
    override fun likedByMember(catalogId: Long, memberId: Long): Boolean =
        spotQueryUseCase.spotViewerReaction(catalogId, memberId) == LIKE

    private companion object {
        /** tourism의 `ReactionType.LIKE.name`과 같은 값. 열거형을 import하지 않으려고 문자열로 둔다. */
        private const val LIKE = "LIKE"
    }
}
