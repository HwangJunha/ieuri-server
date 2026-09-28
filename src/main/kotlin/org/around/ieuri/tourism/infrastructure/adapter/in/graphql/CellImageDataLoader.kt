package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellImageView
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 칸 대표 이미지를 칸 id로 묶어 읽는 배치 로더(SPEC-0032 R25).
 * - 키와 무관하게 전량(칸 161개)을 읽는다. 언어와 무관해 keyContext가 없다.
 * - 이미지가 없는 칸은 결과에 키가 없고, 필드가 null이 된다(SPEC-0030 R10).
 */
@DgsDataLoader(name = CellImageDataLoader.NAME)
@Component
class CellImageDataLoader(
    private val nationwideMapUseCase: NationwideMapUseCase,
) : MappedBatchLoader<Long, CellImageView> {

    override fun load(cellIds: Set<Long>): CompletionStage<Map<Long, CellImageView>> = // 부재 키를 채우지 않는다.
        CompletableFuture.completedFuture(nationwideMapUseCase.allCellImages())

    companion object {
        const val NAME = "cellImage"
    }
}
