package org.around.ieuri.tourism.application.footprint.provided

import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RecordVisitsResult
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.footprint.provided.dto.RemoveVisitResult
import org.around.ieuri.tourism.application.shared.provided.dto.MapShareLinkView
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 발자취 쓰기 — 방문 기록과 공유 링크(SPEC-0030 R3·R4·R12·R14). */
interface FootprintCommandUseCase {

    /**
     * 시군구를 방문으로 기록하고 갱신된 칸을 돌려준다(R3).
     * - 이미 기록된 코드는 중복되지 않으며 오류도 아니다.
     */
    fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult

    /** 칸의 방문을 해제한다(R4). 그 칸에 속한 시군구 기록이 전부 지워진다. */
    fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult

    /** 공유 링크를 발급한다(R12). 이미 유효한 링크가 있으면 **같은 토큰**을 돌려준다. */
    fun createShareLink(memberId: Long): MapShareLinkView

    /**
     * 공유 링크를 폐기한다(R14). 발급 때 받은 URL을 그대로 넘긴다.
     * @return 실제로 폐기했으면 true. 본인 것이 아니거나 이미 폐기됐으면 false다.
     */
    fun revokeShareLink(memberId: Long, shareUrl: String): Boolean

    /** 탈퇴 정리 — 방문 기록과 공유 링크를 지운다(폐기가 아니라 삭제). 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
