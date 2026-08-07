package org.around.ieuri.auth.infrastructure.seed

import jakarta.persistence.EntityManager
import org.around.ieuri.auth.application.port.out.security.EmailEncryptorPort
import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberProfileEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberRoleEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.MemberTermsAgreementEntity
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberTermsAgreementJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.TermsJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberRoleJpaRepository
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.RoleJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 로컬 개발용 테스트 계정 시드. **`local` 프로필에서만 동작한다** —
 * 운영·CI 컨텍스트에는 이 빈 자체가 존재하지 않아 실수로 배포될 수 없다.
 *
 * **Flyway 마이그레이션으로 넣을 수 없다.** 비밀번호는 `bcrypt(HMAC-SHA256(pw, pepper))`이고
 * 이메일은 AES-GCM(매번 다른 IV)이라 둘 다 런타임 키를 쥔 빈이 있어야 만들 수 있다.
 * SQL에 상수로 박으면 pepper·키가 바뀌는 순간 로그인이 조용히 깨진다.
 *
 * **이미 있으면 건드리지 않는다.** 탈퇴(R17)를 시험해 지웠다면 다음 기동에서 다시 생기고,
 * 그 계정으로 쌓아 둔 글·댓글은 기동만으로 날아가지 않는다.
 */
@Component
@Profile("local")
class LocalTestAccountSeeder(
    private val entityManager: EntityManager,
    private val profileRepository: MemberProfileJpaRepository,
    private val roleRepository: RoleJpaRepository,
    private val memberRoleRepository: MemberRoleJpaRepository,
    private val termsRepository: TermsJpaRepository,
    private val agreementRepository: MemberTermsAgreementJpaRepository,
    private val passwordEncoder: PasswordEncoderPort,
    private val emailEncryptor: EmailEncryptorPort,
    private val clock: Clock,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (memberExists()) {
            log.info("local test account already present (id={}), skipping seed", MEMBER_ID)
            return
        }

        insertMember()
        insertProfile()
        insertRole()
        insertAgreements()

        log.info("local test account seeded: id={} email={}", MEMBER_ID, EMAIL)
    }

    private fun memberExists(): Boolean =
        (
            entityManager.createNativeQuery("SELECT count(*) FROM member WHERE id = :id")
                .setParameter("id", MEMBER_ID)
                .singleResult as Number
            ).toLong() > 0

    /**
     * id를 못 박기 위해 네이티브 SQL을 쓴다. `member.id`는 `GENERATED ALWAYS AS IDENTITY`라
     * `OVERRIDING SYSTEM VALUE` 없이는 지정한 값이 무시된다 — JPA로는 표현할 수 없다.
     *
     * 시퀀스도 함께 밀어 준다. 그러지 않으면 다음 가입이 14를 다시 발급받아 PK 충돌이 난다.
     *
     * `provider_user_id`에 평문 이메일이 들어가는 것은 현재 EMAIL 가입 경로와 같은 모양이다
     * (SPEC-0012가 해소 예정). 여기서 다르게 넣으면 로그인 조회가 이 계정만 빗나간다.
     */
    private fun insertMember() {
        entityManager.createNativeQuery(
            """
            INSERT INTO member (
                id, provider, provider_user_id, email_encrypted, email_verified,
                status, age_verified, password_hash, created_at, updated_at
            ) OVERRIDING SYSTEM VALUE
            VALUES (:id, 'EMAIL', :providerUserId, :emailEncrypted, true,
                    'ACTIVE', true, :passwordHash, now(), now())
            """.trimIndent(),
        )
            .setParameter("id", MEMBER_ID)
            .setParameter("providerUserId", EMAIL)
            .setParameter("emailEncrypted", emailEncryptor.encrypt(EMAIL))
            .setParameter("passwordHash", passwordEncoder.encode(PASSWORD))
            .executeUpdate()

        // 시퀀스가 이 id를 이미 지나갔으면 그대로 두고, 뒤처져 있으면 앞으로 당긴다.
        entityManager.createNativeQuery(
            """
            SELECT setval(
                pg_get_serial_sequence('member', 'id'),
                GREATEST((SELECT COALESCE(max(id), 1) FROM member), :id)
            )
            """.trimIndent(),
        )
            .setParameter("id", MEMBER_ID)
            .singleResult
    }

    private fun insertProfile() {
        profileRepository.save(
            MemberProfileEntity(memberId = MEMBER_ID, nickname = NICKNAME, profileImageUrl = null),
        )
    }

    private fun insertRole() {
        val role = roleRepository.findByCode(Role.USER)
            ?: error("기본 역할 '${Role.USER}'가 없다 — RBAC 부트스트랩이 먼저 돌아야 한다.")
        memberRoleRepository.save(MemberRoleEntity(memberId = MEMBER_ID, roleId = requireNotNull(role.id)))
    }

    /** 시행 중인 약관 전부에 동의시킨다 — 필수 약관 게이트(R11)에 걸리지 않게 한다. */
    private fun insertAgreements() {
        val now = clock.instant()
        val activeTerms = termsRepository.findByEffectiveAtLessThanEqual(now)
            .groupBy { it.code }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.version } }

        agreementRepository.saveAll(
            activeTerms.map {
                MemberTermsAgreementEntity(
                    memberId = MEMBER_ID,
                    termsId = requireNotNull(it.id),
                    termsCode = it.code,
                    termsVersion = it.version,
                    agreed = true,
                    agreedAt = now,
                )
            },
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(LocalTestAccountSeeder::class.java)

        const val MEMBER_ID = 14L
        const val EMAIL = "tarot10@naver.com"
        const val PASSWORD = "!!tjdgk1415"
        const val NICKNAME = "tarot10"
    }
}
