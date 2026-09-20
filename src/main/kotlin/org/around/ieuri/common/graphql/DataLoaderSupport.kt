package org.around.ieuri.common.graphql

import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader

/**
 * 등록된 DataLoader를 이름으로 꺼낸다(공유 커널).
 *
 * **이름이 어긋난 로더를 조용히 넘기지 않는다.** `getDataLoader`는 미등록 시 null을 주는데,
 * 그것을 그대로 흘리면 필드가 빈 채로 떠서 "데이터가 없음"과 구별되지 않는다 — 오타 하나가
 * 조용한 오답이 된다. 명시적으로 실패시켜 배포 전에 드러나게 한다.
 *
 * **`Long` 키 로더 전용이다.** 다른 키 타입의 로더는 `getDataLoader`를 직접 부르고 미등록
 * 판정도 그 자리에서 한다(`categoryDisplayLoader`). 이 함수가 주는 것은 에러 한 줄뿐이라,
 * 그것을 재사용하려고 시그니처를 넓히면 호출부 13곳이 함께 바뀐다 — 비용이 뒤집힌 거래다.
 */
fun <V : Any> DataFetchingEnvironment.loader(name: String): DataLoader<Long, V> =
    getDataLoader<Long, V>(name) ?: error("DataLoader '$name'가 등록되지 않았다.")
