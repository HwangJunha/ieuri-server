package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.command.RecordVisitsCommand
import org.around.ieuri.tourism.application.command.RecordVisitsResult
import org.around.ieuri.tourism.application.command.RemoveVisitCommand
import org.around.ieuri.tourism.application.command.RemoveVisitResult
import org.around.ieuri.tourism.application.query.MapShareLinkView
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 전국 지도 쓰기 use case(SPEC-0030 R3·R4·R12·R14). */
interface NationwideMapCommandUseCase {

    /**
     * 시군구를 방문으로 기록한다(R3). 이미 기록된 코드는 중복되지 않으며 오류도 아니다.
     *
     * 갱신된 칸을 돌려줘 클라이언트가 재조회 없이 지도를 고칠 수 있게 한다(계약 §7.1).
     */
    fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult

    /** 칸의 방문을 해제한다(R4). 그 칸에 속한 시군구 기록이 전부 지워진다. */
    fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult

    /** 공유 링크를 발급한다(R12). 이미 유효한 링크가 있으면 **같은 토큰**을 돌려준다. */
    fun createShareLink(memberId: Long): MapShareLinkView

    /**
     * 공유 링크를 폐기한다(R14). 발급 때 받은 URL을 그대로 넘긴다 — 토큰 추출은 서버가 한다.
     *
     * @return 실제로 폐기했으면 true. 본인 것이 아니거나 이미 폐기됐으면 false다.
     */
    fun revokeShareLink(memberId: Long, shareUrl: String): Boolean
}
