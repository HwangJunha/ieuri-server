package org.around.ieuri.community.application.post

import org.around.ieuri.community.application.lookup.provided.SpotLookupUseCase
import org.around.ieuri.community.application.photo.provided.UploadedPhotoUseCase
import org.around.ieuri.community.application.post.provided.PostCommandUseCase
import org.around.ieuri.community.application.post.provided.dto.CreatePostCommand
import org.around.ieuri.community.application.post.provided.dto.EditPostCommand
import org.around.ieuri.community.application.post.provided.dto.PostTargetCommand
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.shared.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.domain.photo.AttachedPhotos
import org.around.ieuri.community.domain.post.HiddenPostNotEditableException
import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.PostTag
import org.around.ieuri.community.domain.post.PostTags
import org.around.ieuri.community.domain.post.SpotPoint
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 글 쓰기·좋아요·개인 숨김.
 * - 외부 호출(R2 사진 확인)은 메서드 앞, 첫 쿼리 전에 둔다 — DB 연결은 첫 쿼리에서 잡힌다.
 * - 사진 확인 실패는 모두 PHOTO_NOT_UPLOADED다.
 */
@Service
@Transactional
class PostModifyService(
    private val postRepository: PostRepository,
    private val spotLookup: SpotLookupUseCase,
    private val uploadedPhotos: UploadedPhotoUseCase,
    private val clock: Clock,
) : PostCommandUseCase {

    override fun createPost(command: CreatePostCommand, memberId: Long, language: ContentLanguage): Long {
        val spot = command.catalogId?.let { findSpot(it, language) }
        val post = Post.write(memberId, command.title, command.body)
        val tags = PostTags.of(command.tags.map(PostTag::parse))
        val photos = AttachedPhotos.of(uploadedPhotos.parse(command.photoKeys), memberId)
        uploadedPhotos.requireUploaded(photos.keys)

        return postRepository.insert(post, command.category, spot, tags, photos)
    }

    /** null은 "건드리지 않는다"이고 빈 목록(전부 제거)과 다르다. 장소는 [EditPostCommand.changeSpot]이 true일 때만 바꾸며 null이면 뗀다. */
    override fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage) {
        // 개수·소유·확정을 먼저 판정하고 R2 확인은 그 뒤에 한다 — 남의 키 존재를 응답으로 드러내지 않는다.
        // 소유자는 수정자다(작성자만 수정할 수 있다).
        val photos = command.photoKeys?.let { AttachedPhotos.of(uploadedPhotos.parse(it), memberId) }
        val tags = command.tags?.map(PostTag::parse)?.let(PostTags::of)
        val spot = if (command.changeSpot) command.catalogId?.let { findSpot(it, language) } else null
        photos?.let { uploadedPhotos.requireUploaded(it.keys) }

        val post = postRepository.findById(command.postId) ?: throw PostNotFoundException()

        val edited = post.edit(memberId, command.title, command.body)

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

        val deleted = post.delete(memberId, Instant.now(clock))

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
        val summary = spotLookup.spot(catalogId, language) ?: throw SpotNotFoundInLocaleException()
        return PostSpot(catalogId, SpotPoint(longitude = summary.longitude, latitude = summary.latitude))
    }

    /** 작성 글은 사용자 삭제와 같은 도메인 판정으로 하나씩 지운다. 좋아요·숨김은 저장소 연산이라 한 번에 지우고 좋아요 수를 내린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int {
        var posts = 0
        postRepository.findIdsByAuthor(memberId).forEach { postId ->
            val post = postRepository.findById(postId) ?: return@forEach
            val deleted = post.delete(memberId, deletedAt)
            if (postRepository.markDeleted(postId, requireNotNull(deleted.deletedAt))) posts++
        }
        val likedPostIds = postRepository.removeLikesByMember(memberId)
        likedPostIds.forEach(postRepository::decreaseLikeCount)
        return posts + likedPostIds.size + postRepository.removeHidesByMember(memberId)
    }

    override fun increaseCommentCount(postId: Long) = postRepository.increaseCommentCount(postId)

    override fun decreaseCommentCount(postId: Long) = postRepository.decreaseCommentCount(postId)

    override fun hideReported(postId: Long): Boolean = postRepository.hideIfVisible(postId)

    override fun detachSpotsOfCatalogs(catalogIds: List<Long>): Int = postRepository.detachSpotsOfCatalogs(catalogIds)
}
