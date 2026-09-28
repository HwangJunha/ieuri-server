package org.around.ieuri.common.persistence

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

/**
 * 읽기 전용 트랜잭션을 replica로 보낸다(PERF-20260921).
 *
 * **[org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy]로 감싸야 한다.**
 * 감싸지 않으면 트랜잭션 시작 시점에 커넥션을 먼저 잡아 [determineCurrentLookupKey]가
 * 불리기 전에 대상이 정해진다 — 오류 없이 **전부 primary로 간다.**
 *
 * **기본값은 primary다.** 애노테이션이 없는 조회는 primary로 가고(느리지만 정확), 쓰기가
 * replica로 새어 `cannot execute INSERT in a read-only transaction`이 나는 일은 없다.
 *
 * 복제는 비동기라 쓰기 직후 읽기는 같은 트랜잭션 안에 두어야 한다 — 쓰기 트랜잭션에
 * 참여한 조회는 `readOnly`가 무시되어 자연히 primary로 간다.
 */
class ReadWriteRoutingDataSource(
    primary: DataSource,
    replica: DataSource,
) : AbstractRoutingDataSource() {

    init {
        setDefaultTargetDataSource(primary)
        setTargetDataSources(mapOf(Target.PRIMARY to primary, Target.REPLICA to replica))
        // **직접 불러야 한다.** 이 객체는 @Bean 메서드 안에서 만들어져 컨테이너가
        // InitializingBean 콜백을 주지 않는다 — 빠뜨리면 첫 조회에서
        // `DataSource router not initialized`로 기동이 깨진다(실측).
        afterPropertiesSet()
    }

    override fun determineCurrentLookupKey(): Any = if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        Target.REPLICA
    } else {
        Target.PRIMARY
    }

    private enum class Target { PRIMARY, REPLICA }
}
