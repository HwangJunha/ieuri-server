package org.around.ieuri.community.infrastructure.adapter.out.storage

import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.infrastructure.config.R2Properties
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
 * Cloudflare R2(S3 호환) 오브젝트 스토리지 어댑터.
 *
 * **서버는 이미지 바이트를 중계하지 않는다.** 업로드는 클라이언트가 presigned URL로 R2에
 * 직접 PUT하며, 이 어댑터는 서명 발급과 확인 단계의 메타데이터 조회·이동만 한다.
 * 이미지를 파싱하지도 않는다 — 확인 단계는 앞부분 몇 바이트만 읽는다.
 *
 * SigV4 서명을 직접 만들지 않고 SDK에 맡긴다. 정본 요청 문자열이 한 글자만 어긋나도
 * 조용히 실패하는 종류의 코드다.
 */
@Component
class R2ObjectStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: R2Properties,
) : ObjectStoragePort {

    /**
     * presigned PUT URL. **contentType·contentLength가 서명에 포함된다** — 선언과 다른
     * 형식이나 크기로 올리면 R2가 거부하므로, 발급 시점의 선언이 업로드 시점에 강제된다.
     * 이것이 이미지 위조와 크기 초과를 막는 첫 번째 층이다.
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

        // 남은 시간으로 환산한다. 이미 지난 시각이 들어오면 SDK가 거부하므로 최소 1초를 준다.
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

    /**
     * 앞부분 [length] 바이트만 Range로 읽는다. 전체를 받으면 10MiB 파일 하나에 그만큼의
     * 대역폭과 힙을 쓰는데, 매직바이트 판정에 필요한 것은 12바이트뿐이다.
     */
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
     * 복사 후 원본 삭제. S3에는 이동이 없다.
     *
     * 복사가 성공하고 삭제가 실패하면 `pending/`에 사본이 남는데, 그것은 고아 오브젝트일 뿐
     * 정합성 문제가 아니다 — R2의 lifecycle rule이 정리한다. 반대 순서로 쓰면 원본을 잃는다.
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

    /**
     * S3의 DeleteObject는 없는 키에도 성공을 돌려준다 — 그것이 멱등이라는 뜻이며
     * 호출자가 존재를 먼저 확인할 필요가 없다.
     */
    override fun delete(objectKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
        )
    }
}
