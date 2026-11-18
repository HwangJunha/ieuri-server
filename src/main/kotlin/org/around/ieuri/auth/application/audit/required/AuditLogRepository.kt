package org.around.ieuri.auth.application.audit.required

import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import java.time.Instant

/** 감사·접속·위치 조회 기록 저장소. 기록은 넣기만 하고, 보관 기간을 넘긴 것은 한 회차씩 지운다. */
interface AuditLogRepository {
    fun insert(log: MemberAuditLog)

    fun insert(log: MemberAccessLog)

    fun insert(log: LocationAccessLog)

    /** [cutoff]보다 먼저 만들어진 행을 최대 [limit]건 지우고 지운 수를 돌려준다. */
    fun deleteMemberAuditBefore(cutoff: Instant, limit: Int): Int

    fun deleteAccessBefore(cutoff: Instant, limit: Int): Int

    fun deleteLocationAccessBefore(cutoff: Instant, limit: Int): Int
}
