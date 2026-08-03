package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.around.ieuri.tourism.domain.aggregate.SpotReaction
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotReactionEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotReactionJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 좋아요/싫어요 저장 포트 구현. 도메인↔엔티티 매핑을 여기서 담당한다(별도 Mapper 클래스 없음, 프로젝트 관례).
 *
 * PK는 surrogate id지만 upsert 단위는 자연키(member_id, content_id, language, UNIQUE). 재평가(upsert)는
 * 자연키로 관리 엔티티를 로드해 reaction만 갱신하고 dirty-checking으로 UPDATE를 낸다(관례). 없으면 새 엔티티 persist.
 * 트랜잭션은 호출자(서비스)가 연다.
 */
@Component
class SpotReactionPersistenceAdapter(
    private val repository: SpotReactionJpaRepository,
    private val spotRepository: SpotJpaRepository,
) : SpotReactionPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun find(memberId: Long, contentId: String, language: DomainLocale): SpotReaction? =
        repository.findByMemberIdAndContentIdAndLanguage(memberId, contentId, language)?.toDomain()

    override fun save(reaction: SpotReaction) {
        val existing = repository.findByMemberIdAndContentIdAndLanguage(
            reaction.memberId, reaction.contentId, reaction.language,
        )
        if (existing != null) {
            // 관리 엔티티에 도메인 변경을 얹는다(dirty-checking → UPDATE). 자연키는 불변이라 reaction만 갱신.
            //
            // spot_id는 재설정하지 않는다. 부모가 정리로 사라졌다가 재적재되면 새 id를 받지만, 그
            // 삭제가 CASCADE로 이 반응 행까지 지우므로 낡은 포인터가 남을 수 없다 — 자연키 종속이
            // 아니라 CASCADE가 이것을 보장한다.
            existing.reaction = reaction.reaction
            return
        }
        // 신규 생성 시에만 부모 id가 필요하다(V19).
        //
        // **부모가 없으면 조용히 건너뛴다.** resolve 판정(AC-14b·SPOT_NOT_FOUND)은 이번 범위 밖이며,
        // [ReactToSpotService]가 "향후 place-resolution 레이어가 생기면 그 지점에서 거부한다"고
        // 명시한다. 어댑터가 앞당겨 예외를 던지면 기존에 성공하던 요청이 SPOT_NOT_FOUND가 되어
        // 관찰 가능한 계약이 바뀐다 — 그 판단은 어댑터가 아니라 유스케이스의 몫이다.
        val spotId = spotRepository.findIdByContentIdAndLanguage(reaction.contentId, reaction.language)
        if (spotId == null) {
            log.warn("spot 행이 없어 반응을 저장하지 않는다: language={}", reaction.language)
            return
        }
        repository.save(reaction.toNewEntity(spotId))
    }

    private fun SpotReactionEntity.toDomain(): SpotReaction =
        SpotReaction.record(
            memberId = memberId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )

    private fun SpotReaction.toNewEntity(spotId: Long): SpotReactionEntity =
        SpotReactionEntity(
            memberId = memberId,
            // 도메인에는 없는 값이다 — 부모 참조는 persistence 관심사이므로 매퍼 안에 가둔다(V19).
            spotId = spotId,
            contentId = contentId,
            language = language,
            reaction = reaction,
        )
}
