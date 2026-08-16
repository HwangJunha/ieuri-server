package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QOrphanPhotoObjectEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostPhotoEntity
import org.springframework.stereotype.Component

/**
 * 주인 잃은 사진 키 조회 어댑터(SPEC-0021 R6). 판정은 애플리케이션이 하고 여기서는 읽기만 한다.
 */
@Component
class OrphanPhotoQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrphanPhotoQueryPort {

    private val orphan = QOrphanPhotoObjectEntity.orphanPhotoObjectEntity
    private val postPhoto = QPostPhotoEntity.postPhotoEntity

    override fun findOrphanPhotoKeys(): List<String> =
        queryFactory
            .select(orphan.objectKey)
            .from(orphan)
            .fetch()

    /** 두 표를 조인한다 — 보존 목록을 끌어와 `IN`으로 되물으면 키 개수만큼 SQL이 길어진다. */
    override fun findOrphanKeysStillUsedByPosts(): Set<String> =
        queryFactory
            .select(orphan.objectKey)
            .from(orphan)
            .join(postPhoto).on(postPhoto.objectKey.eq(orphan.objectKey))
            .fetch()
            .toSet()
}
