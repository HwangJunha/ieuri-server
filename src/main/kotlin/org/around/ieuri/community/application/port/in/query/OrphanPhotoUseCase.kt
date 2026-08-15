package org.around.ieuri.community.application.port.`in`.query

/**
 * 피드 제거로 주인을 잃은 사진의 삭제 대상 판정(SPEC-0021 R6).
 *
 * **판정만 한다 — 스토리지는 건드리지 않는다.** 실제 삭제는 운영 작업이며 ieuri-server#83이
 * 맡는다. 규칙을 SQL이 아니라 여기 두는 이유는 그래야 검증할 수 있기 때문이다(AC6.1~6.3).
 */
interface OrphanPhotoUseCase {
    /**
     * 지워도 되는 오브젝트 키. 피드에만 쓰였던 키만 담긴다.
     *
     * **커뮤니티 글이 같은 키를 쓰고 있으면 제외한다**(AC6.2) — 업로드 확정 경로를 공유하므로
     * 중복이 성립하고, 지우면 살아 있는 글의 사진이 깨진다. 대상이 없으면 빈 목록이다.
     */
    fun deletableFeedPhotoKeys(): List<String>
}
