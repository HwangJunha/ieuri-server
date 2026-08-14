package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.command.CreateFeedItemCommand
import org.around.ieuri.community.application.exception.PhotoNotConfirmedException
import org.around.ieuri.community.application.exception.PhotoRequiredException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PhotoKeyAlreadyUsed
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.aggregate.FeedItem
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 피드 생성(SPEC-0017 R1·R3 — AC1-1·1-3, AC3-1~3-3). */
class FeedItemCommandServiceTest {

    private val memberId = 7L
    private val catalogId = 42L

    private val feedItemRepository = mockk<FeedItemRepositoryPort>()
    private val spotLookup = mockk<SpotLookupPort>()
    private val storage = mockk<ObjectStoragePort>()

    private val service = FeedItemCommandService(feedItemRepository, spotLookup, storage)

    private fun confirmedKey(owner: Long = memberId) = "images/community/$owner/${UUID.randomUUID()}"

    private fun pendingKey(owner: Long = memberId) = "pending/community/$owner/${UUID.randomUUID()}"

    private fun givenValidSpot() {
        every { spotLookup.findSummary(catalogId) } returns PostSpotView(
            id = catalogId,
            title = "경복궁",
            thumbnailImageUrl = "https://img/1.jpg",
            category = null,
            longitude = 127.0,
            latitude = 37.5,
        )
    }

    private fun givenUploadedObjects() {
        every { storage.findSize(any()) } returns 1_024L
    }

    private fun givenSaved(id: Long = 100L) {
        every { feedItemRepository.save(any()) } answers {
            val feedItem = firstArg<FeedItem>()
            FeedItem.restore(
                id = id,
                catalogId = feedItem.catalogId,
                authorId = feedItem.authorId,
                body = feedItem.body,
                photoKeys = feedItem.photoKeys,
                likeCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = java.time.Instant.EPOCH,
            )
        }
    }

    private fun command(
        body: String = "오늘의 경복궁",
        catalogId: Long = this.catalogId,
        photoKeys: List<String> = listOf(confirmedKey()),
    ) = CreateFeedItemCommand(body = body, catalogId = catalogId, photoKeys = photoKeys)

    /** AC1-1 — 유효한 spot과 사진 1장이면 생성되고 그 spot에 묶인다. */
    @Test
    fun `유효한 입력이면 피드가 생성된다`() {
        givenValidSpot()
        givenUploadedObjects()
        givenSaved()
        val saved = slot<FeedItem>()
        every { feedItemRepository.save(capture(saved)) } answers {
            FeedItem.restore(
                id = 100L,
                catalogId = saved.captured.catalogId,
                authorId = saved.captured.authorId,
                body = saved.captured.body,
                photoKeys = saved.captured.photoKeys,
                likeCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = java.time.Instant.EPOCH,
            )
        }

        val feedItemId = service.createFeedItem(command(), memberId)

        assertEquals(100L, feedItemId)
        assertEquals(catalogId, saved.captured.catalogId)
        assertEquals(memberId, saved.captured.authorId)
    }

    /** AC1-3 — 없는 장소에 묶인 피드가 만들어지지 않는다. */
    @Test
    fun `존재하지 않는 카탈로그면 거부된다`() {
        every { spotLookup.findSummary(catalogId) } returns null

        assertFailsWith<SpotNotFoundException> { service.createFeedItem(command(), memberId) }

        verify(exactly = 0) { feedItemRepository.save(any()) }
    }

    /**
     * AC2-3 — 사진 부족은 **초과와 다른 코드다.** 도메인의 require는 한 예외로 뭉뚱그려지므로
     * 서비스가 먼저 판정한다.
     */
    @Test
    fun `사진이 없으면 PHOTO_REQUIRED로 거부된다`() {
        assertFailsWith<PhotoRequiredException> {
            service.createFeedItem(command(photoKeys = emptyList()), memberId)
        }

        verify(exactly = 0) { feedItemRepository.save(any()) }
    }

    /** AC2-4 — 앞 5개만 취해 성공시키지 않는다. */
    @Test
    fun `사진이 6장이면 TOO_MANY_PHOTOS로 거부된다`() {
        assertFailsWith<TooManyPhotosException> {
            service.createFeedItem(command(photoKeys = List(6) { confirmedKey() }), memberId)
        }

        verify(exactly = 0) { feedItemRepository.save(any()) }
    }

    /** AC3-2 — 서버가 모르는 파일이 피드에 실리지 않는다. */
    @Test
    fun `확정되지 않은 키는 거부된다`() {
        givenValidSpot()

        assertFailsWith<PhotoNotConfirmedException> {
            service.createFeedItem(command(photoKeys = listOf(pendingKey())), memberId)
        }
    }

    @Test
    fun `타인의 키는 거부된다`() {
        givenValidSpot()

        assertFailsWith<PhotoNotConfirmedException> {
            service.createFeedItem(command(photoKeys = listOf(confirmedKey(owner = 999L))), memberId)
        }
    }

    /** 오브젝트가 실제로 없으면 확정 키 형태여도 거부한다. */
    @Test
    fun `업로드되지 않은 오브젝트는 거부된다`() {
        givenValidSpot()
        every { storage.findSize(any()) } returns null

        assertFailsWith<PhotoNotConfirmedException> { service.createFeedItem(command(), memberId) }
    }

    /**
     * AC3-3 — 이미 다른 피드에 실린 키는 거부된다. 저장소의 유일 제약이 판정하며, 서비스는
     * 그 신호를 **미확정 키와 같은 코드로** 옮긴다(어디에 쓰였는지 캐낼 수 없게).
     */
    @Test
    fun `이미 사용된 사진 키는 PHOTO_NOT_CONFIRMED로 거부된다`() {
        givenValidSpot()
        givenUploadedObjects()
        every { feedItemRepository.save(any()) } throws PhotoKeyAlreadyUsed()

        assertFailsWith<PhotoNotConfirmedException> { service.createFeedItem(command(), memberId) }
    }

    /** AC2-2 — 배열 순서가 표시 순서이므로 보존되어야 한다. */
    @Test
    fun `사진 5장의 순서가 보존된다`() {
        givenValidSpot()
        givenUploadedObjects()
        val keys = List(5) { confirmedKey() }
        val saved = slot<FeedItem>()
        every { feedItemRepository.save(capture(saved)) } answers {
            FeedItem.restore(
                id = 100L,
                catalogId = catalogId,
                authorId = memberId,
                body = saved.captured.body,
                photoKeys = saved.captured.photoKeys,
                likeCount = 0,
                hidden = false,
                deletedAt = null,
                createdAt = java.time.Instant.EPOCH,
            )
        }

        service.createFeedItem(command(photoKeys = keys), memberId)

        assertEquals(keys, saved.captured.photoKeys.map { it.value })
    }
}
