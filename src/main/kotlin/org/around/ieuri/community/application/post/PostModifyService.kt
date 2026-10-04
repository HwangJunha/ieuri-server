package org.around.ieuri.community.application.post

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.application.post.provided.dto.CreatePostCommand
import org.around.ieuri.community.application.post.provided.dto.EditPostCommand
import org.around.ieuri.community.application.post.provided.dto.PostTargetCommand
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.domain.post.HiddenPostNotEditableException
import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostPhotos
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.post.PostTags
import org.around.ieuri.community.domain.shared.PhotoNotUploadedException
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.SpotPoint
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 글 쓰기·좋아요·개인 숨김.
 * - 외부 호출(R2 사진 확인)은 메서드 앞, 첫 쿼리 전에 둔다 — DB 연결은 첫 쿼리에서 잡힌다.
 * - 사진 확인 실패는 모두 PHOTO_NOT_UPLOADED다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class PostModifyService(
    private val postRepository: PostRepository,
    private val spotLookupPort: SpotLookupPort,
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
) : PostCommandUseCase {

    override fun createPost(command: CreatePostCommand, memberId: Long, language: ContentLanguage): Long {
        val spot = command.catalogId?.let { findSpot(it, language) }
        val post = Post.write(memberId, command.title, command.body)
        val tags = PostTags.of(command.tags.map(PostTag::parse))
        val photos = PostPhotos.of(command.photoKeys.map(::parsePhotoKey), memberId)
        requireUploaded(photos.keys)

        return postRepository.insert(post, command.category, spot, tags, photos)
    }

    /** null은 "건드리지 않는다"이고 빈 목록(전부 제거)과 다르다. 장소는 [EditPostCommand.changeSpot]이 true일 때만 바꾸며 null이면 뗀다. */
    override fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage) {
        val photoKeys = command.photoKeys?.map(::parsePhotoKey)
        val tags = command.tags?.map(PostTag::parse)?.let(PostTags::of)
        val spot = if (command.changeSpot) command.catalogId?.let { findSpot(it, language) } else null
        photoKeys?.let(::requireUploaded)

        val post = postRepository.findById(command.postId) ?: throw PostNotFoundException()

        val edited = post.edit(memberId, command.title, command.body)
        val photos = photoKeys?.let { PostPhotos.of(it, post.authorId) }

        // 읽은 뒤 신고 누적으로 숨겨졌다면 0건이다.
        if (!postRepository.updateContent(edited)) throw HiddenPostNotEditableException()
        command.category?.let { postRepository.changeCategory(command.postId, it) }
        if (command.changeSpot) postRepository.changeSpot(command.postId, spot)
        tags?.let { postRepository.replaceTags(command.postId, it) }
        photos?.let { postRepository.replacePhotos(command.postId, it) }
    }

    override fun deletePost(command: PostTargetCommand, memberId: Long) {
        // 이미 삭제된 글은 조회되지 않으므로 재삭제는 POST_NOT_FOUND다.
        val post = postRepository.findById(command.postId) ?: throw PostNotFoundException()

        val deleted = post.delete(memberId, clockPort.now())

        postRepository.markDeleted(command.postId, requireNotNull(deleted.deletedAt))
    }

    override fun like(command: PostTargetCommand, memberId: Long): Int {
        requireExists(command.postId)

        if (postRepository.addLike(command.postId, memberId)) postRepository.increaseLikeCount(command.postId)

        return postRepository.likeCount(command.postId)
    }

    override fun unlike(command: PostTargetCommand, memberId: Long): Int {
        requireExists(command.postId)

        if (postRepository.removeLike(command.postId, memberId)) postRepository.decreaseLikeCount(command.postId)

        return postRepository.likeCount(command.postId)
    }

    override fun hide(command: PostTargetCommand, memberId: Long) {
        requireExists(command.postId)

        postRepository.addHide(memberId, command.postId)
    }

    override fun unhide(command: PostTargetCommand, memberId: Long) {
        postRepository.removeHide(memberId, command.postId)
    }

    override fun recordView(postId: Long) {
        postRepository.increaseViewCount(postId)
    }

    private fun requireExists(postId: Long) {
        if (!postRepository.exists(postId)) throw PostNotFoundException()
    }

    /** 장소는 활성 언어에서 해소되는 것만 붙일 수 있다. 좌표를 글에 복사한다. */
    private fun findSpot(catalogId: Long, language: ContentLanguage): PostSpot {
        val summary = spotLookupPort.findSummary(catalogId, language) ?: throw SpotNotFoundInLocaleException()
        return PostSpot(catalogId, SpotPoint(longitude = summary.longitude, latitude = summary.latitude))
    }

    /** 형식이 어긋난 키는 미업로드와 같은 코드로 다룬다(키 존재 여부를 노출하지 않는다). */
    private fun parsePhotoKey(raw: String): PhotoObjectKey = PhotoObjectKey.parse(raw) ?: throw PhotoNotUploadedException()

    /** 하나라도 없으면 글 전체를 만들지 않는다 — 부분 생성은 없다. */
    private fun requireUploaded(keys: List<PhotoObjectKey>) {
        if (keys.any { objectStoragePort.findSize(it.value) == null }) throw PhotoNotUploadedException()
    }
}
