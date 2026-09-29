package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostHideRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.ReportRepositoryPort
import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.domain.vo.SpotPoint
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostHideEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPostTag
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toTagEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.CommentJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostChipJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostPhotoJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostSpotJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostTagJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ReportJpaRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 글 적재. 갱신은 관리 엔티티에 도메인 변경을 얹어 dirty-checking으로 UPDATE를 낸다. */
@Component
class PostPersistenceAdapter(
    private val repository: PostJpaRepository,
    private val chipRepository: PostChipJpaRepository,
    private val spotRepository: PostSpotJpaRepository,
    private val photoRepository: PostPhotoJpaRepository,
    private val tagRepository: PostTagJpaRepository,
) : PostRepositoryPort {

    /**
     * 완전한 애그리게이트를 복원한다(조회 5회). 카운터·존재 확인은
     * PostCounterPort·PostModerationPort를 쓴다.
     */
    override fun findById(postId: Long): Post? {
        val entity = repository.findByIdAndDeletedAtIsNull(postId) ?: return null
        // 좌표까지 복원한다 — 빠뜨리면 수정 시 장소 연결 행이 지워진다.
        val spot = spotRepository.findById(postId).orElse(null)
        return entity.toDomain(
            category = requireNotNull(chipRepository.findById(postId).orElse(null)) {
                "글($postId)에 카테고리 연결이 없다."
            }.category,
            catalogId = spot?.catalogId,
            spotPoint = spot?.let { SpotPoint(longitude = it.mapX, latitude = it.mapY) },
            tags = tagRepository.findByPostIdOrderByIdAsc(postId).map { it.toPostTag() },
            photoKeys = photoRepository.findByPostIdOrderByPositionAsc(postId)
                .map { it.toPhotoKey() },
        )
    }

    override fun save(post: Post): Post {
        val id = post.id ?: return create(post)

        val entity = repository.findById(id).orElseThrow {
            IllegalStateException("갱신하려는 글($id)이 존재하지 않는다.")
        }
        entity.title = post.title
        entity.body = post.body
        entity.edited = post.edited
        entity.likeCount = post.likeCount
        entity.commentCount = post.commentCount
        entity.hidden = post.hidden
        entity.deletedAt = post.deletedAt
        replacePhotos(id, post)
        replaceTags(id, post)
        replaceChip(id, post)
        replaceSpot(id, post)
        return post
    }

    /** 글을 먼저 저장해 id를 받은 뒤 자식을 붙인다(순서 고정). 장소가 없으면 행을 만들지 않는다. */
    private fun create(post: Post): Post {
        val saved = repository.save(post.toNewEntity())
        val postId = requireNotNull(saved.id) { "저장된 글에는 id가 있어야 한다." }
        chipRepository.save(post.toChipEntity(postId))
        post.toSpotEntity(postId)?.let { spotRepository.save(it) }
        photoRepository.saveAll(post.toPhotoEntities(postId))
        tagRepository.saveAll(post.toTagEntities(postId))
        return saved.toDomain(
            category = post.category,
            catalogId = post.catalogId,
            spotPoint = post.spotPoint,
            tags = post.tags,
            photoKeys = post.photoKeys,
        )
    }

    /**
     * 사진은 지우고 다시 넣는다.
     * - 삭제 flush를 빼면 Hibernate가 INSERT를 먼저 내보내 (post_id, position) 유일 제약에 걸린다.
     */
    private fun replacePhotos(postId: Long, post: Post) {
        photoRepository.deleteByPostId(postId)
        photoRepository.flush()
        photoRepository.saveAll(post.toPhotoEntities(postId))
    }

    /** 태그도 사진처럼 flush 후 전량 교체한다. */
    private fun replaceTags(postId: Long, post: Post) {
        tagRepository.deleteByPostId(postId)
        tagRepository.flush()
        tagRepository.saveAll(post.toTagEntities(postId))
    }

    /** 카테고리 연결. PK가 post_id라 지우고 넣지 않고 기존 행의 값만 바꾼다(PK 충돌). */
    private fun replaceChip(postId: Long, post: Post) {
        val chip = chipRepository.findById(postId).orElse(null)
        if (chip == null) {
            chipRepository.save(post.toChipEntity(postId))
        } else {
            chip.category = post.category
        }
    }

    /**
     * 장소 연결. 장소를 뗐으면 행을 지운다.
     * - geography 컬럼은 트리거가 map_x/map_y에서 파생시킨다.
     */
    private fun replaceSpot(postId: Long, post: Post) {
        val existing = spotRepository.findById(postId).orElse(null)
        val next = post.toSpotEntity(postId)
        when {
            next == null && existing != null -> spotRepository.delete(existing)

            next == null -> Unit

            existing == null -> spotRepository.save(next)

            else -> {
                // catalogId를 빠뜨리면 글이 옛 장소의 피드에 남는다(조회 축, SPEC-0019).
                existing.catalogId = next.catalogId
                existing.mapX = next.mapX
                existing.mapY = next.mapY
            }
        }
    }
}

/**
 * 파생 계수 증감. `count = count + 1` 원자적 UPDATE로 처리한다.
 * - 벌크 update는 영속성 컨텍스트를 우회한다 — 같은 트랜잭션에서 이 글을 엔티티로 다시 읽지 말 것.
 */
