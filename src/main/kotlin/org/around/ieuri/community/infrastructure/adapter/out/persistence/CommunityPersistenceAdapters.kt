package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostHideRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.persistence.ReportRepositoryPort
import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.domain.vo.SpotPoint
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toChipEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPostTag
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toTagEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toSpotEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.CommentJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostChipJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.PostHideEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostPhotoJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostTagJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostSpotJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ReportJpaRepository
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 글 적재. 갱신은 **관리 엔티티에 도메인 변경을 얹어** dirty-checking으로 UPDATE를 낸다 —
 * 새 엔티티를 만들어 save하면 dirty-checking이 무의미해진다.
 *
 * 사진은 생성 시에만 적재한다 — 수정은 제목·본문만 바꾸므로 사진 구성은 나중에 바뀌지 않는다.
 */
@Component
class PostPersistenceAdapter(
    private val repository: PostJpaRepository,
    private val chipRepository: PostChipJpaRepository,
    private val spotRepository: PostSpotJpaRepository,
    private val photoRepository: PostPhotoJpaRepository,
    private val tagRepository: PostTagJpaRepository,
) : PostRepositoryPort {

    /**
     * **완전한 애그리게이트를 복원한다** — 그래서 조회가 다섯 번이다. 전체 상태가 필요한
     * 연산(글 수정)만 이 포트를 써야 하는 이유이며, 카운터 증감이나 존재 확인은
     * PostCounterPort·PostModerationPort로 간다.
     */
    override fun findById(postId: Long): Post? {
        val entity = repository.findByIdAndDeletedAtIsNull(postId) ?: return null
        // **좌표까지 함께 복원한다.** 빠뜨리면 장소를 바꾸지 않은 수정에서도 좌표가 null이 되어
        // 연결 행이 지워지고, 그 글이 지역 피드에서 조용히 사라진다.
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

    /**
     * 글을 먼저 저장해 id를 받은 뒤 자식을 붙인다 — 자식이 전부 글의 id를 필요로 하므로
     * 순서를 뒤집을 수 없다. 장소는 선택이라 없으면 행을 만들지 않는다.
     */
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
     * 사진은 **지우고 다시 넣는다.** 순서가 계약이라 부분 갱신으로는 position을 맞추기
     * 어렵고, 최대 5장이라 전량 교체의 비용이 무시할 만하다.
     *
     * 삭제를 flush하지 않으면 같은 (post_id, position)으로 INSERT가 먼저 나가 유일 제약에
     * 걸린다 — Hibernate는 INSERT를 DELETE보다 먼저 내보내기 때문이다.
     */
    private fun replacePhotos(postId: Long, post: Post) {
        photoRepository.deleteByPostId(postId)
        photoRepository.flush()
        photoRepository.saveAll(post.toPhotoEntities(postId))
    }

    /** 태그도 사진과 같은 이유로 전량 교체한다 — 최대 5개라 비용이 무시할 만하다. */
    private fun replaceTags(postId: Long, post: Post) {
        tagRepository.deleteByPostId(postId)
        tagRepository.flush()
        tagRepository.saveAll(post.toTagEntities(postId))
    }

    /**
     * 카테고리 연결. PK가 post_id라 **기존 행을 찾아 값만 바꾼다** — 지우고 다시 넣으면
     * 같은 PK로 INSERT가 먼저 나가 충돌한다.
     */
    private fun replaceChip(postId: Long, post: Post) {
        val chip = chipRepository.findById(postId).orElse(null)
        if (chip == null) {
            chipRepository.save(post.toChipEntity(postId))
        } else {
            chip.category = post.category
        }
    }

    /**
     * 장소 연결. **장소를 뗐으면 행을 지운다** — 남겨 두면 그 글이 지역 피드에 계속 뜬다.
     *
     * 좌표까지 함께 갱신한다. geography는 트리거가 map_x/map_y에서 파생시킨다.
     */
    private fun replaceSpot(postId: Long, post: Post) {
        val existing = spotRepository.findById(postId).orElse(null)
        val next = post.toSpotEntity(postId)
        when {
            next == null && existing != null -> spotRepository.delete(existing)
            next == null -> Unit
            existing == null -> spotRepository.save(next)
            else -> {
                // **catalogId를 빠뜨리면 장소를 바꿔도 글이 옛 장소의 피드에 남는다** —
                // 조회 축이 이 컬럼이기 때문이다(SPEC-0019 R-12).
                existing.catalogId = next.catalogId
                existing.mapX = next.mapX
                existing.mapY = next.mapY
            }
        }
    }
}

/**
 * 파생 계수 증감. **읽고 고쳐 쓰지 않는다** — 동시 요청이 서로의 증가를 덮어써 계수가
 * 실제보다 작아지기 때문이다. `count = count + 1` 한 문장을 DB가 원자적으로 처리한다.
 *
 * 벌크 update는 영속성 컨텍스트를 우회한다. 같은 트랜잭션에서 이 글을 **엔티티로** 다시 읽는
 * 곳이 없어야 한다는 뜻인데, 갱신값은 읽기 포트가 별도 쿼리로 가져오므로 문제가 없다.
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

    /**
     * **0 아래로 내려가지 않는다.** 조건절이 0인 행을 아예 건드리지 않으므로 CHECK 제약도
     * 걸리지 않는다 — 음수를 쓰려다 예외가 나는 대신 조용히 0으로 머문다.
     */
    private fun decrease(postId: Long, counter: NumberPath<Int>) {
        queryFactory
            .update(post)
            .set(counter, counter.subtract(1))
            .where(post.id.eq(postId), post.deletedAt.isNull, counter.gt(0))
            .execute()
    }
}

