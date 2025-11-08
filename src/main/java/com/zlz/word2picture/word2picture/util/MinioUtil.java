package com.zlz.word2picture.word2picture.util;


import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@Slf4j
public class MinioUtil {
    @Autowired
    private MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;
    @Value("${minio.bucket}")
    private String bucket;
    @Value("${minio.access}")
    private String access;
    @Value("${minio.secret}")
    private String secret;

    public String getFileUrl(String fileName){
        try{
            return minioUrl +"/"+ bucket +"/"+ fileName;
        }catch (Exception e){
            log.error("--minioUtil#getFileUrl返回异常",e);
            throw e;
        }
    }
    public String uploadToMinio(byte[] data, String objectName, String contentType) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(bais, data.length, -1)
                        .contentType(contentType)
                        .build());
        return objectName;
    }
}
