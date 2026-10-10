package org.around.ieuri.community.application.photo.provided.dto

/** 여러 장을 한 번에 확인한다. 장별로 성패가 갈린다. */
data class ConfirmPostImageUploadsCommand(val objectKeys: List<String>)
