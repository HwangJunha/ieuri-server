package org.around.ieuri.tourism.application.footprint.required

/** 차단 판정. 양방향이라 누가 걸었는지는 묻지 않는다(POL-041). */
interface MapViewerBlockPort {

    fun isBlockedBetween(memberId: Long, counterpartId: Long): Boolean
}