@Component
class PostCounterAdapter(
    private val queryFactory: JPAQueryFactory,
) : PostCounterPort {

    private val post = QPostEntity.postEntity

    @Transactional
    override fun increaseLikeCount(postId: Long) = increase(postId, post.likeCount)

    @Transactional
    override fun decreaseLikeCount(postId: Long) = decrease(postId, post.likeCount)

    @Transactional
    override fun increaseCommentCount(postId: Long) = increase(postId, post.commentCount)

    @Transactional
    override fun decreaseCommentCount(postId: Long) = decrease(postId, post.commentCount)

    @Transactional
    override fun increaseViewCount(postId: Long) = increase(postId, post.viewCount)

    /** 삭제된 글은 올리지 않는다. 대상이 없으면 0건이 갱신될 뿐 오류가 아니다. */
    private fun increase(postId: Long, counter: NumberPath<Int>) {
        queryFactory
            .update(post)
            .set(counter, counter.add(1))
            .where(post.id.eq(postId), post.deletedAt.isNull)
            .execute()
    }

    /** 0 아래로 내려가지 않는다 — 예외 없이 0에 머문다. */
    private fun decrease(postId: Long, counter: NumberPath<Int>) {
        queryFactory
            .update(post)
            .set(counter, counter.subtract(1))
            .where(post.id.eq(postId), post.deletedAt.isNull, counter.gt(0))
            .execute()
    }
}

/** 글 존재 확인과 숨김 전환. 애그리게이트를 복원하지 않는다. */
@Component
class PostModerationAdapter(
    private val queryFactory: JPAQueryFactory,
) : PostModerationPort {

    private val post = QPostEntity.postEntity

    @Transactional(readOnly = true)
    override fun exists(postId: Long): Boolean = queryFactory
        .selectOne()
        .from(post)
        .where(post.id.eq(postId), post.deletedAt.isNull)
        .fetchFirst() != null

    /** 이번 호출로 숨겨졌을 때만 true. 이미 숨겨진 글이면 false다. */
    @Transactional
    override fun hide(postId: Long): Boolean = queryFactory
        .update(post)
        .set(post.hidden, true)
        .where(post.id.eq(postId), post.deletedAt.isNull, post.hidden.isFalse)
        .execute() > 0
}

@Component
class CommentPersistenceAdapter(
    private val repository: CommentJpaRepository,
) : CommentRepositoryPort {

    override fun findById(commentId: Long): Comment? = repository.findByIdAndDeletedAtIsNull(commentId)?.toDomain()

    override fun save(comment: Comment): Comment {
        val id = comment.id ?: return repository.save(comment.toNewEntity()).toDomain()

        val entity = repository.findById(id).orElseThrow {
            IllegalStateException("갱신하려는 댓글($id)이 존재하지 않는다.")
        }
        entity.hidden = comment.hidden
        entity.deletedAt = comment.deletedAt
        return entity.toDomain()
    }
}

/** 좋아요 적재. 새로 생겼을 때만 true이며, 동시 insert의 유일 제약 위반도 false로 본다. */
@Component
class PostLikePersistenceAdapter(
    private val repository: PostLikeJpaRepository,
) : PostLikeRepositoryPort {

    override fun save(like: PostLike): Boolean {
        if (repository.existsByPostIdAndMemberId(like.postId, like.memberId)) return false
        return try {
            repository.saveAndFlush(like.toNewEntity())
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun delete(postId: Long, memberId: Long): Boolean = repository.deleteByPostIdAndMemberId(postId, memberId) > 0
}

/** 개별 글 숨김 적재. 좋아요와 같은 형태다(동시 insert 충돌도 false). */
@Component
class PostHidePersistenceAdapter(
    private val repository: PostHideJpaRepository,
) : PostHideRepositoryPort {

    override fun save(memberId: Long, postId: Long): Boolean {
        if (repository.existsByMemberIdAndPostId(memberId, postId)) return false
        return try {
            repository.saveAndFlush(PostHideEntity(memberId = memberId, postId = postId))
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun delete(memberId: Long, postId: Long): Boolean = repository.deleteByMemberIdAndPostId(memberId, postId) > 0
}

@Component
class BlockPersistenceAdapter(
    private val repository: BlockJpaRepository,
) : BlockRepositoryPort {

    override fun save(block: Block): Boolean {
        if (repository.existsByBlockerIdAndBlockedId(block.blockerId, block.blockedId)) return false
        return try {
            repository.saveAndFlush(block.toNewEntity())
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun delete(blockerId: Long, blockedId: Long): Boolean = repository.deleteByBlockerIdAndBlockedId(blockerId, blockedId) > 0
}

@Component
class ReportPersistenceAdapter(
    private val repository: ReportJpaRepository,
) : ReportRepositoryPort {

    override fun save(report: Report): Boolean {
        val duplicated = repository.existsByReporterIdAndTargetTypeAndTargetId(
            report.reporterId,
            report.targetType,
            report.targetId,
        )
        if (duplicated) return false
        return try {
            repository.saveAndFlush(report.toNewEntity())
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int = repository.countByTargetTypeAndTargetId(targetType, targetId).toInt()
}
