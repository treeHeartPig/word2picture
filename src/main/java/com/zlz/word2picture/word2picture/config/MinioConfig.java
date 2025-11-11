package com.zlz.word2picture.word2picture.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Configuration
@Data
public class MinioConfig {
    @Value("${minio.url}")
    private String minioUrl;
    @Value("${minio.bucket}")
    private String bucket;
    @Value("${minio.access}")
    private String access;
    @Value("${minio.secret}")
    private String secret;
    @Lazy
    @Bean(name="minioClient")
    public MinioClient minioClient() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        log.info("minioUrl:{},bucket:{},access:{},secret:{}",minioUrl,bucket,access,secret);
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(access, secret)
                .build();
        boolean exist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exist) {
            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
        }else {
            log.info("bucket:{}已存在",bucket);
        }
        return minioClient;
    }
}
