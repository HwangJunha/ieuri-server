package org.around.ieuri.auth.application.port.out.persistence

import org.around.ieuri.auth.domain.member.Agreement
import java.time.Instant

/**
 * 프로필 쓰기 포트(SPEC-0011 R5·R6). 닉네임과 프로필 이미지는 member_profile 소유다.
 */
interface MemberProfileRepositoryPort {
    /** managed 엔티티를 로드해 닉네임을 갱신(dirty-check). */
    fun updateNickname(memberId: Long, nickname: String)

    /** managed 엔티티를 로드해 프로필 이미지 키를 갱신(dirty-check). null이면 제거다. */
    fun updateProfileImage(memberId: Long, objectKey: String?)

    /** 현재 프로필 이미지 키. 교체 시 이전 오브젝트를 정리하기 위해 읽는다. 없으면 null. */
    fun findProfileImageKey(memberId: Long): String?
}

/**
 * 약관 동의 현재 상태 포트(R9~R12). 약관 하나에 한 행이며 재동의·철회는 그 행의 갱신이다.
 * - 이력은 감사 로그(AuditLogPort)가 갖는다. 이 테이블은 회원 삭제 시 함께 지워진다.
 */
interface MemberAgreementRepositoryPort {
    /** 회원의 (termsCode, termsVersion)별 현재 판단. 행이 없는 약관은 키가 없으며 그것이 미동의다. */
    fun findCurrentAgreements(memberId: Long): Map<TermsKey, AgreementRecord>

    /** 동의/철회를 반영한다. 같은 약관의 기존 판단은 덮어쓴다. */
    fun upsert(memberId: Long, agreements: List<Agreement>)
}

/** 약관 상태 조회의 키. 코드만으로는 부족하다 — 버전이 올라가면 다른 동의다. */
data class TermsKey(val code: String, val version: String)

/** 특정 약관 버전에 대한 현재 판단. */
data class AgreementRecord(val agreed: Boolean, val agreedAt: Instant)
