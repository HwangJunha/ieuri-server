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
 * 키가 `Long`인 로더가 대부분이라 [loader]가 그 경우를 짧게 쓰게 해 주고, 복합 키를 쓰는
 * 로더는 [loaderOf]로 키 타입을 명시한다.
 */
fun <K : Any, V : Any> DataFetchingEnvironment.loaderOf(name: String): DataLoader<K, V> =
    getDataLoader<K, V>(name) ?: error("DataLoader '$name'가 등록되지 않았다.")

/** 키가 `Long`인 로더. 이 저장소의 대부분이 여기 해당한다. */
fun <V : Any> DataFetchingEnvironment.loader(name: String): DataLoader<Long, V> =
    loaderOf(name)
