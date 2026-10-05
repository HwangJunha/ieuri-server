package org.around.ieuri.community.domain.shared

/** 미업로드 키와 타인·위조 키를 같은 코드로 다룬다(키 존재 여부를 노출하지 않는다). */
class PhotoNotUploadedException(message: String = "업로드되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_UPLOADED", message)
