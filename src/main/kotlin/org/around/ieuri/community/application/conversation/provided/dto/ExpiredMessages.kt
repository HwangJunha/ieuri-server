package org.around.ieuri.community.application.conversation.provided.dto

/** 지운 만료 메시지 행. [photoObjectKeys]는 스토리지에서 지워야 할 키다 — 행이 사라져 되찾을 수 없다. */
data class ExpiredMessages(
    val deletedCount: Int,
    val photoObjectKeys: List<String>,
)
