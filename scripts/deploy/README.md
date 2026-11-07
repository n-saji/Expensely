# Production deployment

The `expensely-deployment` CloudFormation stack manages a deployment IAM user and a restricted Systems Manager command for EC2 `i-0cf453c0bd115e443` in account `993371733162`, us-east-2. GitHub OIDC is blocked by the project's AWS managed service control policy. The deployment user's key is stored only in repository Actions secrets. It can push to ECR `expensely`, invoke the custom deployment document on this instance, and read command results.

The instance role has SSM managed-node and ECR pull access. `/usr/local/sbin/expensely-deploy` is installed from the versioned script here. Host Nginx retains its TLS certificate, rate limits, Cloudflare settings and WebSocket headers; only its backend target uses the `expensely_backend` named upstream. The original site configuration is backed up under `/etc/expensely`.

On main pushes or manual deploys, Actions builds a Linux amd64 image, pushes a commit tag and deploys its immutable digest. Existing app secrets are encrypted on the runner to the EC2-specific certificate before entering SSM command history. The corresponding private key stays on EC2. App configuration is stored root-only in `/etc/expensely/app.env`. Changes to the instance require replacing the public certificate variable.

The candidate runs on loopback port 8081 or 8082 with memory limits. Direct `/ping` and local HTTPS `/ping` must pass before accepting the release. The previous container is stopped after success to conserve RAM; its image is retained. Select **Run workflow → action: rollback** to restart and validate the previous release before switching traffic. A first-ever deployment has no previous release to roll back to.

Database migrations are disabled during deployment, and Hibernate validates the existing schema. Plan and apply schema migrations separately with backups and backwards compatibility before releasing code that requires them. Application rollback does not roll back database data or schema. The public domain may return a Cloudflare challenge to automated clients; the deployment verifies the EC2 origin locally without changing Cloudflare's configuration.

SIT deployment is disabled. The previous Docker Hub and SSH-based production workflow is replaced by ECR and SSM. This stack does not upgrade the AWS account plan or change the instance type.

## Verification

Run `python3 scripts/deploy/test-deployment.py` for isolated release-state tests. These test first and second releases, preservation of the active upstream when candidate health fails, rollback selection, and rejection of an image outside the permitted registry/digest format. They do not substitute for a live app health check.

First app deployment passed: https://github.com/n-saji/Expensely/actions/runs/42338884796. After deploying the same immutable app image to the second slot, the GitHub rollback action passed: https://github.com/n-saji/Expensely/actions/runs/42339304556. This verifies image/environment retention, healthy startup, Nginx traffic switching and rollback mechanics with the same application version; it does not verify database rollback or compatibility between different versions. Initial runs were blocked by GitHub billing, which was subsequently resolved.

The shared ECR-only key is configured as AWS_ECR_ACCESS_KEY_ID and AWS_ECR_SECRET_ACCESS_KEY in the five requested repositories. It is separate from the backend deployment key. New repositories need these secrets added explicitly; personal GitHub accounts have no account-wide Actions secrets.

Install script updates with sudo bash scripts/deploy/install, then update EXPENSELY_DEPLOY_CERT only if the EC2 certificate was newly created. Installation replaces the deployment executable atomically. The broader S3 and Lambda migration is separate from this EC2 deployment setup; existing app configuration is reused.
