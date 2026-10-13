# LecKeep

LecKeep is a lecture-note and academic document management application. It provides authenticated workspaces where students can organize subjects, note folders, uploaded documents, and study groups.

The repository contains a Spring Boot backend, a React/Vite frontend, and deployment scaffolding for Docker, Jenkins, Ansible, and Terraform.

## Project Walkthrough

```text
LecKeep/
  leckeep_backend/      Spring Boot API for auth, users, subjects, folders, documents, groups, and sharing
  leckeep_frontend/     React + Vite client application
  Ansible/              EC2 Docker deployment playbook
  Terraform/            AWS security group and EC2 lookup configuration
  docker-compose.yml    Local container orchestration scaffold
  Jenkinsfile           CI/CD pipeline scaffold
  architecture-design.md
  future-plan.md
```

## Current Features

- User registration and login with JWT authentication.
- Password hashing with BCrypt.
- Authenticated APIs protected by Spring Security.
- Subject create, list, update, and delete.
- Folder create, list, update, delete, and scoped listing.
- Document upload, listing, download, preview URL generation, and delete.
- MinIO-backed object storage for uploaded documents.
- Study group create, list, update, delete, add member, and remove member APIs.
- Frontend screens for authentication, dashboard, folders, notes, subjects, groups, uploads, and document previews.
- Swagger/OpenAPI UI enabled for backend API exploration.

Search currently exists as an API route, but the controller returns a placeholder response.

## Tech Stack

### Backend

- Java 17
- Spring Boot 3.3.2
- Spring Web
- Spring Security
- Spring Data MongoDB
- JWT with `jjwt`
- MinIO Java SDK
- Lombok
- Maven
- Springdoc OpenAPI

### Frontend

- React 19
- Vite
- Tailwind CSS
- lucide-react
- react-select

### DevOps

- Docker
- Docker Compose
- Jenkins
- Ansible
- Terraform
- AWS EC2

## Prerequisites

- Java 17
- Maven or the included Maven wrapper
- Node.js 22 or compatible recent Node version
- npm
- MongoDB
- MinIO
- Docker, optional

## Environment Variables

### Backend

The backend reads these values from Spring configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017/noteApp` in dev | MongoDB connection string |
| `MONGODB_DATABASE` | `noteApp` | MongoDB database name |
| `JWT_SECRET` | Dev-only fallback secret | JWT signing secret |
| `MINIO_URL` | `http://localhost:9000` | MinIO endpoint |
| `MINIO_PUBLIC_URL` | Same as `MINIO_URL` | Browser-visible endpoint used to sign preview URLs |
| `MINIO_REGION` | `us-east-1` | Region used by storage and URL signing |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `MINIO_BUCKET_NAME` | `leckeep-documents` | Document bucket |
| `STORAGE_PROVIDER` | `minio` | Storage adapter selector |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` | Allowed browser origins |

For production, set a strong `JWT_SECRET` and avoid relying on the fallback value in `application.yml`.

### Frontend

```env
VITE_API_URL=http://localhost:8081/api/v1
```

If `VITE_API_URL` is not set, the frontend uses `/api/v1`. The Vite development server also proxies `/api` to `http://localhost:8081`.

## Local Development (without Docker)

### 1. Start MongoDB and MinIO

Run MongoDB and MinIO locally using your preferred installation method. With default development settings, the backend expects:

```text
MongoDB: mongodb://localhost:27017/noteApp
MinIO:   http://localhost:9000
Bucket:  leckeep-documents
```

The backend creates the MinIO bucket automatically if it does not exist.

### 2. Run the Backend

```bash
cd leckeep_backend
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd leckeep_backend
.\mvnw.cmd spring-boot:run
```

The backend runs on:

```text
http://localhost:8081
```

Swagger UI is available at:

```text
http://localhost:8081/swagger-ui.html
```

### 3. Run the Frontend

```bash
cd leckeep_frontend
npm install
npm run dev
```

The Vite dev server prints the local frontend URL, usually:

```text
http://localhost:5173
```

## Build Commands

### Backend

