package org.around.ieuri.auth.infrastructure.seed

import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 로컬 개발용 테스트 계정 시드. **`local` 프로필에서만 동작한다** —
 * 운영·CI 컨텍스트에는 이 빈 자체가 없어 실수로 배포될 수 없다.
 *
 * 계정: tarot10@naver.com / !!tjdgk1415 (member.id = 14 고정)
 *
 * **Flyway `R__`이 아니라 ApplicationRunner인 이유.** `R__` 파일은 *체크섬이 바뀔 때만*
 * 재적용된다. 탈퇴(R17)를 시험해 계정을 지워도 파일이 그대로면 Flyway가 건너뛰므로
 * 다음 기동에 계정이 돌아오지 않는다 — 매번 실행을 강제하는 옵션은 없다.
 * 여기서는 기동할 때마다 무조건 돈다.
 *
 * `member.id = 14`로 못 박는 이유는 `R__seed_community.sql`이 `author_id = 14`로 글·댓글을
 * 넣기 때문이다. id가 달라지면 그 데이터가 작성자 없는 상태로 남는다.
 *
 * **해시와 암호문은 앱이 만든 값을 그대로 쓴다.** 비밀번호 해시는
 * `bcrypt(base64(HMAC-SHA256(pw, pepper)))`이고 pepper는 환경변수로 덮일 수 있어
 * 다시 계산하면 특정 셸에서만 맞는다. 이메일은 AES-256-GCM(랜덤 IV)이라 재현이 불가능하다.
 * 로컬 키를 바꾸면 이 두 상수도 다시 떠 와야 한다(앱으로 가입 후 두 컬럼 복사).
 */
