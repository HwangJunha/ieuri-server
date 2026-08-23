package org.around.ieuri.community.application.port.out.query

/**
 * 주인 잃은 사진 키 조회(SPEC-0021 R6).
 */
interface OrphanPhotoQueryPort {
    /** 피드 제거 시점에 보존해 둔 키 전량. 대상이 없으면 빈 목록이다. */
    fun findOrphanPhotoKeys(): List<String>

    /**
     * 커뮤니티 글이 **지금도 쓰고 있는** 키. 판정에서 제외할 대상이다(AC6.2).
     *
     * 보존 목록을 애플리케이션으로 끌어와 `IN`으로 되묻지 않는다 — 그러면 키 개수만큼
     * SQL이 길어진다. 두 표를 조인해 겹치는 것만 돌려준다.
     */
    fun findOrphanKeysStillUsedByPosts(): Set<String>
}
