package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QOrphanPhotoObjectEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostPhotoEntity
import org.springframework.stereotype.Component

/** 주인 잃은 사진 키 조회(SPEC-0021 R6). 판정은 애플리케이션이 한다. */
@Component
class OrphanPhotoQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : OrphanPhotoQueryPort {

    private val orphan = QOrphanPhotoObjectEntity.orphanPhotoObjectEntity
    private val postPhoto = QPostPhotoEntity.postPhotoEntity

    override fun findOrphanPhotoKeys(): List<String> = queryFactory
        .select(orphan.objectKey)
        .from(orphan)
        .fetch()

    /** 고아 목록 중 아직 글 사진으로 쓰이는 키. */
    override fun findOrphanKeysStillUsedByPosts(): Set<String> = queryFactory
        .select(orphan.objectKey)
        .from(orphan)
        .join(postPhoto).on(postPhoto.objectKey.eq(orphan.objectKey))
        .fetch()
        .toSet()
}
