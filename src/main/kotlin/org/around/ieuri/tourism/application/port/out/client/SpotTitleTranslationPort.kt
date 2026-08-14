package org.around.ieuri.tourism.application.port.out.client

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 제목 번역 아웃바운드 포트(SPEC-0019 R-6). 애플리케이션은 "이 KO 제목을 이 언어들로
 * 번역해 달라"고 요청할 뿐 **어떤 엔진인지 알지 않는다.**
 *
 * 이 경계가 있어야 단위 테스트에서 포트를 스텁으로 두고 대상 선택 로직을 검사할 수 있다 —
 * 어댑터 자체는 테스트 범위 밖이다(CLAUDE.md의 테스트 범위 규약).
 *
 * 표기 규칙(POL-048)은 어댑터가 프롬프트·사전으로 구현한다. 상호·고유명사는 음차,
 * 글로벌 브랜드는 원표기, 보통명사는 의미 번역이며, **의미 번역으로 상호를 바꾸지 않는다** —
 * `부산식당`은 `Busan Sikdang`이지 `Busan Restaurant`이 아니다.
 */
interface SpotTitleTranslationPort {

    /**
     * KO 카드(제목·주소)를 여러 언어로 번역한다.
     *
     * **주소가 함께 간다**(R-4) — 제목만 번역하면 사용자가 목록에서 이름은 읽어도 찾아갈 수
     * 없다. [koreanAddress]가 null이면 원문에 주소가 없는 것이며, 결과의 주소도 null이다.
     *
     * 번역하지 못한 언어는 결과에서 빠진다 — 빈 문자열이나 원문을 돌려주지 않는다.
     * 그래야 다음 실행이 그 쌍을 다시 대상으로 삼는다.
     */
    fun translate(
        koreanTitle: String,
        koreanAddress: String?,
        targets: Set<DomainLocale>,
    ): Map<DomainLocale, TranslatedCard>
}

/** 번역된 언어별 카드. 주소는 원문에 없으면 null이다. */
data class TranslatedCard(
    val title: String,
    val address: String?,
)
