package com.devProject.leckeep_backend.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MinioConfig {
    @Value("${minio.url}") private String url;
    @Value("${minio.access-key}") private String accessKey;
    @Value("${minio.secret-key}") private String secretKey;
    @Value("${minio.bucket-name}") private String bucket;
    @Value("${minio.public-url}") private String publicUrl;
    @Value("${minio.region}") private String region;

    @Bean
    @Primary
    public MinioClient minioClient() throws Exception {
        MinioClient client = MinioClient.builder().endpoint(url).region(region).credentials(accessKey, secretKey).build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return client;
    }

    @Bean("minioPreviewClient")
    public MinioClient minioPreviewClient() {
        // A known region lets presigning work without connecting to the browser-only hostname.
        return MinioClient.builder().endpoint(publicUrl).region(region).credentials(accessKey, secretKey).build();
    }
}

