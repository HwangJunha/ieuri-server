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
 * 약관 동의 이력 포트(R9~R12). **append-only다** — 철회도 `agreed = false` 행을 추가하는
 * 것이지 기존 행을 고치거나 지우는 것이 아니다. 동의 이력은 법적 증빙이다.
 */
interface MemberAgreementRepositoryPort {
    /**
     * 회원이 각 (termsCode, termsVersion)에 대해 **가장 최근에** 남긴 판단.
     * 이력이 없는 약관은 결과에 키가 없으며 그것이 미동의 판정이다.
     */
    fun findLatestAgreements(memberId: Long): Map<TermsKey, AgreementRecord>

    /** 동의/비동의 행을 추가한다. 기존 행은 건드리지 않는다. */
    fun append(memberId: Long, agreements: List<Agreement>)
}

/** 약관 이력 조회의 키. 코드만으로는 부족하다 — 버전이 올라가면 다른 동의다. */
data class TermsKey(val code: String, val version: String)

/** 특정 약관 버전에 대한 가장 최근 판단. */
data class AgreementRecord(val agreed: Boolean, val agreedAt: Instant)
