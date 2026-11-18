package org.around.ieuri.community.adapter.storage

import org.around.ieuri.community.adapter.config.R2Properties
import org.around.ieuri.community.application.photo.required.ObjectStoragePort
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
 * 서명 발급과 확인 단계의 메타데이터 조회·이동만 하며 이미지 바이트를 중계하지 않는다.
 */
@Component
class R2ObjectStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: R2Properties,
) : ObjectStoragePort {

    /** presigned PUT URL. contentType·contentLength가 서명에 포함되어 다르게 올리면 R2가 거부한다. */
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

        // 이미 지난 시각이면 SDK가 거부하므로 최소 1초를 준다.
        val ttl = Duration.between(Instant.now(), expiresAt).coerceAtLeast(Duration.ofSeconds(1))

        return s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build(),
        ).url().toString()
    }

    override fun findSize(objectKey: String): Long? = try {
        s3Client.headObject(
            HeadObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
        ).contentLength()
    } catch (e: NoSuchKeyException) {
        null
    } catch (e: S3Exception) {
        // R2는 부재를 NoSuchKeyException이 아닌 404로 줄 때가 있다. 부재는 null, 그 밖은 장애다.
        if (e.statusCode() == 404) null else throw e
    }

    /** 앞부분 [length] 바이트만 Range로 읽는다. 없으면 null이다. */
    override fun readPrefix(objectKey: String, length: Int): ByteArray? = try {
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
     * 복사 후 원본 삭제(S3에는 이동이 없다).
     * - 순서를 뒤집으면 원본을 잃는다. 삭제 실패로 남은 사본은 R2 lifecycle rule이 정리한다.
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

    /** 멱등이다 — 없는 키에도 성공한다. */
    override fun delete(objectKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
        )
    }
}
