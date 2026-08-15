package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QOrphanPhotoObjectEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostPhotoEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 주인 잃은 사진 키 조회 어댑터(SPEC-0021 R6). 판정은 애플리케이션이 하고 여기서는 읽기만 한다.
 */
@Component
class OrphanPhotoQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrphanPhotoQueryPort {

    private val orphan = QOrphanPhotoObjectEntity.orphanPhotoObjectEntity
    private val postPhoto = QPostPhotoEntity.postPhotoEntity

    @Transactional(readOnly = true)
    override fun findOrphanPhotoKeys(): List<String> =
        queryFactory
            .select(orphan.objectKey)
            .from(orphan)
            .fetch()

    @Transactional(readOnly = true)
    override fun findKeysUsedByPosts(objectKeys: List<String>): Set<String> {
        if (objectKeys.isEmpty()) return emptySet()
        return queryFactory
            .select(postPhoto.objectKey)
            .from(postPhoto)
            .where(postPhoto.objectKey.`in`(objectKeys))
            .fetch()
            .toSet()
    }
}