/**
 * 존재 확인과 상태 전환. 애그리게이트를 복원하지 않는다 — 신고의 대상 확인은 "있는가"만
 * 물으면 되는데 복원하면 태그·사진까지 읽는다.
 */
@Component
class PostModerationAdapter(
    private val queryFactory: JPAQueryFactory,
) : PostModerationPort {

    private val post = QPostEntity.postEntity

    @Transactional(readOnly = true)
    override fun exists(postId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(post)
            .where(post.id.eq(postId), post.deletedAt.isNull)
            .fetchFirst() != null

    /**
     * 이미 숨겨진 글은 조건절에서 걸러져 0건이 갱신되고 false가 된다. 호출자가 그 값으로
     * 중복 처리를 건너뛰므로, 신고가 더 쌓여도 댓글 수를 두 번 내리는 일이 없다.
     */
    @Transactional
    override fun hide(postId: Long): Boolean =
        queryFactory
            .update(post)
            .set(post.hidden, true)
            .where(post.id.eq(postId), post.deletedAt.isNull, post.hidden.isFalse)
            .execute() > 0
}

@Component
class CommentPersistenceAdapter(
    private val repository: CommentJpaRepository,
) : CommentRepositoryPort {

    override fun findById(commentId: Long): Comment? =
        repository.findByIdAndDeletedAtIsNull(commentId)?.toDomain()

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

/**
 * 좋아요 유일성. 멱등 판정을 저장소가 맡는다.
 *
 * exists 검사 뒤 insert 사이에 동시 요청이 끼어들 수 있어 유일 제약 위반도 함께 잡는다 —
 * 그 경우도 "이미 있었다"와 같은 결과이므로 오류가 아니다.
 */
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

    override fun delete(postId: Long, memberId: Long): Boolean =
        repository.deleteByPostIdAndMemberId(postId, memberId) > 0
}

/**
 * 글 숨김. 좋아요·차단과 같은 형태다 — exists 검사 뒤 insert 사이에 동시 요청이 끼어들 수
 * 있어 유일 제약 위반도 함께 잡는다. 그 경우도 "이미 숨겨져 있었다"와 같은 결과다.
 */
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

    override fun delete(memberId: Long, postId: Long): Boolean =
        repository.deleteByMemberIdAndPostId(memberId, postId) > 0
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

    override fun delete(blockerId: Long, blockedId: Long): Boolean =
        repository.deleteByBlockerIdAndBlockedId(blockerId, blockedId) > 0
}

@Component
class ReportPersistenceAdapter(
    private val repository: ReportJpaRepository,
) : ReportRepositoryPort {

    override fun save(report: Report): Boolean {
        val duplicated = repository.existsByReporterIdAndTargetTypeAndTargetId(
            report.reporterId, report.targetType, report.targetId,
        )
        if (duplicated) return false
        return try {
            repository.saveAndFlush(report.toNewEntity())
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    override fun countDistinctReporters(targetType: ReportTargetType, targetId: Long): Int =
        repository.countByTargetTypeAndTargetId(targetType, targetId).toInt()
}
