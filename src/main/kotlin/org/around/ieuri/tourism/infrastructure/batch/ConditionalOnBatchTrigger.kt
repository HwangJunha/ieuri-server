package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * 외부 트리거(CronJob) 모드에서만 빈을 만든다 — **두 조건의 AND**다:
 *  - `enabled=true`         : 배치 기능 자체가 켜져 있고(= batch 프로파일로 떴고)
 *  - `scheduler-enabled=false` : 스케줄을 앱이 아니라 외부가 소유한다
 *
 * 같은 애노테이션(`@ConditionalOnProperty`)을 한 요소에 두 번 붙일 수 없어 이렇게 합성한다.
 * `enabled` 조건을 빠뜨리면 API 서버(enabled=false)에서도 `matchIfMissing=true` 때문에
 * 러너가 살아나 `SpotSyncLauncher` 주입 실패로 **부팅이 깨진다.**
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "ieuri.tourapi.sync", name = ["enabled"], havingValue = "true")
@ConditionalOnSchedulerDisabled
annotation class ConditionalOnBatchTrigger

/** `scheduler-enabled=false`(또는 미지정)일 때만. 위 애노테이션과 합성해 쓴다. */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "ieuri.tourapi.sync",
    name = ["scheduler-enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
annotation class ConditionalOnSchedulerDisabled
