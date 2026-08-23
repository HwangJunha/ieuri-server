package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.vo.Agreement
import java.time.Instant

/**
 * 프로필 쓰기 포트(SPEC-0011 R5·R6). 닉네임과 프로필 이미지는 member_profile 소유다.
 */
interface MemberProfileRepositoryPort {
    /**
     * 다른 회원이 [nickname]을 쓰고 있는지. 본인 것은 제외하므로 같은 값 재제출은 참이 아니다.
     *
     * **이 확인이 고유성을 보장하지는 않는다** — 두 요청이 동시에 통과할 수 있다.
     * 최종 판정은 유니크 제약이며, 위반은 [NicknameConflictException]으로 올라온다.
     */
    fun existsByNicknameExcludingMember(nickname: String, memberId: Long): Boolean

    /** managed 엔티티를 로드해 닉네임을 갱신(dirty-check). */
    fun updateNickname(memberId: Long, nickname: String)

    /** managed 엔티티를 로드해 프로필 이미지 키를 갱신(dirty-check). null이면 제거다. */
    fun updateProfileImage(memberId: Long, objectKey: String?)

    /** 현재 프로필 이미지 키. 교체 시 이전 오브젝트를 정리하기 위해 읽는다. 없으면 null. */
    fun findProfileImageKey(memberId: Long): String?
}

/**
 * 유니크 제약 위반이 애플리케이션 어휘로 올라온 것. 어댑터가 DB 예외를 이걸로 옮긴다 —
 * 애플리케이션이 JPA/JDBC 예외 타입을 알 이유가 없다.
 */
class NicknameConflictException(message: String) : RuntimeException(message)

/**
 * 약관 동의 **현재 상태** 포트(R9~R12). 이력이 아니다 — 약관 하나에 한 행이며 재동의·철회는
 * 그 행의 갱신이다.
 *
 * **이력은 감사 로그(AuditLogPort)가 갖는다.** 이 테이블은 회원 삭제 시 FK 자식이라 함께
 * 지워지므로, 철회·탈퇴처럼 증빙이 가장 필요한 사건이 오히려 사라진다. 감사 로그는 FK가
 * 없어 남는다. 상태와 이력을 나눠 두는 이유이며, 상태 조회는 잦아서 전용 테이블이 필요하다.
 */
interface MemberAgreementRepositoryPort {
    /**
     * 회원의 (termsCode, termsVersion)별 현재 판단.
     * 행이 없는 약관은 결과에 키가 없으며 그것이 미동의 판정이다.
     */
    fun findCurrentAgreements(memberId: Long): Map<TermsKey, AgreementRecord>

    /** 동의/철회를 반영한다. 같은 약관의 기존 판단은 덮어쓴다. */
    fun upsert(memberId: Long, agreements: List<Agreement>)
}

/** 약관 상태 조회의 키. 코드만으로는 부족하다 — 버전이 올라가면 다른 동의다. */
data class TermsKey(val code: String, val version: String)

/** 특정 약관 버전에 대한 현재 판단. */
data class AgreementRecord(val agreed: Boolean, val agreedAt: Instant)
