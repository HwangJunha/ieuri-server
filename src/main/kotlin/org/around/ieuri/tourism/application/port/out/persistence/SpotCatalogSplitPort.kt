package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 잘못 접힌 카탈로그를 가르는 쓰기 포트(SPEC-0029).
 *
 * [SpotCatalogStorePort]와 갈라 둔 이유는 **쓰기의 성격이 다르기 때문이다** — 저쪽은 배정이
 * 부르는 상시 경로이고, 이쪽은 배정이 잘못 접은 것을 되돌리는 교정 경로다. 한 포트에 섞으면
 * 배정 쪽 구현이 교정용 연산을 얼떨결에 부르기 쉬워진다.
 */
interface SpotCatalogSplitPort {

    /** spot 행 여럿을 다른 카탈로그로 옮긴다. **이미 배정된 행을 덮어쓴다** — 배정과 반대다. */
    fun reassign(spotIds: List<Long>, catalogId: Long)

    /**
     * 옮겨 간 언어의 번역 행을 옛 카탈로그에서 지운다.
     *
     * **감춤(`visible=false`)이 아니라 삭제다.** 감춤은 "원본이 잠시 사라졌다"는 뜻이고 원본이
     * 돌아오면 되살아나야 하지만, 여기서 원본은 사라진 것이 아니라 **다른 카탈로그로 갔다.**
     * 남겨 두면 옛 카탈로그가 자기 것이 아닌 언어를 계속 주장하고, 그 언어의 원본이 새
     * 카탈로그에 있으므로 노출 갱신이 되살릴 근거도 없다.
     */
    fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>)
}
