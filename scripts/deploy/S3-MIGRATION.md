# S3 account migration

Source account: 080050138880. Destination account: 993371733162. Region: us-east-2.

| Source | Destination | Objects | Bytes |
|---|---|---:|---:|
| expensely-expenses | expensely-expenses-993371733162 | 17 | 33062939 |
| expensely-profiles | expensely-profiles-993371733162 | 5 | 16225168 |

Every copied object's SHA-256, size, content metadata and tags were checked against the source. Object keys are unchanged. Source inventory was stable during the copy. Objects were streamed through memory using authenticated source/destination SDK clients; object bodies were not saved in the workspace. The new account owns the objects. S3 system LastModified times change when copied.

The expensely-storage CloudFormation stack records the destination buckets and the EC2 role's access policy. Both buckets use AES256 encryption, bucket-owner-enforced ownership and versioning. Expenses remain private, and profile image public GET access matches the source behavior. CORS preserves the existing allowed origins and methods. Expense objects start in STANDARD and transition to GLACIER_IR after 30 days. The source buckets and objects are retained for recovery.

EC2 role upload, download, presigned download and delete checks passed for isolated test keys in each destination bucket. Anonymous expense access returns 403, public profile image access returns 200, and browser PUT CORS from https://expensely.store succeeds.

GitHub Actions secrets AWS_BUCKET_NAME and AWS_PROFILE_BUCKET_NAME now contain the destination names. Deployment reads those settings into the app environment. The backend remaps recognized old S3 profile URLs at response time without rewriting database records or changing external OAuth image URLs. The frontend uses the new bucket and normalizes legacy profile URLs.

Any rollback to a release/configuration from before migration must retain the new bucket names and EC2 role policy. Do not delete the original buckets until application verification and the required retention period are complete.
