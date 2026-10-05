package org.around.ieuri.community.application.qa.provided.dto

data class CreateSpotQuestionCommand(
    val catalogId: Long,
    val body: String,
    val photoKeys: List<String>,
) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}
