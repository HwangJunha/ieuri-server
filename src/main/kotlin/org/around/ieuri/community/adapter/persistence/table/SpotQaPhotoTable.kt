package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** 사진의 주인은 질문·답변·답글 중 하나다(다형 참조라 FK가 없다). */
object SpotQaPhotoTable : Table("community.community_spot_qa_photo") {
    val id = long("id").autoIncrement()
    val ownerType = varchar("owner_type", 10)
    val ownerId = long("owner_id")
    val objectKey = varchar("object_key", 512)
    val position = short("position")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_spot_qa_photo_position", ownerType, ownerId, position)
        index("idx_community_spot_qa_photo_owner", false, ownerType, ownerId, position)
        check("ck_community_spot_qa_photo_owner_type") { ownerType inList listOf("QUESTION", "ANSWER", "REPLY") }
        check("ck_community_spot_qa_photo_position") { position.between(0.toShort(), 4.toShort()) }
    }
}
