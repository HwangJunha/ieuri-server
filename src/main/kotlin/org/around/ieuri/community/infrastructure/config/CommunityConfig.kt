package org.around.ieuri.community.infrastructure.config

import org.around.ieuri.community.application.policy.CommunityPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * community 도메인 인프라 빈 구성. @ConfigurationPropertiesScan이 없으므로 여기서 명시적으로
 * @EnableConfigurationProperties 한다(프로젝트 관례).
 */
@Configuration
@EnableConfigurationProperties(R2Properties::class, CommunityProperties::class)
class CommunityConfig {

    @Bean
    fun communityPolicy(
        r2: R2Properties,
        community: CommunityProperties,
    ): CommunityPolicy = CommunityPolicy(
        maxPageSize = community.maxPageSize,
        commentPreviewSize = community.commentPreviewSize,
        maxRadiusMeters = community.maxRadiusMeters,
        uploadUrlTtl = r2.uploadUrlTtl,
        maxUploadBytes = r2.maxUploadBytes,
        allowedContentTypes = r2.allowedContentTypes.map { it.trim().lowercase() }.toSet(),
    )

    /**
     * R2는 리전 개념이 없어 `auto`를 쓴다. SigV4 서명에는 리전 문자열이 필요하므로 값 자체는
     * 있어야 하며, R2가 이 값을 요구하는 규약이다.
     *
     * path-style을 켜는 이유는 virtual-host 방식이 버킷명을 하위 도메인으로 붙이는데
     * R2 엔드포인트가 그 형태를 쓰지 않기 때문이다.
     */
    @Bean
    fun r2S3Client(properties: R2Properties): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(properties.s3Endpoint))
            .region(Region.of("auto"))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

    @Bean
    fun r2S3Presigner(properties: R2Properties): S3Presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(properties.s3Endpoint))
            .region(Region.of("auto"))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()

    private fun credentials(properties: R2Properties) = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretAccessKey),
    )
}
