package org.around.ieuri.auth.domain.vo

/**
 * 표시용 닉네임. 회원 간 고유하며(SPEC-0011 R5) 표시용이라 평문 저장한다.
 *
 * 앞뒤 공백은 제거하고 저장한다 — 눈에 보이지 않는 차이로 다른 닉네임이 되면
 * 고유성 판정이 사용자 눈에 어긋나 보인다.
 */
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
