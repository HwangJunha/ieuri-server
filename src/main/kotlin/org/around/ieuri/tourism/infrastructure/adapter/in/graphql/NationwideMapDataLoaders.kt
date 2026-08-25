package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.application.query.CellSpotView
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 전국 지도 칸의 배치 로더(SPEC-0032 R24·R25).
 *
 * community의 13종과 같은 형태를 쓴다 — `MappedBatchLoader`, 키는 `Long`,
 * `companion object { const val NAME }`, 동기 완료. tourism에 로더가 없어 같은 문제를 다른
 * 방식으로 풀고 있던 것을 하나로 맞춘다.
 */

/**
 * 칸에 속한 장소의 **첫 페이지**를 칸 id로 묶어 읽는다(R24).
 *
 * `mapCells`는 161칸을 페이지네이션 없이 돌려주므로, `spots`를 고르면 칸마다 질의가 따로
 * 나가 **161회**가 된다. `updatedCells`·`SharedMap.overlappingCells`로도 같은 곳에 도달한다.
 * 이전에는 이것을 스키마 주석이 "지도를 그릴 때는 고르지 말라"고 막고 있었는데, **계약이
 * 강제하지 못하는 것을 클라이언트에게 요구하는 것**이라 고르면 그대로 161회였다.
 *
 * **첫 페이지만 배치한다.** `after`가 있는 호출은 칸이 하나로 좁혀져 있어 모을 것이 없고,
 * 칸마다 다른 커서를 허용하면 SQL이 칸 수를 따라 커진다.
 *
 * **활성 언어와 페이지 크기를 컨텍스트로 받는다** — 요청당 하나뿐인 값이라 키는 여전히 칸
 * id 하나지만, 없이 조회하면 언어가 기본값으로 돌아간다. `PostSpotDataLoader`의 선례를 따라
 * **폴백하지 않고 실패한다**: 요청이 언제나 갖는 값이라 부재는 리졸버의 결함이다.
 *
 * **장소가 없는 칸은 빈 페이지로 채운다** — null과 "비어 있음"이 다르면 클라이언트가 분기해야
 * 하고, 여기서는 그럴 이유가 없다.
 */
@DgsDataLoader(name = CellSpotsDataLoader.NAME)
@Component
class CellSpotsDataLoader(
    private val nationwideMapUseCase: NationwideMapUseCase,
) : MappedBatchLoaderWithContext<CellSpotsKey, CursorPage<CellSpotView>> {

    override fun load(
        keys: Set<CellSpotsKey>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<CellSpotsKey, CursorPage<CellSpotView>>> {
        val locale = environment.keyContexts.values
            .filterIsInstance<DomainLocale>()
            .firstOrNull()
            ?: error("CellSpotsDataLoader는 keyContext로 언어를 받아야 한다 — 리졸버가 load(key, locale)로 부른다.")

        // **페이지 크기별로 나눠 조회한다.** 한 문서가 같은 칸을 다른 first로 두 번 고를 수
        // 있어(별칭), 크기를 하나로 뭉치면 한쪽이 남의 페이지를 받는다.
        val pages = keys.groupBy { it.first }.mapValues { (first, group) ->
            nationwideMapUseCase.firstCellSpotsOf(
                cellIds = group.map { it.cellId },
                first = first,
                locale = locale,
            )
        }

        // 장소가 없는 칸은 조회 결과에 키가 없다 — 빈 페이지로 채워 리졸버가 null을 만나지 않게 한다.
        return CompletableFuture.completedFuture(
            keys.associateWith { pages[it.first]?.get(it.cellId) ?: emptyPage() },
        )
    }

    /** 장소가 없는 칸의 페이지. 첫 페이지이므로 양쪽 다 false다. */
    private fun emptyPage(): CursorPage<CellSpotView> =
        cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)

    companion object {
        const val NAME = "cellSpots"
    }
}

/**
 * [CellSpotsDataLoader]의 키.
 *
 * **페이지 크기가 키의 일부여야 한다.** DataLoader는 키로 캐시하는데(`caching` 기본값 true),
 * `first`는 필드 인자라 한 문서가 같은 칸을 다른 크기로 두 번 고를 수 있다 —
 * `mapCells { a: spots(first: 5) b: spots(first: 50) }`. 칸 id만 키로 쓰면 두 번째 호출이
 * 첫 번째의 페이지를 그대로 받고, **오류가 아니라 잘못된 데이터라 조용하다.**
 *
 * 언어는 요청당 하나뿐이라 키가 아니라 keyContext로 간다.
 */
data class CellSpotsKey(
    val cellId: Long,
    val first: Int,
)

/**
 * 칸 대표 이미지를 칸 id로 묶어 읽는다(R25).
 *
 * 이전에는 `CellImageBatchLoader`라는 이름의 요청 범위 메모(`graphQlContext.computeIfAbsent`)였다.
 * 동작은 의도대로였지만 **이름이 사실과 달라** DataLoader를 감사하는 사람이 이것을 로더로
 * 착각했다. R24로 칸 id 키 로더가 생기므로 같은 형태로 맞춘다.
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
