package org.around.ieuri.community.application.photo.provided.dto

data class PostImageConfirmFailure(
    val objectKey: String,
    /** 실패 사유. 미업로드·타인 키·형식 위반을 구분하지 않는다(키 존재를 캐낼 수 있어서다). */
    val code: String,
)
