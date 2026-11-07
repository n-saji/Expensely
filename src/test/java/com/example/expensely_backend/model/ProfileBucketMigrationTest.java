package com.example.expensely_backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileBucketMigrationTest {
    @Test
    void legacyS3UrlsUseTheConfiguredBucketAndPreserveKeys() {
        String bucket = System.getenv().getOrDefault("AWS_PROFILE_BUCKET_NAME", "expensely-profiles-993371733162");
        String region = System.getenv("AWS_REGION");
        String target = "https://" + bucket + ".s3" + (region == null || region.isBlank() ? "" : "." + region) + ".amazonaws.com/user/photo%20one.jpg";
        for (String origin : new String[]{"https://expensely-profiles.s3.amazonaws.com/", "https://expensely-profiles.s3.us-east-2.amazonaws.com/"}) {
            User user = new User();
            user.setProfilePicFilePath(origin + "user/photo%20one.jpg");
            assertEquals(target, user.getProfilePictureUrl());
        }
    }

    @Test
    void externalOAuthImagesAndUnrelatedHostsStayUnchanged() {
        for (String url : new String[]{"https://lh3.googleusercontent.com/photo", "https://expensely-profiles.s3.amazonaws.com.evil.example/photo"}) {
            User user = new User();
            user.setProfilePicFilePath(url);
            assertEquals(url, user.getProfilePictureUrl());
        }
    }
}
