package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 저장되지 않은 원본에 상세·축제를 붙이려 했다. */
class SpotNotSavedException(contentId: String, language: DomainLocale) : IllegalStateException("저장되지 않은 원본에는 상세를 붙일 수 없습니다: contentId=$contentId language=$language")
