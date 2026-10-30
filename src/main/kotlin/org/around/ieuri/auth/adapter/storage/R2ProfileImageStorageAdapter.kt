package org.around.ieuri.auth.adapter.storage
import org.around.ieuri.auth.adapter.config.ProfileImageProperties
import org.around.ieuri.auth.application.member.required.ProfileImageStoragePort
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

/** 프로필 이미지용 R2(S3 호환) 어댑터. S3Client·S3Presigner 빈은 community와 공유하되 포트는 공유하지 않는다. */
@Component
class R2ProfileImageStorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: ProfileImageProperties,
) : ProfileImageStoragePort {

    /** presigned PUT URL. contentType·contentLength가 서명에 포함돼 선언과 다르게 올리면 R2가 거부한다. */
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

    override fun findSize(objectKey: String): Long? = try {
        s3Client.headObject(
            HeadObjectRequest.builder().bucket(properties.bucket).key(objectKey).build(),
        ).contentLength()
    } catch (e: NoSuchKeyException) {
        null
    } catch (e: S3Exception) {
        // R2 HeadObject는 부재를 NoSuchKeyException이 아닌 404로 줄 때가 있다. 부재는 예외가 아니라 null이다.
        if (e.statusCode() == 404) null else throw e
    }

    /** 앞부분 [length] 바이트만 Range로 읽는다 — 매직바이트 판정에 12바이트면 충분하다. */
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

    /** 복사 후 원본 삭제(S3에는 이동이 없다). 삭제 실패로 남는 `pending/` 사본은 lifecycle rule이 정리한다. */
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
