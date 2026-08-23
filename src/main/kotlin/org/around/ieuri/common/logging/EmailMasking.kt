package org.around.ieuri.common.logging

/**
 * 로그에 남길 이메일을 가린다. `ab***@도메인` 형태로, 로컬파트 앞 2자와 도메인만 남는다.
 * `ab@x.com`처럼 로컬파트가 2자 이하면 앞 2자 규칙이 아무것도 가리지 못하므로 전부 가린다.
 *
 * 이메일이 아닌 값(`@` 없음·빈 문자열·null)에도 예외를 던지지 않고 `***`를 반환한다 —
 * 마스킹이 실패해 발송 경로가 깨지면 로그를 남기려다 로그를 잃는다.
 * `@`가 여러 개면 마지막 것을 도메인 구분자로 본다.
 */
fun maskEmail(value: String?): String {
    if (value.isNullOrBlank()) return "***"
    val at = value.lastIndexOf('@')
    if (at <= 0 || at == value.length - 1) return "***"

    val local = value.substring(0, at)
    val domain = value.substring(at)
    return if (local.length <= 2) "***$domain" else "${local.take(2)}***$domain"
}
