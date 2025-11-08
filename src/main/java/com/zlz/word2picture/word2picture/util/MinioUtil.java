package com.zlz.word2picture.word2picture.util;


import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

import static com.zlz.word2picture.word2picture.constants.Constant.BUCKET_NAME;
import static com.zlz.word2picture.word2picture.constants.Constant.MINIO_URL;

@Service
@Slf4j
public class MinioUtil {
    @Autowired
    private MinioClient minioClient;

    public static String getFileUrl(String fileName){
        try{
            return MINIO_URL +"/"+ BUCKET_NAME +"/"+ fileName;
        }catch (Exception e){
            log.error("--minioUtil#getFileUrl返回异常",e);
            throw e;
        }
    }
    public String uploadToMinio(byte[] data, String objectName, String contentType) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(objectName)
                        .stream(bais, data.length, -1)
                        .contentType(contentType)
                        .build());
        return objectName;
    }
}
