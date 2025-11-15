package com.example.logreg.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
public class AvatarService {
    @Value("${storage.endpoint}")
    private String endpoint;
    @Value("${storage.accessKey}")
    private String accessKey;
    @Value("${storage.secretKey}")
    private String secretKey;
    @Value("${storage.bucket}")
    private String bucket;
    @Value("${storage.region}")
    private String region;

    private MinioClient client;

    @PostConstruct
    public void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ignore) {}
    }

    public String putAvatar(Long userId, String contentType, InputStream in, long size) throws Exception {
        String objectName = userId + ".jpg";
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .contentType(contentType == null ? "image/jpeg" : contentType)
                .stream(in, size, -1)
                .build());
        String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(24 * 60 * 60)
                .build());
        return url;
    }
}