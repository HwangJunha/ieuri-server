package org.around.ieuri.community.application.qa.provided.dto

data class CreateSpotAnswerCommand(
    val questionId: Long,
    val body: String,
    val photoKeys: List<String>,
) {
    init {
        require(questionId > 0) { "questionId는 양수여야 합니다." }
    }
}
