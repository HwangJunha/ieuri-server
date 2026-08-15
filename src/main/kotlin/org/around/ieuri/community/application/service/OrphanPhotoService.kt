package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.query.OrphanPhotoUseCase
import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 피드 제거로 주인을 잃은 사진의 삭제 대상 판정(SPEC-0021 R6).
 *
 * **판정과 실행을 나눈다.** 여기서는 지워도 되는 키를 고르기만 하고 스토리지는 건드리지
 * 않는다 — 실제 삭제는 운영 작업이며 ieuri-server#83이 맡는다.
 */
@Service
class OrphanPhotoService(
    private val orphanPhotoQueryPort: OrphanPhotoQueryPort,
) : OrphanPhotoUseCase {

    @Transactional(readOnly = true)
    override fun deletableFeedPhotoKeys(): List<String> {
        val orphans = orphanPhotoQueryPort.findOrphanPhotoKeys()
        if (orphans.isEmpty()) return emptyList()

        // 커뮤니티 글이 같은 키를 쓰고 있으면 대상에서 뺀다(AC6.2) — 업로드 확정 경로를
        // 공유하므로 중복이 성립하고, 지우면 살아 있는 글의 사진이 깨진다.
        val stillUsed = orphanPhotoQueryPort.findKeysUsedByPosts(orphans)
        return orphans.filterNot { it in stillUsed }
    }
}
