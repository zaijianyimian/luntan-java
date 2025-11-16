package com.example.activity.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityPhotoService {
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
        try {
            if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) return;
            client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ignore) {}
    }

    private synchronized void ensureClient() throws Exception {
        if (client != null) return;
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("storage credentials missing");
        }
        client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public String putPhoto(Integer activityId, int seq, String contentType, InputStream in, long size) throws Exception {
        ensureClient();
        String ext = extFromContentType(contentType);
        String objectName = "activity-" + activityId + "-" + seq + "." + ext;
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .contentType(contentType == null ? "image/jpeg" : contentType)
                .stream(in, size, -1)
                .build());
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(24 * 60 * 60)
                .build());
    }

    public String presignedUrl(String objectName) throws Exception {
        ensureClient();
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(24 * 60 * 60)
                .build());
    }

    public List<String> listObjectNames(Integer activityId) throws Exception {
        ensureClient();
        String prefix = "activity-" + activityId + "-";
        List<String> names = new ArrayList<>();
        var results = client.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build());
        results.forEach(r -> {
            try { var i = r.get(); names.add(i.objectName()); } catch (Exception ignore) {}
        });
        return names;
    }

    public void deleteByActivityId(Integer activityId) throws Exception {
        ensureClient();
        for (String name : listObjectNames(activityId)) {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(name).build());
        }
    }

    public void deleteObject(String objectName) throws Exception {
        ensureClient();
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    private String extFromContentType(String contentType) {
        if (contentType == null) return "jpg";
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return "png";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("jpeg")) return "jpg";
        if (ct.contains("jpg")) return "jpg";
        return "jpg";
    }
}
