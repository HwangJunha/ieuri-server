package org.around.ieuri.auth.domain.vo

/**
 * 표시용 닉네임. provider 제공값으로 자동 세팅한다(표시용이라 평문 저장).
 */
@JvmInline
value class Nickname private constructor(val value: String) {
    companion object {
        operator fun invoke(raw: String): Nickname {
            val v = raw.trim()
            require(v.isNotBlank()) { "닉네임은 비어 있을 수 없습니다." }
            require(v.length <= 50) { "닉네임은 50자를 넘을 수 없습니다." }
            return Nickname(v)
        }
    }
}
