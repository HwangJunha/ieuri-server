package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import org.around.ieuri.tourism.application.port.`in`.query.NationwideMapUseCase
import org.around.ieuri.tourism.application.query.CellImageView

/**
 * 칸 대표 이미지의 요청 스코프 배치 적재(SPEC-0030 R9).
 *
 * `mapCells`가 161칸을 돌려주므로 칸마다 한 번씩 물으면 161왕복이 된다. 첫 칸이 물어볼 때
 * **161칸 전부의 대표 이미지를 한 질의로 읽어** 담아 두고, 나머지 칸은 맵에서 꺼낸다.
 *
 * 요청한 칸만 골라 읽지 않고 전량을 읽는 이유는 칸이 **161개로 고정된 작은 집합**이기
 * 때문이다. 어느 칸이 선택됐는지 알아내는 장치를 만들어 봐야 단건 조회에서 한 행 대신 161행을
 * 읽는 차이뿐이고, 그 장치가 틀리면 N+1로 조용히 되돌아간다.
 *
 * DataLoader가 아니라 요청 컨텍스트 메모인 이유는 모을 키가 없기 때문이다 —
 * `CategoryDisplayResolver`의 선례와 같다.
 *
 * **이미지가 없는 칸은 맵에 키가 없다**(R10). 그 칸도 조회에서 빠지지 않으며 필드만 null이다.
 *
 * `ConcurrentHashMap`이 필요 없다 — `CategoryDisplayResolver`는 코드마다 항목을 채워 넣어서
 * 형제 필드가 같은 맵을 동시에 **변경**했지만, 여기서는 한 번에 만든 맵을 읽기만 한다.
 * `graphQlContext`의 `computeIfAbsent`가 원자적이라 가상 스레드에서도 적재가 한 번뿐이다.
 */
object CellImageBatchLoader {

    private const val CACHE_KEY = "SPEC-0030.cellImages"

    fun load(
        dfe: DgsDataFetchingEnvironment,
        cellCode: String,
        useCase: NationwideMapUseCase,
    ): CellImageView? {
        @Suppress("UNCHECKED_CAST")
        val cache = dfe.graphQlContext.computeIfAbsent(CACHE_KEY) {
            useCase.allCellImages()
        } as Map<String, CellImageView>

        return cache[cellCode]
    }
}
