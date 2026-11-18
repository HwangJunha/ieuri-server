package org.around.ieuri.auth.domain.member

/**
 * 시행 중인 약관 — 코드마다 가장 큰 버전 하나.
 * - 버전은 문자열로 비교한다("1.10" < "1.9"). 순서는 받은 목록의 코드 첫 등장 순이다.
 */
class ActiveTerms private constructor(val all: List<Terms>) {

    val requiredCodes: Set<String> get() = all.filter { it.required }.map { it.code }.toSet()

    fun find(code: String): Terms? = all.firstOrNull { it.code == code }

    /** 약관마다 회원의 동의를 얹는다. 버전이 다른 동의는 미동의다 — 이전 버전 동의는 이월되지 않는다. */
    fun statusOf(agreements: List<Agreement>): List<AgreementStatus> = all.map { terms ->
        val agreement = agreements.firstOrNull { it.termsCode == terms.code && it.termsVersion == terms.version }
        val agreed = agreement?.agreed == true
        AgreementStatus(terms = terms, agreed = agreed, agreedAt = if (agreed) agreement?.agreedAt else null)
    }

    companion object {
        /** [effective]는 시행일이 지난 약관 전부다. */
        fun of(effective: List<Terms>): ActiveTerms = ActiveTerms(
            effective.groupBy { it.code }.mapNotNull { (_, versions) -> versions.maxByOrNull { it.version } },
        )
    }
}
