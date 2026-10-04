package org.around.ieuri.community.domain.shared

/** 장소 질문·답변·답글 본문. 트리밍 후 판정하며 위반은 INVALID_INPUT이다. 한도는 셋이 같다. */
object SpotQaText {
    const val MAX_LENGTH = 500

    fun question(raw: String): String = valid(raw, required = "질문을 입력해 주세요.", tooLong = "질문은 ${MAX_LENGTH}자를 넘을 수 없습니다.")

    /** 답변과 답글이 함께 쓴다. */
    fun answer(raw: String): String = valid(raw, required = "내용을 입력해 주세요.", tooLong = "내용은 ${MAX_LENGTH}자를 넘을 수 없습니다.")

    private fun valid(raw: String, required: String, tooLong: String): String {
        val text = raw.trim()
        if (text.isEmpty()) throw InvalidInputException(required)
        if (text.length > MAX_LENGTH) throw InvalidInputException(tooLong)
        return text
    }
}
