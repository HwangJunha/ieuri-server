package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 한 context가 Exposed로 선언한 테이블 목록. context마다 빈 하나로 내놓는다. */
class ExposedTables(val tables: List<Table>)

/**
 * `ddl-auto=validate` 대신: Exposed 테이블 정의가 DB와 다르면 기동을 실패시킨다(SPEC-0039 D-35).
 * - 컬럼 타입 불일치는 잡지 못한다(Exposed가 PostgreSQL에서 타입을 비교하지 않는다).
 * - 모든 싱글톤이 만들어진 뒤, 웹 서버가 요청을 받기 전에 돈다. Flyway 마이그레이션 뒤다.
 */
@Component
@DependsOn("flyway")
class ExposedSchemaValidator(
    private val tableSets: ObjectProvider<ExposedTables>,
    @Qualifier(ExposedConfig.EXPOSED_TX) private val transactionManager: PlatformTransactionManager,
) : SmartInitializingSingleton {

    override fun afterSingletonsInstantiated() {
        val tables = tableSets.orderedStream().toList().flatMap { it.tables }
        if (tables.isEmpty()) return
        val drift = TransactionTemplate(transactionManager).execute {
            val all = tables.toTypedArray()
            // identity 컬럼의 시퀀스는 Exposed가 스키마 붙은 이름으로 추정해 늘 어긋난다 — Exposed가 낸 시퀀스 문장만 뺀다.
            val identitySequences = MigrationUtils.dropUnmappedSequences(*all, withLogs = false) +
                tables.flatMap { table -> table.sequences.flatMap { it.createStatement() } }
            MigrationUtils.statementsRequiredForDatabaseMigration(*all, withLogs = false) - identitySequences.toSet()
        }.orEmpty()
        check(drift.isEmpty()) {
            "Exposed 테이블 정의가 DB 스키마와 다르다 — Table 객체를 마이그레이션에 맞춘다:\n" + drift.joinToString(";\n")
        }
        log.info("Exposed 스키마 검증 통과: 테이블 {}개", tables.size)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ExposedSchemaValidator::class.java)
    }
}
