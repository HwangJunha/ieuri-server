package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.toComment
import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.adapter.persistence.mapper.writeTo
import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.application.comment.required.CommentRepository
import org.around.ieuri.community.domain.comment.Comment
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CommentPersistenceAdapter : CommentRepository {

    override fun findById(commentId: Long): Comment? = CommentTable.selectAll()
        .where { (CommentTable.id eq commentId) and CommentTable.deletedAt.isNull() }
        .singleOrNull()?.toComment()

    override fun insert(comment: Comment): Long = CommentTable.insertReturning(listOf(CommentTable.id)) { comment.writeTo(it) }
        .single()[CommentTable.id]

    override fun hideIfVisible(commentId: Long): Long? = CommentTable.updateReturning(
        returning = listOf(CommentTable.postId),
        where = { (CommentTable.id eq commentId) and CommentTable.deletedAt.isNull() and (CommentTable.hidden eq false) },
    ) {
        it[hidden] = true
        it[updatedAt] = CurrentTimestampWithTimeZone
    }.singleOrNull()?.get(CommentTable.postId)

    /** 숨김과 동시에 와도 행 잠금이 둘을 줄 세운다 — 뒤에 온 쪽은 앞의 결과를 보고 판정한다. */
    override fun markDeletedWasCounted(commentId: Long, deletedAt: Instant): Boolean = CommentTable.updateReturning(
        returning = listOf(CommentTable.hidden),
        where = { (CommentTable.id eq commentId) and CommentTable.deletedAt.isNull() },
    ) {
        it[CommentTable.deletedAt] = deletedAt.toTimestamptz()
        it[updatedAt] = CurrentTimestampWithTimeZone
    }.singleOrNull()?.let { !it[CommentTable.hidden] } ?: false

    override fun findIdsByAuthor(authorId: Long): List<Long> = CommentTable.select(CommentTable.id)
        .where { (CommentTable.authorId eq authorId) and CommentTable.deletedAt.isNull() }
        .map { it[CommentTable.id] }
}
