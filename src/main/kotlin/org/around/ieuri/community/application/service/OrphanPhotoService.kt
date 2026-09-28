package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 피드 제거로 주인을 잃은 사진의 삭제 대상 판정(SPEC-0021).
 * - 키를 고르기만 하고 스토리지는 건드리지 않는다. 실제 삭제와 진입점은 ieuri-server#83이 맡는다.
 */
@Service
class OrphanPhotoService(
    private val orphanPhotoQueryPort: OrphanPhotoQueryPort,
) {

    /** 지워도 되는 오브젝트 키(피드에만 쓰였던 키). 대상이 없으면 빈 목록이다. */
    @Transactional(readOnly = true)
    fun deletableFeedPhotoKeys(): List<String> {
        val orphans = orphanPhotoQueryPort.findOrphanPhotoKeys()
        if (orphans.isEmpty()) return emptyList()

        // 커뮤니티 글이 같은 키를 쓰고 있으면 뺀다 — 지우면 살아 있는 글의 사진이 깨진다.
        val stillUsed = orphanPhotoQueryPort.findOrphanKeysStillUsedByPosts()
        return orphans.filterNot { it in stillUsed }
    }
}
