package org.around.ieuri.common.logging

/**
 * 로그에 남길 이메일을 `ab***@도메인` 형태로 가린다. 로컬파트가 2자 이하면 전부 가린다.
 * - 이메일이 아닌 값(`@` 없음·빈 문자열·null)은 예외 없이 `***`다. `@`가 여러 개면 마지막 것이 구분자다.
 */
fun maskEmail(value: String?): String {
    if (value.isNullOrBlank()) return "***"
    val at = value.lastIndexOf('@')
    if (at <= 0 || at == value.length - 1) return "***"

    val local = value.substring(0, at)
    val domain = value.substring(at)
    return if (local.length <= 2) "***$domain" else "${local.take(2)}***$domain"
}
