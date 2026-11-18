package org.around.ieuri.common.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * 읽기 복제본 풀을 언제 만드는가 — url이 빈 문자열이면 만들지 않는다.
 */
class ReplicaDataSourceWiringTest {

    private val config = DataSourceConfig()

    private fun properties(): DataSourceProperties = DataSourceProperties().apply {
        url = "jdbc:postgresql://localhost:5432/ieuri"
        username = "ieuri"
        password = "pw"
    }

    @Test
    @DisplayName("복제본 url이 비어 있으면 복제본 풀을 만들지 않는다 — 로컬 기본값")
    fun `blank url creates no replica pool`() {
        assertThat(config.replicaDataSource(properties(), ReadReplicaProperties(url = ""))).isNull()
    }

    @Test
    @DisplayName("공백뿐인 url도 없는 것으로 본다")
    fun `whitespace url creates no replica pool`() {
        assertThat(config.replicaDataSource(properties(), ReadReplicaProperties(url = "   "))).isNull()
    }

    @Test
    @DisplayName("복제본 url이 있으면 그 url로 풀을 만든다")
    fun `present url creates a replica pool`() {
        val replica = config.replicaDataSource(
            properties(),
            ReadReplicaProperties(url = "jdbc:postgresql://ieuri-pg-ro:5432/ieuri"),
        )
        assertThat(replica).isNotNull()
    }

    @Test
    @DisplayName("복제본이 없으면 라우팅 없이 primary를 그대로 쓴다")
    fun `no replica means primary is used directly`() {
        val primary = StubDataSource()

        assertThat(config.dataSource(primary, replica = null)).isSameAs(primary)
    }

    @Test
    @DisplayName("복제본이 있으면 LazyConnectionDataSourceProxy로 감싼 라우팅을 쓴다")
    fun `replica present means lazy routing proxy`() {
        val resolved = config.dataSource(StubDataSource(), replica = StubDataSource())

        // 감싸지 않으면 트랜잭션 시작 시점에 커넥션을 먼저 잡아 라우팅 판단 전에 primary가 정해진다.
        assertThat(resolved).isInstanceOf(LazyConnectionDataSourceProxy::class.java)
    }
}

private class StubDataSource : DataSource {
    override fun getConnection() = throw UnsupportedOperationException()
    override fun getConnection(username: String?, password: String?) = throw UnsupportedOperationException()
    override fun getLogWriter() = throw UnsupportedOperationException()
    override fun setLogWriter(out: java.io.PrintWriter?) = throw UnsupportedOperationException()
    override fun setLoginTimeout(seconds: Int) = throw UnsupportedOperationException()
    override fun getLoginTimeout() = 0
    override fun getParentLogger() = throw UnsupportedOperationException()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()
    override fun isWrapperFor(iface: Class<*>?) = false
}
