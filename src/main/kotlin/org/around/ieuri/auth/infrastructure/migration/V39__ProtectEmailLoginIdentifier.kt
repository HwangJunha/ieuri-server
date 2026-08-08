package org.around.ieuri.auth.infrastructure.migration

import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.springframework.stereotype.Component

/**
 * SPEC-0012 R3. `Provider.EMAIL` 회원의 provider_user_id(평문 이메일)를 보호된 형태로 이전한다.
 *
 * SQL이 아니라 Java 마이그레이션인 이유: 변환에 애플리케이션 pepper가 필요한데,
 * 그 값은 환경변수로만 주입되며 저장소에 커밋될 수 없다. pgcrypto로 DB에서 계산하려면
 * 마이그레이션 파일에 pepper를 literal로 박아야 해서 쓸 수 없다.
 *
 * SNS(APPLE/GOOGLE/KAKAO) 행은 대상이 아니다 — provider가 준 불투명 식별자라 보호 대상이 아니며,
 * 이 마이그레이션이 건드리면 SNS 로그인이 깨진다.
 *
 * 이전 판별은 값의 형태로 한다: 보호된 값은 64자 hex이고 평문 이메일은 '@'를 포함하므로
 * 재실행되어도 이미 이전된 행을 다시 변환하지 않는다.
 */
@Component
class V39__ProtectEmailLoginIdentifier(
    private val identifierProtector: LoginIdentifierProtectorPort,
) : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val connection = context.connection

        val plaintextIdentifiers = mutableListOf<Pair<Long, String>>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, provider_user_id FROM member WHERE provider = 'EMAIL' AND provider_user_id LIKE '%@%'",
            ).use { rows ->
                while (rows.next()) {
                    plaintextIdentifiers += rows.getLong("id") to rows.getString("provider_user_id")
                }
            }
        }

        connection.prepareStatement("UPDATE member SET provider_user_id = ? WHERE id = ?").use { statement ->
            plaintextIdentifiers.forEach { (memberId, plaintext) ->
                // Email VO의 정규화(trim+lowercase)와 반드시 같아야 한다 — 어긋나면 이전된 회원이 로그인 불가가 된다.
                statement.setString(1, identifierProtector.protect(plaintext.trim().lowercase()))
                statement.setLong(2, memberId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
