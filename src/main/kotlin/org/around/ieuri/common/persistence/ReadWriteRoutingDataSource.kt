package org.around.ieuri.common.persistence

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

/**
 * 읽기 전용 트랜잭션을 replica로 보낸다. 그 밖은 전부 primary다.
 * - [org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy]로 감싸야 한다 — 아니면 조용히 전부 primary로 간다.
 * - 복제는 비동기다. 쓰기 직후 읽기는 같은 쓰기 트랜잭션 안에 둔다.
 */
class ReadWriteRoutingDataSource(
    primary: DataSource,
    replica: DataSource,
) : AbstractRoutingDataSource() {

    init {
        setDefaultTargetDataSource(primary)
        setTargetDataSources(mapOf(Target.PRIMARY to primary, Target.REPLICA to replica))
        // 직접 불러야 한다 — @Bean 안에서 만들어져 InitializingBean 콜백이 오지 않는다.
        afterPropertiesSet()
    }

    override fun determineCurrentLookupKey(): Any = if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        Target.REPLICA
    } else {
        Target.PRIMARY
    }

    private enum class Target { PRIMARY, REPLICA }
}
