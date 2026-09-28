package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.exception.HiddenPostNotEditableException
import org.around.ieuri.community.application.exception.InvalidTagException
import org.around.ieuri.community.application.exception.NotPostOwnerException
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.PostBodyLengthInvalidException
import org.around.ieuri.community.application.exception.PostBodyRequiredException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.exception.PostTitleLengthInvalidException
import org.around.ieuri.community.application.exception.PostTitleRequiredException
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.exception.TooManyTagsException
import org.around.ieuri.community.application.port.`in`.command.PostCommandUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.domain.vo.PostTag
import org.around.ieuri.community.domain.vo.SpotPoint
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 글 생성·수정·삭제.
 * - 사진 키는 소유권(키 문자열)을 먼저, 업로드 여부(저장소)를 나중에 본다. 실패는 모두 PHOTO_NOT_UPLOADED다.
 */
@Service
class PostCommandService(
    private val postRepositoryPort: PostRepositoryPort,
    private val spotLookupPort: SpotLookupPort,
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
) : PostCommandUseCase {

    @Transactional
    override fun createPost(
        command: CreatePostCommand,
        memberId: Long,
        language: ContentLanguage,
    ): Long {
        // 빈 값과 길이 초과를 다른 코드로 내려고 도메인보다 먼저 판정한다.
        val title = command.title.trim()
        if (title.isEmpty()) throw PostTitleRequiredException()
        if (title.length > Post.TITLE_MAX_LENGTH) throw PostTitleLengthInvalidException()

        val body = command.body.trim()
        if (body.isEmpty()) throw PostBodyRequiredException()
        if (body.length > Post.BODY_MAX_LENGTH) throw PostBodyLengthInvalidException()
        if (command.photoKeys.size > Post.MAX_PHOTOS) throw TooManyPhotosException()

        // 장소는 선택이다. 준 경우 활성 언어로 확인하고 좌표를 글에 복사한다.
        // 없는 카탈로그와 이 언어에 원본이 없는 카탈로그는 같은 SPOT_NOT_FOUND_IN_LOCALE이다.
        val spotPoint = command.catalogId?.let { catalogId ->
            val summary = spotLookupPort.findSummary(catalogId, language)
                ?: throw SpotNotFoundInLocaleException()
            SpotPoint(longitude = summary.longitude, latitude = summary.latitude)
        }

        // 형식이 어긋난 태그는 버리지 않고 거부한다.
        val tags = command.tags.map { raw ->
            PostTag.parseOrNull(raw) ?: throw InvalidTagException()
        }

        // 하나라도 없으면 글 전체를 생성하지 않는다 — 부분 생성은 없다.
        val photoKeys = command.photoKeys.map { verifiedPhotoKey(it, memberId) }

        val post = Post.create(
            category = command.category,
            catalogId = command.catalogId,
            spotPoint = spotPoint,
            authorId = memberId,
            tags = tags,
            title = title,
            body = body,
            photoKeys = photoKeys,
        )
        val saved = postRepositoryPort.save(post)
        return requireNotNull(saved.id) { "저장된 글에는 id가 있어야 한다." }
    }

    /**
     * 글 수정. 길이 판정은 생성과 같이 도메인보다 먼저 한다.
     * - 소유자 아님(NotPostOwner)과 숨겨진 글(HiddenPostNotEditable)은 다른 코드다.
     */
    @Transactional
    override fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage) {
        val title = command.title.trim()
        if (title.isEmpty()) throw PostTitleRequiredException()
        if (title.length > Post.TITLE_MAX_LENGTH) throw PostTitleLengthInvalidException()

        val body = command.body.trim()
        if (body.isEmpty()) throw PostBodyRequiredException()
        if (body.length > Post.BODY_MAX_LENGTH) throw PostBodyLengthInvalidException()

        // null은 "건드리지 않는다"이고 빈 배열(전부 제거)과 다르다.
        val photoKeys = command.photoKeys?.let { raw ->
            if (raw.size > Post.MAX_PHOTOS) throw TooManyPhotosException()
            raw.map { verifiedPhotoKey(it, memberId) }
        }

        // 형식이 어긋난 태그는 버리지 않고 거부한다.
        val tags = command.tags?.map { raw ->
            PostTag.parseOrNull(raw) ?: throw InvalidTagException()
        }
        if (tags != null && tags.size > PostTag.MAX_PER_POST) throw TooManyTagsException()

        // 장소를 바꾸면 좌표도 함께 바꾼다 — 빠뜨리면 거리순 피드에서 글이 사라진다. 활성 언어로 확인한다.
        val newSpotPoint = command.catalogId?.let { catalogId ->
            val summary = spotLookupPort.findSummary(catalogId, language)
                ?: throw SpotNotFoundInLocaleException()
            SpotPoint(longitude = summary.longitude, latitude = summary.latitude)
        }

        val post = postRepositoryPort.findById(command.postId) ?: throw PostNotFoundException()
        if (post.authorId != memberId) throw NotPostOwnerException()
        if (!post.editBy(memberId, title, body)) throw HiddenPostNotEditableException()
        photoKeys?.let { post.replacePhotos(it) }
        command.category?.let { post.changeCategory(it) }
        // catalogId가 null이어도 changeSpot이면 장소를 떼는 것이다 — null 하나로는 구분되지 않는다.
        if (command.changeSpot) post.changeSpot(command.catalogId, newSpotPoint)
        tags?.let { post.replaceTags(it) }
        postRepositoryPort.save(post)
    }

    /** 키의 형식·소유권·확정 여부와 실제 오브젝트 존재를 본다. 실패는 모두 PhotoNotUploadedException이다. */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotUploadedException()
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotUploadedException()
        if (objectStoragePort.findSize(key.value) == null) throw PhotoNotUploadedException()
        return key
    }

    @Transactional
    override fun deletePost(command: DeletePostCommand, memberId: Long) {
        // 이미 삭제된 글은 조회되지 않으므로 재삭제는 POST_NOT_FOUND다.
        val post = postRepositoryPort.findById(command.postId) ?: throw PostNotFoundException()
        if (!post.deleteBy(memberId, clockPort.now())) throw NotPostOwnerException()
        postRepositoryPort.save(post)
    }
}