```bash
cd leckeep_backend
./mvnw clean package
```

Run backend tests:

```bash
cd leckeep_backend
./mvnw test
```

### Frontend

```bash
cd leckeep_frontend
npm install
npm run build
```

Preview the frontend production build:

```bash
npm run preview
```

## Docker (full local stack)

Docker Desktop (Linux containers) or Docker Engine with Docker Compose v2+ is sufficient. The backend is built from source inside Docker; a host JDK, Maven, MongoDB, and MinIO are not required.

```bash
git clone https://github.com/AmiruHoradagoda/LecKeep.git
cd LecKeep
cp .env.example .env
docker compose up -d
```

On Windows PowerShell, use `Copy-Item .env.example .env` instead of `cp`. The example contains development-only placeholders. `.env` is gitignored; never use these credentials or the example JWT secret in production.

Default local URLs:

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend | http://localhost:5050 |
| Swagger UI | http://localhost:5050/swagger-ui.html |
| MinIO object API / signed previews | http://localhost:19000 |
| MinIO console | http://localhost:19001 |

Console credentials come from `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` in your local `.env`.

The stack contains frontend, backend, MongoDB, and MinIO on its own Compose network. MongoDB has no host port. Published ports bind only to `127.0.0.1`. Defaults avoid the existing Airflow port 8080 and AIStor ports 9000/9001; the stack does not reuse or stop those containers. Override host ports in `.env` if needed.

Spring runs on container port 8080 via `SERVER_PORT=8080`. Its normal non-Docker default remains 8081. The backend connects to `mongodb:27017` and `minio:9000`. `MINIO_PUBLIC_URL` uses the browser-visible hostname and MinIO host port, so signed preview URLs do not expose an unresolvable Docker hostname.

