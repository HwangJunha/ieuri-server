package org.around.ieuri.community.application.port.out.query

/**
 * 주인 잃은 사진 키 조회(SPEC-0021 R6). 판정은 애플리케이션이 하고 여기서는 재료만 읽는다.
 */
interface OrphanPhotoQueryPort {
    /** 피드 제거 시점에 보존해 둔 키. 대상이 없으면 빈 목록이다. */
    fun findOrphanPhotoKeys(): List<String>

    /**
     * [objectKeys] 중 커뮤니티 글이 **지금도 쓰고 있는** 키. 결과에 없는 키는 아무 글도
     * 쓰지 않는다는 뜻이다. 빈 입력이면 빈 집합이며 조회하지 않는다.
     */
    fun findKeysUsedByPosts(objectKeys: List<String>): Set<String>
}
