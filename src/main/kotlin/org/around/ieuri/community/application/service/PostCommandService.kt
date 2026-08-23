package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.exception.HiddenPostNotEditableException
import org.around.ieuri.community.application.exception.NotPostOwnerException
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.PostBodyLengthInvalidException
import org.around.ieuri.community.application.exception.InvalidTagException
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
 * 글 생성·삭제.
 *
 * 사진 키에 대한 **두 검사는 계층이 다르다**:
 *  - 키 소유권(이 키가 호출자에게 발급된 것인가) → 도메인. 키 문자열만 보며 포트 호출이 없다.
 *  - 업로드 여부(그 키에 실제 객체가 있는가)     → 여기. ObjectStoragePort에 묻는다.
 *
 * 소유권을 먼저 본다. 둘 다 실패 코드는 PHOTO_NOT_UPLOADED 하나로 통일한다 — 구분해 알려
 * 주면 남의 오브젝트 키가 존재하는지 캐내는 수단이 된다.
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
        // 본문 판정을 도메인보다 먼저 한다 — 도메인의 require는 IllegalArgumentException 하나로
        // 뭉뚱그려지는데, 계약은 빈 본문과 길이 초과를 서로 다른 코드로 구분한다.
        val title = command.title.trim()
        if (title.isEmpty()) throw PostTitleRequiredException()
        if (title.length > Post.TITLE_MAX_LENGTH) throw PostTitleLengthInvalidException()

        val body = command.body.trim()
        if (body.isEmpty()) throw PostBodyRequiredException()
        if (body.length > Post.BODY_MAX_LENGTH) throw PostBodyLengthInvalidException()
        if (command.photoKeys.size > Post.MAX_PHOTOS) throw TooManyPhotosException()

        // **장소는 선택이다.** 준 경우에만 존재를 확인한다 — 부재가 곧 유효성 판정이다.
        // 좌표를 여기서 함께 받아 글에 복사한다 — 지역 피드가 tourism에 spot 목록을
        // 되묻지 않게 하는 값이며, 그래야 목록 상한이 피드의 한계가 되지 않는다.
        // 검사 기준은 **활성 언어다**(SPEC-0029 R-16) — 없는 카탈로그와 이 언어에 원본이
        // 없는 카탈로그를 포트가 똑같이 null로 돌려주므로 한 코드로 응답한다.
        val spotPoint = command.catalogId?.let { catalogId ->
            val summary = spotLookupPort.findSummary(catalogId, language)
                ?: throw SpotNotFoundInLocaleException()
            SpotPoint(longitude = summary.longitude, latitude = summary.latitude)
        }

        // 형식이 어긋난 태그는 조용히 버리지 않고 거부한다 — 유저가 입력한 것이 사라지면
        // 왜 안 붙었는지 알 수 없다.
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
     * 제목·본문 수정. 길이 판정을 도메인보다 먼저 해서 계약의 코드로 구분한다(생성과 같은 이유).
     *
     * 소유자가 아닌 경우와 숨겨진 글인 경우를 **서로 다른 코드로** 응답한다 — 남의 글인지는
     * 이미 ownedByViewer로 드러나 있어 숨길 것이 없고, 작성자에게는 왜 못 고치는지 알려야 한다.
     */
    @Transactional
    override fun editPost(command: EditPostCommand, memberId: Long, language: ContentLanguage) {
        val title = command.title.trim()
        if (title.isEmpty()) throw PostTitleRequiredException()
        if (title.length > Post.TITLE_MAX_LENGTH) throw PostTitleLengthInvalidException()

        val body = command.body.trim()
        if (body.isEmpty()) throw PostBodyRequiredException()
        if (body.length > Post.BODY_MAX_LENGTH) throw PostBodyLengthInvalidException()

        // **사진을 준 경우에만** 검증한다. null은 "건드리지 않는다"는 뜻이라 빈 배열과 다르다.
        val photoKeys = command.photoKeys?.let { raw ->
            if (raw.size > Post.MAX_PHOTOS) throw TooManyPhotosException()
            raw.map { verifiedPhotoKey(it, memberId) }
        }

        // 태그는 형식이 어긋나면 조용히 버리지 않고 거부한다 — 유저가 입력한 것이 사라지면
        // 왜 안 붙었는지 알 수 없다.
        val tags = command.tags?.map { raw ->
            PostTag.parseOrNull(raw) ?: throw InvalidTagException()
        }
        if (tags != null && tags.size > PostTag.MAX_PER_POST) throw TooManyTagsException()

        // 장소를 바꾼다면 좌표를 함께 받아 둔다 — 좌표 없이 장소만 바꾸면 그 글이 지역
        // 피드의 거리순에서 조용히 사라진다.
        // 생성과 같이 **활성 언어 기준으로** 확인한다(SPEC-0029 R-16).
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

    /**
     * 키의 형식·소유권·확인 여부를 판정하고 실제 오브젝트가 있는지까지 본다.
     * 세 실패를 한 코드로 뭉뚱그리는 이유는 남의 키가 존재하는지 캐낼 수 있어서다.
     */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotUploadedException()
        // 소유권과 확인 여부는 저장소를 묻지 않고 판정한다.
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
