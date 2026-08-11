package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand

/**
 * 반려동물 동반 가능 관광지 적재 포트(SPEC-0015 · V48). **쓰기 전용**이다.
 */
interface PetFriendlyStorePort {

    /**
     * 한 페이지 분량을 upsert한다. PK `content_id` 충돌 시 전체 컬럼을 덮어쓴다.
     *
     * **사라진 항목을 지우는 정리 단계가 없다** — 반려동물 목록에서 빠진 건은 다음 전량 적재
     * 때까지 남는다. spot 동기화와 달리 이 데이터는 개수 집계에만 쓰여 몇 건의 잔류가 추천
     * 결과를 뒤집지 않고, 정리에는 대량 오삭제 방어(급감 게이트)가 따라붙어야 하기 때문이다.
     *
     * @return 실제로 반영된 행 수.
     */
    fun upsertAll(records: List<SyncPetFriendlyCommand>): Int
}