`BROWSER_HOST` defaults to `localhost`. Compose derives the frontend's build-time `VITE_API_URL` and the signed-preview endpoint from that hostname and the chosen host ports. CORS permits the selected frontend origin and its `127.0.0.1` equivalent. An optional `CORS_ALLOWED_ORIGINS` override must list explicit origins. Rebuild after changing browser-facing settings:

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend minio mongodb
```

The backend waits for MongoDB and MinIO healthchecks; the frontend waits for the backend's existing Swagger UI endpoint. No Actuator dependency is added. Failed containers use `restart: unless-stopped`.

MinIO is built from the pinned official source release `RELEASE.2025-10-15T17-29-55Z`, following its [container build guidance](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z). This avoids relying on unavailable community registry images or an AIStor license. First builds download Go/Maven/npm dependencies and can take time; subsequent builds use Docker caches.

MongoDB data and uploaded objects live in the named `mongodb_data` and `minio_data` volumes. Stop and restart without deleting data:

```bash
docker compose stop
docker compose start
```

`docker compose down` also retains the named volumes. **`docker compose down -v` deletes the local database and uploaded files.** Existing AIStor volumes are separate.

This is an HTTP, loopback-only development stack. It does not configure production TLS, external access, AWS, or deployment. Jenkins retains its default backend Docker target, which copies the JAR packaged by CI; Compose selects the separate `from-source` target in the same Dockerfile.

## API Overview

Authentication routes are public. Other routes require:

```http
Authorization: Bearer <token>
```

### Auth

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### Users

```text
GET    /api/v1/users/getAllUsers
GET    /api/v1/users/{id}
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
```

### Subjects

```text
GET    /api/v1/subjects
POST   /api/v1/subjects
PUT    /api/v1/subjects/{id}
DELETE /api/v1/subjects/{id}
```

### Folders

```text
GET    /api/v1/folders?scope=private&page=0&size=10
GET    /api/v1/folders/{id}
POST   /api/v1/folders
PUT    /api/v1/folders/{id}
DELETE /api/v1/folders/{id}
```

Supported folder scopes depend on backend service behavior. The frontend uses private, global, group, and shared-style workspace views.

### Documents

```text
POST   /api/v1/folders/{folderId}/documents
GET    /api/v1/folders/{folderId}/documents?page=0&size=10
GET    /api/v1/documents/{documentId}/download
GET    /api/v1/documents/{documentId}/preview
DELETE /api/v1/documents/{documentId}
```

Document upload uses multipart form data with a `file` field.

### Groups

```text
GET    /api/v1/groups?page=0&size=10
POST   /api/v1/groups
GET    /api/v1/groups/{id}
PUT    /api/v1/groups/{id}
DELETE /api/v1/groups/{id}
POST   /api/v1/groups/{id}/members
DELETE /api/v1/groups/{id}/members/{userId}
```

### Sharing

```text
POST   /api/v1/folders/{folderId}/share
DELETE /api/v1/folders/{folderId}/share
```

### Search

```text
GET /api/v1/search?q=<term>&scope=all
```

This endpoint is currently a placeholder.

## File Upload Rules

The backend validates uploaded files before saving them to object storage.

- Maximum file size: 25 MB.
- Original filenames are sanitized before storage metadata is built.
- Preview URLs are generated through the configured object storage adapter.
- MinIO preview URLs expire after 15 minutes.

Supported document handling is split by file type handlers in:

```text
leckeep_backend/src/main/java/com/devProject/leckeep_backend/service/document/type
```

## Response Shape

Most backend endpoints return:

```json
{
  "code": 200,
  "message": "Folders fetched",
  "data": {}
}
```

Auth endpoints return an `AuthenticationResponse` directly.

## Jenkins CI

The Jenkinsfile runs Checkout, Backend Tests, Backend Build, Frontend Install, Frontend Build, Docker Build, and Docker Push, in that order. It stops after pushing images to Docker Hub; it does not deploy to EC2.

- Configure a Pipeline from SCM or a Multibranch Pipeline for this LecKeep repository, using `Jenkinsfile`. Checkout uses the job's SCM configuration and credentials.
- Provide a Linux agent with both `linux` and `docker` labels, a JDK 17 or newer, a POSIX shell, and access to a working Docker daemon. Maven is supplied by the wrapper; no Jenkins Maven installation is required.
- Install the Pipeline, Git, Credentials Binding, JUnit, and NodeJS plugins. Configure the existing NodeJS tool name `node-22.12.0` with Node.js 22.12.0 or newer compatible version.
- Provide the username/password credential `dockerhub-credentials`. Use the Docker Hub username and an access token with push permission for `amiru1234/leckeep-backend` and `amiru1234/leckeep-frontend`. Change the image namespace in `Jenkinsfile` if needed.
- Set the public `VITE_API_URL` build parameter for the intended environment. It defaults to `/api/v1` and is applied to both the standalone frontend build and Docker build. This value is embedded in browser assets; it must not contain credentials. Production routes `/api` through the Nginx proxy described below.
- Backend tests mock MongoDB and object storage and require no production database, JWT, MinIO, or SSH credentials. Tests run once with `sh ./mvnw -B clean test`; packaging uses `sh ./mvnw -B package -DskipTests` after that stage succeeds. Jenkins publishes the JUnit reports.
- Frontend dependencies come from `npm ci` and `package-lock.json`. Docker builds use the current app directories and publish `amiru1234/leckeep-backend:latest` and `amiru1234/leckeep-frontend:latest`.
- Docker Hub credentials are bound only during push, tracing is disabled for login, and authentication uses password stdin. Logout and removal of the temporary Docker config run even when a push fails. A test, build, or push failure fails the pipeline.

Validate the Jenkinsfile with the target Jenkins controller's Declarative Pipeline linter and run the job once to verify its agent, plugins, SCM access, and registry permissions. Local builds alone cannot verify Docker Hub push access.

## Deployment Notes

### Ansible deployment

Run Ansible from Linux, macOS, or Ubuntu WSL with Python and `ansible-core` 2.19+. Install the collection declared in `Ansible/requirements.yml`:

```bash
ansible-galaxy collection install -r Ansible/requirements.yml
cp Ansible/inventory.example.ini Ansible/inventory.ini
```

Edit the ignored inventory with the current EC2 DNS/IP, SSH user, and local private-key path (or use `ssh-agent`). Verify the server's SSH fingerprint and add it to your known hosts. Host-key checking stays enabled. Never commit an SSH key or real inventory.

The target needs Python 3, SSH access, passwordless sudo (or use `--ask-become-pass`), internet access to Docker's package repository and image registry, and enough disk for images and persistent data. Automatic host preparation installs Docker Engine and its Compose plugin from the [official Docker Ubuntu repository](https://docs.docker.com/engine/install/ubuntu/) on Ubuntu 22.04+ (amd64/arm64), then enables Docker. It does not install Jenkins or remove existing Docker packages. If the host already has a working Docker/Compose installation, use `-e leckeep_prepare_host=false`; Python `requests` is also needed for optional registry login. Compose must be version 2.24.4+ for the production port resets.

`Ansible/vars/main.yml` contains non-secret defaults: `/opt/leckeep`, project name `leckeep`, image references, host ports, database/bucket names, and CORS origins. Override these with `-e @path/to/deployment-vars.yml`. Keep the project name stable across releases so the same named volumes are reused. Image maps must specify all four services; prefer release tags or digests for controlled releases.

Before deployment, publish backend/frontend images using the existing Jenkins pipeline or your existing image build/push process. Jenkins remains CI-only. **Build the frontend with `VITE_API_URL=/api/v1` (the Jenkins default) for production Nginx routing.** Rebuild/publish the frontend image to include the SPA fallback configuration. Changing Ansible variables cannot change an API URL already baked into a frontend image.

MinIO is built from source locally and is not currently published by Jenkins. Publish that image to a registry accessible by EC2, for example (replace `YOUR_NAMESPACE` and the release tag):

```bash
docker build -t YOUR_NAMESPACE/leckeep-minio:source-2025-10-15 docker/minio
docker push YOUR_NAMESPACE/leckeep-minio:source-2025-10-15
export DOCKER_IMAGE_MINIO=YOUR_NAMESPACE/leckeep-minio:source-2025-10-15
```

Supply these secrets through the Ansible controller's environment or an untracked/encrypted extra-vars file using the `leckeep_*` variable names in the playbook:

| Controller environment | Ansible variable | Requirement |
| --- | --- | --- |
| `JWT_SECRET` | `leckeep_jwt_secret` | Base64-encoded key of at least 32 bytes; generate with `openssl rand -base64 48` |
| `MINIO_ACCESS_KEY` | `leckeep_minio_access_key` | Explicit MinIO username, at least 3 characters |
| `MINIO_SECRET_KEY` | `leckeep_minio_secret_key` | Strong MinIO password, at least 8 characters |
| `DOCKER_USERNAME` | `leckeep_registry_username` | Optional registry username |
| `DOCKER_PASSWORD` | `leckeep_registry_password` | Optional registry access token; supply both registry values together |

Development defaults are rejected. Runtime secrets must be single-line and must not contain `${`, which the current Spring configuration interprets as a nested property placeholder. Ordinary dollar signs, quotes, spaces, and backslashes are preserved. Do not pass secret values directly on the command line, print them, or copy the development `.env` to EC2. Runtime `.env` is generated under the deployment directory with permissions `0600`; secret tasks suppress logs and diffs. Registry authentication is stored in the deployment user's Docker configuration (root with default `become: true`). Secrets files named `Ansible/secrets*.yml`/`.yaml` are ignored; protect any local secret file with `chmod 600` or encrypt it with Ansible Vault. Changing MinIO credentials on an existing stack requires keeping server and backend values synchronized; changing the JWT key invalidates existing tokens.

Obtain the TLS certificate as described below, then set `APP_DOMAIN` on the Ansible controller (or supply `leckeep_domain` in extra-vars). Deploy and run the same command again to check repeatability:

```bash
ansible-playbook -i Ansible/inventory.ini Ansible/playbook.yml --syntax-check
ansible-playbook -i Ansible/inventory.ini Ansible/playbook.yml
```

The playbook deploys the base Compose definition plus `docker-compose.prod.yml` and the normal project Nginx template. It validates the domain, matching HTTPS origin, existing certificate files, and root-owned private-key permissions before changing the host. Five services must become healthy. Proxy configuration changes regenerate and syntax-check the Nginx configuration before reloading it. Images/configuration are reconciled without deleting named volumes. No automatic rollback or certificate issuance is performed by Ansible. `leckeep_production=false` retains the original four-service deployment for development/testing.

### Production reverse proxy and HTTPS

Public access is `https://<APP_DOMAIN>/` and `https://<APP_DOMAIN>/api/v1/...`. Nginx is the only service publishing ports: **80 and 443**. It sends frontend requests to `frontend:80`, preserves `/api/v1/...` when forwarding to `backend:8080`, and supplies standard forwarding headers. The frontend's Nginx serves `index.html` for SPA paths, so refreshing a frontend URL works.

Frontend, backend, MongoDB, MinIO API, and MinIO console have no production host ports. The override explicitly resets development port mappings; passing both Compose files is required. Local development still uses `docker compose up -d` and the original localhost ports. The production frontend uses `/api/v1`; CORS permits only the configured HTTPS origin, and Spring honors the proxy headers.

Document previews retain the existing permission check and 15-minute signed URL. `MINIO_PUBLIC_URL` becomes the application HTTPS origin. Nginx permits only signed GET/HEAD requests under the configured bucket path, forwards the original URI/query and Host for signature verification, and denies storage writes through this route. MinIO still validates each signature. This limited read route is required for browser previews; it does not publish a MinIO port or its administration console. Treat signed URLs as temporary bearer credentials. Query strings are omitted from proxy access logs, and preview-route logging is disabled.

Nginx adds `nosniff`, `SAMEORIGIN`, and `no-referrer` headers. It permits 26 MiB request bodies to allow multipart overhead around the application's 25 MiB file limit. Spring multipart limits are 25 MiB per file and 26 MiB per request. HSTS is deliberately absent until the public TLS endpoint and renewal are confirmed.

For a build-on-host deployment instead of Ansible:

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
# Set APP_DOMAIN and private JWT/MinIO credentials in .env.prod.
# Certificates must already exist as described below.
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml build
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --wait
```

Do not use the development credentials. `PUBLIC_ORIGIN` defaults to `https://APP_DOMAIN`; an explicit value must match the domain and optional HTTPS port. `TLS_CERT_DIR` defaults to `/etc/letsencrypt`, mounted read-only including `live/` and `archive/` so certificate symlinks resolve. TLS keys are never copied into images or Git. Nginx configuration is mounted read-only. Keep the same Compose project name across releases to retain `<project>_mongodb_data` and `<project>_minio_data`; never deploy with `down -v`.

### DNS, initial certificate, and renewal

No live certificate has been issued or verified from this workstation. Configure a domain you control; the old application domains in historical configuration are not assumed to be active.

1. Point the domain's DNS A record to the EC2 public/elastic IP. Add an AAAA record only if IPv6 routing works. Allow inbound TCP **80/443** in the EC2 security group and host firewall, restrict SSH to administrators, and remove obsolete public 3000/5050/8080/9000/9001 rules after migration. Review the Terraform network-hardening procedure below before changing AWS rules.
2. Install current Certbot (2.3+ for `reconfigure`) on the Ubuntu server, using the [official Certbot instructions](https://certbot.eff.org/instructions). For a snap installation: `sudo snap install --classic certbot`.
3. With port 80 free on the first deployment, obtain the certificate using [Certbot standalone](https://eff-certbot.readthedocs.io/en/stable/using.html#standalone). Replace the example domain/email:

```bash
sudo /snap/bin/certbot certonly --standalone --cert-name leckeep.example.com -d leckeep.example.com --email admin@example.com --agree-tos --no-eff-email
sudo chmod 600 /etc/letsencrypt/live/leckeep.example.com/privkey.pem
```

4. Deploy with Ansible using that domain, or use the production Compose commands above. HTTP redirects to the configured HTTPS origin, except the ACME challenge path and a non-sensitive healthcheck. Do not expose the application over an unencrypted bootstrap proxy.
5. Once Nginx is running, switch that certificate's renewal to its shared webroot. This verifies the new authenticator before saving renewal settings:

```bash
sudo /snap/bin/certbot reconfigure --cert-name leckeep.example.com --webroot --webroot-path /opt/leckeep/acme
```

6. Install a root-owned executable renewal hook at `/etc/letsencrypt/renewal-hooks/deploy/leckeep-reload` with the following content and mode `0700`:

```sh
#!/bin/sh
set -eu
docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml exec -T nginx nginx -t
docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml exec -T nginx nginx -s reload
```

7. Run `sudo /snap/bin/certbot renew --dry-run` and inspect `systemctl list-timers '*certbot*'` to confirm automatic renewal. Adapt the deployment directory in the webroot command and hook if you changed it. Preserve root-only private-key permissions on renewal. See [Certbot renewal documentation](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates) for hook behavior.

### Verify and inspect production

On an Ansible-managed host (the generated `.env` is automatically loaded):

```bash
sudo docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml config --quiet
sudo docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml ps
sudo docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml exec -T nginx nginx -t
sudo docker compose --project-directory /opt/leckeep -f /opt/leckeep/docker-compose.yml -f /opt/leckeep/docker-compose.prod.yml logs --tail=100 nginx backend
curl -I http://leckeep.example.com/
curl -I https://leckeep.example.com/
```

Expect HTTP 308 to HTTPS and a valid certificate on HTTPS; do not use `curl -k` as proof of production TLS. Verify frontend refresh, registration/login, PDF upload, signed preview, and authenticated download. Check that only Nginx has published ports. For manual Compose installations, include `--env-file .env.prod` in these commands. Public EC2 DNS, firewall rules, trusted certificates, registry access, and renewal still need verification on the actual server. Jenkins CD remains disabled.

### Terraform network hardening

`Terraform/` manages **one security group and its additive attachment to the primary network interface of an existing EC2 instance**. The EC2 instance is a data lookup; this configuration does not create or manage EC2, VPCs, databases, load balancers, or instance lifecycle. Keep the original Terraform state when updating an existing deployment. There is no remote-state backend configured by this repository.

The managed group's ingress is:

| Port | Source | Purpose |
| --- | --- | --- |
| TCP 80 | `0.0.0.0/0` | Nginx HTTP redirect and ACME challenges |
| TCP 443 | `0.0.0.0/0` | Nginx HTTPS application/API access |
| TCP 22 | `admin_ssh_cidr` only | Administrator SSH |

There are no managed ingress rules for frontend 3000, backend 5050/8080, MongoDB 27017, or MinIO 9000/9001. Jenkins is not installed by the application playbook, and this repository does not establish that Jenkins runs on this EC2 host, so no Jenkins ingress rule is retained. If Jenkins is present, keep it private and access it through an SSH tunnel rather than reopening public 8080.

If the existing instance already has IPv6 addresses, the managed group permits `::/0` only for 80/443, and allows outbound IPv6. SSH remains IPv4-CIDR restricted. This does not enable IPv6 addressing, routing, Docker bindings, or Nginx listeners; verify those separately before publishing an AAAA record. Allow-all outbound traffic is retained for DNS, package/image downloads, certificate renewal, and existing external API use.

Use Terraform 1.7+ and the committed provider lockfile. Normal AWS credential resolution is used: an AWS CLI profile (`AWS_PROFILE`), AWS environment credentials/session token, or an instance role. Never put credentials in `.tf`, `.tfvars`, plans, or Git. Initialize and configure local inputs:

```bash
cd Terraform
cp terraform.tfvars.example terraform.tfvars
chmod 600 terraform.tfvars
# Edit the ignored file before planning; example instance/CIDR values are documentation only.
terraform init
terraform fmt -check -recursive
terraform validate
terraform test
```

Required inputs are the actual `ec2_instance_id` and trusted `admin_ssh_cidr`; `aws_region` defaults to `us-east-1`. Prefer the administrator's public IPv4 address as `/32`, or an explicitly trusted organization range. `/0`, malformed CIDRs, and IPv6 SSH CIDRs are rejected. Do not use the example `203.0.113.10/32` as a real administrator address, and do not infer the user's IP from a CI runner or coding agent. `security_group_name` defaults to the existing `note-app-sg` to preserve deployed infrastructure. A fresh deployment without an existing managed group may choose `leckeep-app-sg` explicitly.

The legacy Terraform resource addresses and immutable AWS group name/description are intentionally retained. LecKeep naming is applied using mutable tags. The security group now explicitly uses the existing instance's VPC. `prevent_destroy` on the group and attachment blocks accidental group replacement or detachment, including changes caused by a different instance/VPC/name. Do not remove these guards to get an unexpected plan through; investigate first. The new provider constraint is AWS 6.x; when recovering an older deployment, review its previous lockfile/state and the [AWS provider upgrade guide](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/guides/version-6-upgrade) before planning. Follow the guide's staged upgrade procedure if the existing state uses an older provider major version.

Before applying, inspect **every security group on every network interface** of the instance using the AWS console or read-only CLI calls (replace the placeholders):

```bash
aws ec2 describe-instances --region REGION --instance-ids INSTANCE_ID --query 'Reservations[].Instances[].NetworkInterfaces[].{Interface:NetworkInterfaceId,Groups:Groups[].GroupId}'
aws ec2 describe-security-groups --region REGION --group-ids SG_ID_1 SG_ID_2
aws ec2 describe-network-interfaces --region REGION --filters Name=group-id,Values=MANAGED_SG_ID
```

AWS [aggregates the rules of all attached groups](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html). Hardening this group cannot override a permissive rule in another group. The attachment resource adds only this SG and preserves others; it does not take ownership of the instance's full SG set. Audit and deliberately remediate obsolete public access in the other groups, respecting any other workloads they protect. Also verify whether the managed group is shared with other ENIs: changing its rules affects every attached workload. `attached_security_group_ids` exposes the groups observed by the instance lookup for review; it is a snapshot at read time, not a complete effective-firewall analysis. Other tools must not manage the same ENI group set authoritatively, as described in the [provider attachment documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/network_interface_sg_attachment).

If the existing group/attachment already exists but its Terraform state is unavailable, recover the original state first. If that is impossible, import the verified existing resources into the retained addresses before planning (replace the identifiers; ensure variables and AWS credentials are configured):

```bash
terraform import 'aws_security_group.note-app-sg' SG_ID
terraform import 'aws_network_interface_sg_attachment.sg_attachment' ENI_ID_SG_ID
```

The attachment import ID is the actual ENI ID and SG ID joined with an underscore. Do not create duplicate groups or silently detach existing ones because an empty state suggests a fresh deployment.

Create and inspect a live plan, then apply **manually only after review**:

```bash
terraform plan -out=leckeep.tfplan
terraform show leckeep.tfplan
# Manual operator action only, after reviewing the complete plan:
terraform apply leckeep.tfplan
```

For an existing managed deployment, expect ingress/tag updates to the existing group and no EC2 replacement or destructive detachment. Stop on any `-/+`, destroy action, unexpected VPC/ENI change, or `prevent_destroy` error. Confirm the new SSH CIDR from a second session before closing the original administrator session. After manual apply, verify HTTPS, SSH from the approved source, and lack of direct application/database/MinIO/Jenkins access, including the combined effects of other groups and host/container listeners.

State, local `.tfvars`, saved `.tfplan` files, crash logs, and `.terraform/` are gitignored. Commit `.terraform.lock.hcl`, `.tf` sources, the safe example, and plan-only tests. `terraform test` uses a mocked provider to check ingress, IPv6 handling, VPC/ENI targeting, and invalid SSH ranges without AWS access; it does not prove live state, effective security-group exposure, or a production plan is safe. A live plan still requires current credentials, real inputs, and the original/imported state. No automatic `terraform apply` is performed.
