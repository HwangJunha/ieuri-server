package org.around.ieuri.community.application.block.required.dto

data class BlockedUsersSearch(
    val memberId: Long,
    val first: Int,
    val after: String?,
)
