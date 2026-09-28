package org.around.ieuri.auth.domain.vo

/** 표시용 닉네임. 회원 간 고유하지 않다(SPEC-0032 R31). 앞뒤 공백은 제거한다. */
@JvmInline
value class Nickname private constructor(val value: String) {
    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 20

        operator fun invoke(raw: String): Nickname {
            val v = raw.trim()
            require(v.length >= MIN_LENGTH) { "닉네임은 ${MIN_LENGTH}자 이상이어야 합니다." }
            require(v.length <= MAX_LENGTH) { "닉네임은 ${MAX_LENGTH}자를 넘을 수 없습니다." }
            return Nickname(v)
        }
    }
}
