package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.CommentRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.ReportRepositoryPort
import org.around.ieuri.community.domain.aggregate.Block
import org.around.ieuri.community.domain.aggregate.Comment
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.aggregate.PostLike
import org.around.ieuri.community.domain.aggregate.Report
import org.around.ieuri.community.domain.vo.ReportTargetType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.CommentJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ReportJpaRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * 글 적재. 갱신은 **관리 엔티티에 도메인 변경을 얹어** dirty-checking으로 UPDATE를 낸다 —
 * 새 엔티티를 만들어 save하면 dirty-checking이 무의미해진다.
 *
 * 사진은 생성 시에만 적재한다 — 수정은 제목·본문만 바꾸므로 사진 구성은 나중에 바뀌지 않는다.
 */
@Component
class PostPersistenceAdapter(
    private val repository: PostJpaRepository,
) : PostRepositoryPort {

    override fun findById(postId: Long): Post? =
        repository.findByIdAndDeletedAtIsNull(postId)?.toDomain()

    override fun save(post: Post): Post {
        val id = post.id ?: return repository.save(post.toNewEntity()).toDomain()

        // 기존 글 갱신. 사진은 여전히 불변이다 — 수정은 제목·본문만 바꾼다.
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
        return entity.toDomain()
    }
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

    /**
     * **양방향 해석.** 내가 차단한 사람과 나를 차단한 사람을 합집합으로 돌려준다 —
     * 저장은 단방향 한 건이지만 필터는 양쪽 모두를 제외해야 한다.
     */
    override fun findRelatedMemberIds(memberId: Long): Set<Long> =
        repository.findAllByBlockerId(memberId).map { it.blockedId }.toSet() +
            repository.findAllByBlockedId(memberId).map { it.blockerId }.toSet()
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
