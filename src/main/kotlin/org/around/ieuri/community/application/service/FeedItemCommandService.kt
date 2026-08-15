package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.CreateFeedItemCommand
import org.around.ieuri.community.application.exception.PhotoNotConfirmedException
import org.around.ieuri.community.application.exception.PhotoRequiredException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.`in`.command.FeedItemCommandUseCase
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PhotoKeyAlreadyUsed
import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 피드 생성(SPEC-0017 R1~R3).
 *
 * **개수 판정을 도메인보다 먼저 한다** — 도메인의 require는 IllegalArgumentException 하나로
 * 뭉뚱그려지는데, 계약은 사진 부족(PHOTO_REQUIRED)과 초과(TOO_MANY_PHOTOS)를 서로 다른
 * 코드로 구분한다(AC2-3·2-4).
 *
 * 사진 키 검사는 [PostCommandService]와 같은 층위 구분을 따른다 — 소유권·확정 여부는 도메인이
 * 키 문자열만으로 보고, 실제 업로드 여부는 여기서 저장소에 묻는다. 다만 피드에는 하나가 더
 * 있다: **재사용 판정**이다. 이미 다른 피드에 실린 키는 거부되며(AC3-3), 그 판정은 저장소의
 * 유일 제약이 맡는다 — 미리 조회해 확인하면 경합에서 두 피드가 같은 키를 나눠 갖는다.
 */
@Service
class FeedItemCommandService(
    private val feedItemRepositoryPort: FeedItemRepositoryPort,
    private val spotLookupPort: SpotLookupPort,
    private val objectStoragePort: ObjectStoragePort,
) : FeedItemCommandUseCase {

    @Transactional
    override fun createFeedItem(command: CreateFeedItemCommand, memberId: Long): Long {
        // 사진 개수를 도메인보다 먼저 본다 — 계약이 부족과 초과를 다른 코드로 구분한다.
        if (command.photoKeys.isEmpty()) throw PhotoRequiredException()
        if (command.photoKeys.size > FeedItem.MAX_PHOTOS) throw TooManyPhotosException()

        // **spot은 필수다.** 존재하지 않으면 없는 장소에 묶인 피드가 만들어진다(AC1-3).
        // 도메인은 다른 애그리게이트의 존재를 스스로 확인하지 않으므로 여기서 묻는다.
        spotLookupPort.findSummary(command.catalogId) ?: throw SpotNotFoundException()

        // 하나라도 어긋나면 피드 전체를 생성하지 않는다 — 부분 생성은 없다.
        val photoKeys = command.photoKeys.map { verifiedPhotoKey(it, memberId) }

        val feedItem = FeedItem.create(
            catalogId = command.catalogId,
            authorId = memberId,
            body = command.body,
            photoKeys = photoKeys,
        )
        val saved = try {
            feedItemRepositoryPort.save(feedItem)
        } catch (e: PhotoKeyAlreadyUsed) {
            // 이미 다른 피드에 실린 키다(AC3-3). 미확정 키와 같은 코드로 답한다 —
            // 구분해 알려 주면 남의 키가 어디에 쓰였는지 캐낼 수 있다.
            throw PhotoNotConfirmedException()
        }
        return requireNotNull(saved.id) { "저장된 피드에는 id가 있어야 한다." }
    }

    /**
     * 키의 형식·소유권·확정 여부를 판정하고 실제 오브젝트가 있는지까지 본다.
     * 실패를 한 코드로 뭉뚱그리는 이유는 남의 키가 존재하는지 캐낼 수 있어서다.
     */
    private fun verifiedPhotoKey(raw: String, memberId: Long): PhotoObjectKey {
        val key = PhotoObjectKey.parse(raw) ?: throw PhotoNotConfirmedException()
        // 소유권과 확정 여부는 저장소를 묻지 않고 판정한다.
        if (!key.ownedBy(memberId) || !key.confirmed) throw PhotoNotConfirmedException()
        if (objectStoragePort.findSize(key.value) == null) throw PhotoNotConfirmedException()
        return key
    }
}
