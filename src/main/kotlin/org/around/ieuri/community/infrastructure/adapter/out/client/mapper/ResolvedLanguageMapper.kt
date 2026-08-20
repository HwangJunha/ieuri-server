package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * tourism이 돌려준 언어 코드 → 커뮤니티의 [ContentLanguage] 변환(POL-033).
 *
 * 코드가 문자열로 건너오는 이유는 [org.around.ieuri.community.application.port.out.client.SpotLookupPort]
 * 주석과 같다 — 커뮤니티가 tourism의 `DomainLocale`을 import하면 도메인을 따로 떼어낼 수 없다.
 *
 * **모르는 코드는 폴백하지 않고 버린다.** [ContentLanguage.fromLanguageTag]는 미지원 태그를
 * EN으로 수렴시키는데, 여기서 그것을 쓰면 tourism에 언어가 하나 늘었을 때 그 언어의 카탈로그가
 * 전부 "영어에서 해소된다"로 기록되어 영어 사용자에게 읽을 수 없는 글이 뜬다. 없는 것으로
 * 두면 그 언어에서만 안 보일 뿐이고, 커뮤니티가 그 언어를 알게 되는 시점에 저절로 맞는다.
 */
fun Set<String>.toContentLanguages(): Set<ContentLanguage> =
    mapNotNullTo(mutableSetOf()) { code ->
        ContentLanguage.entries.firstOrNull { it.name == code }
    }