@Component
@Profile("local")
@Order(SEED_ORDER)
class LocalTestAccountSeeder(
    private val entityManager: EntityManager,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        upsertMember()
        alignSequence()
        clearVerifications()
        upsertProfile()
        assignUserRole()
        agreeToActiveTerms()
        seedSpotReactions()

        log.info("local test account seeded: id={} email={}", MEMBER_ID, EMAIL)
    }

    /**
     * id를 지정하려면 `OVERRIDING SYSTEM VALUE`가 필요하다(member.id는 IDENTITY다).
     * 이미 있으면 인증 관련 컬럼만 되돌린다 — 정지·인증 해제·비밀번호 변경을 시험했어도
     * 기동하면 로그인 가능한 상태로 복구된다.
     */
    private fun upsertMember() = execute(
        """
        INSERT INTO member (
            id, provider, provider_user_id, email_encrypted, email_verified,
            status, age_verified, password_hash, created_at, updated_at
        ) OVERRIDING SYSTEM VALUE
        VALUES (:id, 'EMAIL', :email, :emailEncrypted, TRUE, 'ACTIVE', TRUE, :passwordHash, now(), now())
        ON CONFLICT (id) DO UPDATE SET
            provider          = EXCLUDED.provider,
            provider_user_id  = EXCLUDED.provider_user_id,
            email_encrypted   = EXCLUDED.email_encrypted,
            email_verified    = TRUE,
            status            = 'ACTIVE',
            age_verified      = TRUE,
            suspended_at      = NULL,
            suspended_until   = NULL,
            suspension_reason = NULL,
            password_hash     = EXCLUDED.password_hash,
            updated_at        = now()
        """.trimIndent(),
        "id" to MEMBER_ID,
        "email" to EMAIL,
        "emailEncrypted" to EMAIL_ENCRYPTED,
        "passwordHash" to PASSWORD_HASH,
    )

    /** IDENTITY 시퀀스는 위 INSERT로 움직이지 않는다 — 밀어 두지 않으면 다음 가입이 14를 재발급한다. */
    private fun alignSequence() {
        entityManager.createNativeQuery(
            "SELECT setval(pg_get_serial_sequence('member', 'id'), GREATEST((SELECT max(id) FROM member), :id))",
        ).setParameter("id", MEMBER_ID).singleResult
    }

    /** 이미 인증된 상태로 넣으므로 남길 이유가 없고, 재발송 쿨다운이 걸린 채 남으면 번거롭다. */
    private fun clearVerifications() =
        execute("DELETE FROM email_verification WHERE member_id = :id", "id" to MEMBER_ID)

    /** 이미 있으면 닉네임을 건드리지 않는다 — 닉네임 변경(R5)을 시험한 값이 살아남는다. */
    private fun upsertProfile() = execute(
        """
        INSERT INTO member_profile (member_id, nickname, profile_image_url, created_at, updated_at)
        VALUES (:id, :nickname, NULL, now(), now())
        ON CONFLICT (member_id) DO NOTHING
        """.trimIndent(),
        "id" to MEMBER_ID,
        "nickname" to NICKNAME,
    )

    private fun assignUserRole() = execute(
        """
        INSERT INTO member_role (member_id, role_id)
        SELECT :id, r.id FROM role r WHERE r.code = 'USER'
        ON CONFLICT DO NOTHING
        """.trimIndent(),
        "id" to MEMBER_ID,
    )

    /**
     * 시행 중인 약관 전부에 동의시킨다 — 미동의면 필수 약관 게이트(R11)에 걸린다.
     * 코드별 최신 버전만 고른다(버전이 올라가면 이전 동의는 이월되지 않는다).
     */
    private fun agreeToActiveTerms() = execute(
        """
        INSERT INTO member_terms_agreement (member_id, terms_id, terms_code, terms_version, agreed, agreed_at)
        SELECT :id, t.id, t.code, t.version, TRUE, now()
        FROM terms t
        JOIN (
            SELECT code, max(version) AS version FROM terms WHERE effective_at <= now() GROUP BY code
        ) latest ON latest.code = t.code AND latest.version = t.version
        ON CONFLICT (member_id, terms_id) DO UPDATE SET agreed = TRUE, agreed_at = now()
        """.trimIndent(),
        "id" to MEMBER_ID,
    )

    /**
     * 관광지 좋아요/싫어요. **탈퇴 시 삭제(R17)를 시험하려면 지울 것이 있어야 한다** —
     * 이게 없으면 그 경로가 동작하는지 확인할 수 없다.
     *
     * 존재하지 않는 spot은 FK가 거부하므로 실재하는 것만 고른다. `spot`이 아직 비어 있는
     * 환경(배치 미실행)에서는 조인 결과가 없어 아무것도 넣지 않는다 — 오류가 아니다.
     */
    private fun seedSpotReactions() = execute(
        """
        INSERT INTO spot_reaction (member_id, spot_id, reaction, created_at, updated_at)
        SELECT :id, s.id, v.reaction, now(), now()
        FROM (VALUES (11094, 'LIKE'), (33313, 'LIKE'), (10997, 'LIKE'), (11104, 'DISLIKE'))
             AS v(spot_id, reaction)
        JOIN spot s ON s.id = v.spot_id
        ON CONFLICT (member_id, spot_id) DO UPDATE SET reaction = EXCLUDED.reaction, updated_at = now()
        """.trimIndent(),
        "id" to MEMBER_ID,
    )

    private fun execute(sql: String, vararg params: Pair<String, Any>) {
        val query = entityManager.createNativeQuery(sql)
        params.forEach { (name, value) -> query.setParameter(name, value) }
        query.executeUpdate()
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LocalTestAccountSeeder::class.java)

        const val MEMBER_ID = 14L
        const val EMAIL = "tarot10@naver.com"
        const val NICKNAME = "테스트계정"

        /** 로컬 encryption.key로 암호화된 [EMAIL]. base64(iv(12B) || ciphertext+tag). */
        const val EMAIL_ENCRYPTED = "cdliqSVt2uerTs5PiOfL4N7YqzFtFJ7e4kmcaYLq680v/Uxcf7DZ2h1tL9pV"

        /** 로컬 pepper로 해시된 `!!tjdgk1415`. */
        const val PASSWORD_HASH = "\$2a\$10\$B83.I4/zu2lO9P9QzCHayOQIcOww2ceW60XN6N4hwis6BiuECKC9."
    }
}

/** RBAC·약관 시드(Flyway)가 끝난 뒤에 돈다. 기본 순서보다 뒤이기만 하면 된다. */
private const val SEED_ORDER = 100
