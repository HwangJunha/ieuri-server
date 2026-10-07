package org.around.ieuri.community.adapter.persistence.mapper

import org.around.ieuri.community.adapter.persistence.table.CommentTable
import org.around.ieuri.community.adapter.persistence.table.MessageQuoteTable
import org.around.ieuri.community.adapter.persistence.table.MessageTable
import org.around.ieuri.community.adapter.persistence.table.PostTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.around.ieuri.community.application.conversation.provided.dto.ConversationSpotQaOriginView
import org.around.ieuri.community.application.conversation.provided.dto.MessageView
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import org.jetbrains.exposed.v1.core.ResultRow

// 읽기 행 → read DTO. 한 행이 가진 것만 옮기고 나머지(작성자 표시 정보·사진 등)는 필드 리졸버가 채운다.

fun ResultRow.toPostView(viewerId: Long): PostView = PostView(
    id = this[PostTable.id],
    title = this[PostTable.title],
    body = this[PostTable.body],
    authorId = this[PostTable.authorId],
    likeCount = this[PostTable.likeCount],
    commentCount = this[PostTable.commentCount],
    viewCount = this[PostTable.viewCount],
    ownedByViewer = this[PostTable.authorId] == viewerId,
    hidden = this[PostTable.hidden],
    edited = this[PostTable.edited],
    createdAt = this[PostTable.createdAt].toInstant(),
)

/** 작성자 닉네임·프로필은 비워 두고 리졸버가 채운다. */
fun ResultRow.toCommentView(viewerId: Long): CommentView = CommentView(
    id = this[CommentTable.id],
    parentCommentId = this[CommentTable.parentCommentId],
    body = this[CommentTable.body],
    author = MemberSummaryView(id = this[CommentTable.authorId], nickname = "", profileImageUrl = null),
    ownedByViewer = this[CommentTable.authorId] == viewerId,
    hidden = this[CommentTable.hidden],
    createdAt = this[CommentTable.createdAt].toInstant(),
)

fun ResultRow.toSpotQuestionView(viewerId: Long): SpotQuestionView = SpotQuestionView(
    id = this[SpotQuestionTable.id],
    catalogId = this[SpotQuestionTable.catalogId],
    body = this[SpotQuestionTable.body],
    authorId = this[SpotQuestionTable.authorId],
    resolved = this[SpotQuestionTable.resolved],
    answerCount = this[SpotQuestionTable.answerCount],
    ownedByViewer = this[SpotQuestionTable.authorId] == viewerId,
    hidden = this[SpotQuestionTable.hidden],
    createdAt = this[SpotQuestionTable.createdAt].toInstant(),
)

fun ResultRow.toSpotAnswerView(viewerId: Long): SpotAnswerView = SpotAnswerView(
    id = this[SpotAnswerTable.id],
    questionId = this[SpotAnswerTable.questionId],
    body = this[SpotAnswerTable.body],
    authorId = this[SpotAnswerTable.authorId],
    ownedByViewer = this[SpotAnswerTable.authorId] == viewerId,
    hidden = this[SpotAnswerTable.hidden],
    createdAt = this[SpotAnswerTable.createdAt].toInstant(),
)

fun ResultRow.toSpotAnswerReplyView(viewerId: Long): SpotAnswerReplyView = SpotAnswerReplyView(
    id = this[SpotAnswerReplyTable.id],
    answerId = this[SpotAnswerReplyTable.answerId],
    body = this[SpotAnswerReplyTable.body],
    authorId = this[SpotAnswerReplyTable.authorId],
    ownedByViewer = this[SpotAnswerReplyTable.authorId] == viewerId,
    hidden = this[SpotAnswerReplyTable.hidden],
    createdAt = this[SpotAnswerReplyTable.createdAt].toInstant(),
)

/** 복호화한 본문과 인용은 호출자가 넘긴다. */
fun ResultRow.toMessageView(viewerId: Long, decryptedBody: String, spotQaOrigin: ConversationSpotQaOriginView?): MessageView = MessageView(
    id = this[MessageTable.id],
    body = decryptedBody,
    senderId = this[MessageTable.senderId],
    ownedByViewer = this[MessageTable.senderId] == viewerId,
    viewerId = viewerId,
    createdAt = this[MessageTable.createdAt].toInstant(),
    photoKey = this[MessageTable.photoObjectKey],
    spotQaOrigin = spotQaOrigin,
)

/** 장소 답변 인용: title=장소명, primary=질문, secondary=답변, context=카탈로그, parent=질문 id. 복호화한 발췌를 받는다. */
fun ResultRow.toSpotQaOriginView(decryptedQuestionExcerpt: String, decryptedAnswerExcerpt: String): ConversationSpotQaOriginView = ConversationSpotQaOriginView(
    catalogId = requireNotNull(this[MessageQuoteTable.contextId]) { "장소 답변 인용에는 카탈로그 id가 있어야 한다." },
    spotTitle = this[MessageQuoteTable.titleSnapshot],
    questionId = requireNotNull(this[MessageQuoteTable.parentId]) { "장소 답변 인용에는 질문 id가 있어야 한다." },
    questionBodySnapshot = decryptedQuestionExcerpt,
    answerId = this[MessageQuoteTable.targetId],
    answerBodySnapshot = decryptedAnswerExcerpt,
)
