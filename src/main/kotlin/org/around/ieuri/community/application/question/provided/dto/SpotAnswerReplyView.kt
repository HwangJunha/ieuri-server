package org.around.ieuri.community.application.question.provided.dto

import java.time.Instant

data class SpotAnswerReplyView(
    val id: Long,
    val answerId: Long,
    val body: String,
    val authorId: Long,
    val ownedByViewer: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
)
