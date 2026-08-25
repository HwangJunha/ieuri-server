package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellImageView
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 전국 지도 칸의 배치 로더(SPEC-0032 R25).
 *
 * community의 13종과 같은 형태를 쓴다 — `MappedBatchLoader`, 키는 `Long`,
 * `companion object { const val NAME }`, 동기 완료. tourism에 로더가 없어 같은 문제를 다른
 * 방식으로 풀고 있던 것을 하나로 맞춘다.
 */

/**
 * 칸 대표 이미지를 칸 id로 묶어 읽는다(R25).
 *
 * 이전에는 `CellImageBatchLoader`라는 이름의 요청 범위 메모(`graphQlContext.computeIfAbsent`)였다.
 * 동작은 의도대로였지만 **이름이 사실과 달라** DataLoader를 감사하는 사람이 이것을 로더로
 * 착각했다.
 *
 * **키를 모아도 전량을 읽는다.** 칸이 161개로 고정된 작은 집합이라 요청된 칸만 골라 읽는
 * 장치를 만들어 봐야 한 행 대신 161행을 읽는 차이뿐이고, 그 장치가 틀리면 N+1로 조용히
 * 되돌아간다. 언어와 무관한 값이라 keyContext도 필요 없다.
 *
 * **이미지가 없는 칸은 결과에 키가 없다**(SPEC-0030 R10) — 그 칸도 조회에서 빠지지 않으며
 * 필드만 null이 된다. 호출부가 그것을 null로 다룬다.
 */
@DgsDataLoader(name = CellImageDataLoader.NAME)
@Component
class CellImageDataLoader(
    private val nationwideMapUseCase: NationwideMapUseCase,
) : MappedBatchLoader<Long, CellImageView> {

    override fun load(cellIds: Set<Long>): CompletionStage<Map<Long, CellImageView>> =
        // 부재 키를 채우지 않는다 — 이미지 없는 칸은 필드가 null이어야 한다.
        CompletableFuture.completedFuture(nationwideMapUseCase.allCellImages())

    companion object {
        const val NAME = "cellImage"
    }
}
