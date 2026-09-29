package org.around.ieuri.common.graphql

import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoader

/**
 * 등록된 DataLoader를 이름으로 꺼낸다. 미등록 이름이면 null을 흘리지 않고 실패한다.
 */
fun <V : Any> DataFetchingEnvironment.loader(name: String): DataLoader<Long, V> = getDataLoader<Long, V>(name) ?: error("DataLoader '$name'가 등록되지 않았다.")
