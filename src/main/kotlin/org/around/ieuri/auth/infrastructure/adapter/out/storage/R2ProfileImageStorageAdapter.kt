package org.around.ieuri.auth.infrastructure.adapter.out.storage

import org.around.ieuri.auth.application.port.out.client.ProfileImageStoragePort
import org.around.ieuri.auth.infrastructure.config.ProfileImageProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant

/**
 * 프로필 이미지용 R2(S3 호환) 어댑터.
 *
 * **S3Client·S3Presigner 빈은 community와 공유하고 포트는 공유하지 않는다.** 그 둘은
 * AWS SDK 타입이라 도메인 경계와 무관하지만, 포트를 공유하면 auth가 community 패키지를
 * import하게 되어 나중에 auth를 떼어낼 수 없다.
 *
 * 서버는 이미지 바이트를 중계하지 않는다 — 업로드는 클라이언트가 presigned URL로 직접 PUT한다.
 */
@Component
class R2ProfileImageStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: ProfileImageProperties,
) : ProfileImageStoragePort {

    /**
     * presigned PUT URL. **contentType·contentLength가 서명에 포함된다** — 선언과 다르게
     * 올리면 R2가 거부하므로, 발급 시점의 선언이 업로드 시점에 강제된다.
     */
    override fun presignPut(
        objectKey: String,
        contentType: String,
        contentLength: Long,
        expiresAt: Instant,
    ): String {
        val put = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(objectKey)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()

        // 이미 지난 시각이 들어오면 SDK가 거부하므로 최소 1초를 준다.
        val ttl = Duration.between(Instant.now(), expiresAt).coerceAtLeast(Duration.ofSeconds(1))

        return s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build(),
        ).url().toString()
    }

    override fun findSize(objectKey: String): Long? =
        try {
            s3Client.headObject(
                HeadObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
            ).contentLength()
        } catch (e: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            // R2의 HeadObject는 부재를 404로 돌려주며 NoSuchKeyException으로 매핑되지 않는
            // 경우가 있다. 부재와 장애를 구분하되, 부재는 예외가 아니라 null이다.
            if (e.statusCode() == 404) null else throw e
        }

    /** 앞부분 [length] 바이트만 Range로 읽는다 — 매직바이트 판정에 12바이트면 충분하다. */
    override fun readPrefix(objectKey: String, length: Int): ByteArray? =
        try {
            s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(objectKey)
                    .range("bytes=0-${length - 1}")
                    .build(),
            ).asByteArray()
        } catch (e: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }

    /**
     * 복사 후 원본 삭제. S3에는 이동이 없다. 복사 성공·삭제 실패면 `pending/`에 사본이
     * 남는데 고아 오브젝트일 뿐이며 lifecycle rule이 정리한다. 반대 순서로 쓰면 원본을 잃는다.
     */
    override fun move(sourceKey: String, targetKey: String) {
        s3Client.copyObject(
            CopyObjectRequest.builder()
                .sourceBucket(properties.bucket)
                .sourceKey(sourceKey)
                .destinationBucket(properties.bucket)
                .destinationKey(targetKey)
                .build(),
        )
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(properties.bucket).key(sourceKey).build(),
        )
    }

    /** DeleteObject는 없는 키에도 성공을 돌려준다 — 호출자가 존재를 먼저 확인할 필요가 없다. */
    override fun delete(objectKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
        )
    }
}
