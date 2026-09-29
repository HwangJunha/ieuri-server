package org.around.ieuri.community.application.port.out.query

/** 주인 잃은 사진 키 조회(SPEC-0021 R6). */
interface OrphanPhotoQueryPort {
    /** 피드 제거 시점에 보존해 둔 키 전량. 대상이 없으면 빈 목록이다. */
    fun findOrphanPhotoKeys(): List<String>

    /** 보존 키 중 커뮤니티 글이 지금도 쓰고 있는 키. 판정에서 제외할 대상이다. */
    fun findOrphanKeysStillUsedByPosts(): Set<String>
}
