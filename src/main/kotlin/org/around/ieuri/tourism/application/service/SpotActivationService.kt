package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.SpotActivationUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotActivationRow
import org.around.ieuri.tourism.application.command.SpotActivationResult
import org.around.ieuri.tourism.domain.specification.SpotExclusionRule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 비관광 spot 판정 적용(SPEC-0018 R8~R12·R19).
 *
 * **매 실행이 전량을 양방향으로 판정한다.** 제외 대상이 아닌 행은 이전에 비활성이었더라도
 * 활성으로 되돌린다 — 오탐 교정이 키워드 설정 변경만으로 이루어지려면 이 방향이 있어야 한다.
 * 그래서 실행 회차를 입력으로 받지 않으며, 첫 실행과 백 번째 실행이 같다.
 */
@Service
class SpotActivationService(
    private val activationPort: SpotActivationPort,
    private val exclusionRule: SpotExclusionRule,
) : SpotActivationUseCase {

    /**
     * 전 행을 판정해 반영하고 **실제로 상태가 바뀐 건수**를 돌려준다.
     *
     * 적재와 별도 트랜잭션이다 — 판정이 실패해도 그 실행의 적재는 남고, 판정은 멱등하므로
     * 다음 실행이 다시 시도하면 같은 결과가 된다.
     */
    @Transactional
    override fun applyExclusions(): SpotActivationResult {
        val rows = activationPort.findAllForJudgement()

        // 1단계: 행 단위 판정. 2단계: 하나라도 걸린 그룹은 전체를 비활성으로 전파한다(R8).
        //
        // 전파가 필요한 이유는 업스트림의 다국어 표기가 계약이 아니기 때문이다. 지금은 ja·zh
        // 제목이 한글명을 병기해 한/영 키워드만으로 전 언어가 걸리지만, 그 관행이 바뀌어도
        // 같은 장소가 언어에 따라 보였다 안 보였다 해서는 안 된다.
        val excludedGroups: Set<String> = rows
            .filter { exclusionRule.isExcluded(it.title, it.category) }
            .mapTo(HashSet(), SpotActivationRow::groupKey)

        // 제외 집합만 넘긴다 — 활성 대상은 그 여집합이라 포트가 `NOT IN`으로 표현한다.
        // 두 방향은 서로 겹치지 않는 집합에 걸리므로 순서가 결과를 바꾸지 않는다.
        val excludedIds = rows
            .filter { it.groupKey in excludedGroups }
            .map(SpotActivationRow::spotId)

        val counts = activationPort.applyActivation(excludedIds)

        return SpotActivationResult(
            judged = rows.size,
            deactivated = counts.deactivated,
            reactivated = counts.reactivated,
        )
    }
}
