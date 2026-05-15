package com.example.expensely_backend.utils;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
public class S3Service {
	String AWS_REGION = System.getenv("AWS_REGION");
	Region region = Region.of(AWS_REGION);

	String BUCKET_NAME = System.getenv("AWS_BUCKET_NAME");

	public String generatePresignedURL(String key,
	                                   String contentType) {
		try (S3Presigner presigner = S3Presigner.builder().region(region).build()) {
			PutObjectRequest putObjectRequest =
					PutObjectRequest.builder().bucket(BUCKET_NAME).key(key).contentType(contentType).build();
			PutObjectPresignRequest putObjectPresignRequest =
					PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(10)).putObjectRequest(putObjectRequest).build();
			PresignedPutObjectRequest ppor =
					presigner.presignPutObject(putObjectPresignRequest);

			return ppor.url().toString();

		}
	}

	public String generateDownloadUrl(String key) {
		try (S3Presigner presigner = S3Presigner.builder().region(region).build()) {

			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(key)
					.build();

			GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
					.signatureDuration(Duration.ofMinutes(15))
					.getObjectRequest(getObjectRequest)
					.build();

			PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(getObjectPresignRequest);

			return presignedRequest.url().toString();
		}
	}

	public void deleteObject(String key) {
		try (S3Client s3Client = S3Client.builder().region(region).build()) {
			DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(key)
					.build();
			s3Client.deleteObject(deleteObjectRequest);
		}
	}
}
