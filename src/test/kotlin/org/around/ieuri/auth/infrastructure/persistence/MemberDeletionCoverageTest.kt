package org.around.ieuri.auth.infrastructure.persistence

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `member`를 참조하는 FK 자식이 전부 `deleteMember`에서 정리되는지 마이그레이션과 대조한다.
 *
 * **하나라도 빠지면 그 테이블에 행을 만든 회원만 탈퇴가 영구히 막힌다.** 실제로
 * `password_reset`이 빠져 있었고, 비밀번호 재설정을 요청한 적 없는 계정은 정상 탈퇴돼
 * 오래 드러나지 않았다. 탈퇴는 사용자가 자기 데이터를 지울 수 있는 유일한 경로이고
 * SPEC-0024에서는 인증 화면의 탈출구이기도 하므로, 막히면 앱에서 빠져나갈 수 없다.
 *
 * DB를 띄우지 않는다 — 마이그레이션 SQL을 읽어 FK 선언을 세는 정적 검사다.
 */
class MemberDeletionCoverageTest {

    private val migrationDir = File("src/main/resources/db/migration")
    private val adapterSource = File(
        "src/main/kotlin/org/around/ieuri/auth/infrastructure/adapter/out/persistence/MemberPersistenceAdapter.kt",
    )

    /**
     * `member(id)`를 참조하는 테이블 이름. `REFERENCES member(id)`를 선언한
     * `CREATE TABLE`/`ALTER TABLE`을 찾는다.
     */
    private fun tablesReferencingMember(): Set<String> {
        val referencing = mutableSetOf<String>()

        migrationDir.walkTopDown().filter { it.extension == "sql" }.forEach { file ->
            val sql = file.readText()
            // CREATE TABLE <t> ( ... REFERENCES member(id) ... )
            Regex(
                """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\((.*?)\n\);""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(sql).forEach { match ->
                if (Regex("""REFERENCES\s+(?:auth\.)?member\s*\(""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(match.groupValues[2])
                ) {
                    referencing += match.groupValues[1].lowercase()
                }
            }
            // ALTER TABLE [ONLY] <t> ADD CONSTRAINT ... REFERENCES member(id)
            //
            // **`ONLY`를 건너뛰어야 한다** — pg_dump가 항상 붙이며(V1 베이스라인이 그 산출물이다),
            // 없으면 `only`가 테이블 이름으로 잡혀 참조 목록이 통째로 어긋난다.
            Regex(
                """ALTER\s+TABLE\s+(?:ONLY\s+)?(?:auth\.)?(\w+)[^;]*?REFERENCES\s+(?:auth\.)?member\s*\(""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).findAll(sql).forEach { referencing += it.groupValues[1].lowercase() }
        }
        return referencing
    }

    @Test
    fun `member를 참조하는 모든 테이블이 탈퇴 시 정리된다`() {
        val referencing = tablesReferencingMember()
        // 마이그레이션을 읽지 못했다면 이 테스트가 무의미해지므로 먼저 막는다.
        assertTrue(referencing.size >= 4, "member를 참조하는 테이블을 찾지 못했다: $referencing")

        val source = adapterSource.readText()
        val deleteBody = source.substringAfter("override fun deleteMember(").substringBefore("\n    }")

        // 삭제 본문이 부르는 저장소 필드 → 그 필드의 선언 타입(=JPA 리포지토리 이름)을 찾는다.
        // 이름 규칙(agreementRepository ↔ member_terms_agreement)이 일정하지 않아 필드명이 아니라
        // **타입**으로 대조한다 — 타입은 엔티티를 따라가므로 테이블과 어긋나지 않는다.
        val calledTypes = Regex("""(\w+)\.delete(?:ByMemberId|ById)\(""")
            .findAll(deleteBody)
            .mapNotNull { call ->
                Regex("""val ${call.groupValues[1]}: (\w+)""").find(source)?.groupValues?.get(1)
            }
            .toSet()

        // MemberTermsAgreementJpaRepository → membertermsagreement
        val normalized = calledTypes.map {
            it.removeSuffix("JpaRepository").lowercase()
        }.toSet()

        val missing = referencing.filterNot { table ->
            normalized.contains(table.replace("_", ""))
        }

        assertTrue(
            missing.isEmpty(),
            "deleteMember가 정리하지 않는 FK 자식이 있다: $missing — " +
                "이 테이블에 행이 있는 회원은 탈퇴가 FK 제약에 걸려 영구히 막힌다. " +
                "(정리 중인 타입: $normalized)",
        )
    }
}
