package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.domain.comment.Comment
import org.around.ieuri.community.domain.post.Post
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 글 한 행. 카테고리·장소·태그·사진은 읽기 쪽 필드 리졸버가 따로 가져온다. */
fun ResultRow.toPost(): Post = Post(
    id = this[PostTable.id],
    authorId = this[PostTable.authorId],
    title = this[PostTable.title],
    body = this[PostTable.body],
    edited = this[PostTable.edited],
    hidden = this[PostTable.hidden],
    deletedAt = this[PostTable.deletedAt]?.toInstant(),
)

fun ResultRow.toComment(): Comment = Comment(
    id = this[CommentTable.id],
    postId = this[CommentTable.postId],
    parentCommentId = this[CommentTable.parentCommentId],
    authorId = this[CommentTable.authorId],
    body = this[CommentTable.body],
    hidden = this[CommentTable.hidden],
    deletedAt = this[CommentTable.deletedAt]?.toInstant(),
)

/** `timestamptz` 열에 쓸 값. 오프셋은 UTC로 고정한다(DB가 시점으로 저장한다). */
fun Instant.toTimestamptz(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
