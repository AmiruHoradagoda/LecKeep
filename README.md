# LecKeep

LecKeep is a lecture-note and academic document management application. It provides authenticated workspaces where students can organize subjects, note folders, uploaded documents, and study groups.

The repository contains a Spring Boot backend, a React/Vite frontend, and deployment scaffolding for Docker, Jenkins, Ansible, and Terraform.

## Project Walkthrough

```text
LecKeep/
  leckeep_backend/      Spring Boot API for auth, users, subjects, folders, documents, groups, and sharing
  leckeep_frontend/     React + Vite client application
  Ansible/              EC2 Docker deployment playbook
  terraform/            AWS security group and EC2 lookup configuration
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
- Set the public `VITE_API_URL` build parameter for the intended environment. It defaults to `/api/v1` and is applied to both the standalone frontend build and Docker build. This value is embedded in browser assets; it must not contain credentials. A relative URL requires routing `/api` to the backend when deployment is configured later.
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

The target needs Python 3, SSH access, passwordless sudo (or use `--ask-become-pass`), internet access to Docker's package repository and image registry, and enough disk for images and persistent data. Automatic host preparation installs Docker Engine and its Compose plugin from the [official Docker Ubuntu repository](https://docs.docker.com/engine/install/ubuntu/) on Ubuntu 22.04+ (amd64/arm64), then enables Docker. It does not install Jenkins or remove existing Docker packages. If the host already has a working Docker/Compose installation, use `-e leckeep_prepare_host=false`; Python `requests` is also needed for optional registry login. Compose must be version 2.18+.

`Ansible/vars/main.yml` contains non-secret defaults: `/opt/leckeep`, project name `leckeep`, image references, host ports, database/bucket names, and CORS origins. Override these with `-e @path/to/deployment-vars.yml`. Keep the project name stable across releases so the same named volumes are reused. Image maps must specify all four services; prefer release tags or digests for controlled releases.

Before deployment, publish backend/frontend images using the existing Jenkins pipeline or your existing image build/push process. Jenkins remains CI-only. **Build the frontend with `VITE_API_URL=http://localhost:5050/api/v1` for the default SSH-tunnel setup below.** The Jenkins default `/api/v1` needs a reverse proxy, which this step does not provide. Changing Ansible variables cannot change an API URL already baked into a frontend image.

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

Deploy, then run the same command again to check repeatability:

```bash
ansible-playbook -i Ansible/inventory.ini Ansible/playbook.yml --syntax-check
ansible-playbook -i Ansible/inventory.ini Ansible/playbook.yml
```

The playbook renders `/opt/leckeep/docker-compose.yml` from the repository's Compose definition, substituting registry images and removing local build instructions. Ports, dependencies, healthchecks, environment wiring, and persistent volumes remain defined by Compose. It validates the configuration without printing secrets, pulls images, runs Compose with automatic recreation and health waiting, removes only orphans within the selected project, and fails if any of the four required services is missing or unhealthy. It never stops/removes individual application containers first and never deletes volumes. Same-image redeployments retain running containers; updated images/configuration can recreate affected containers. No automatic rollback is provided.

On the server, inspect status and logs:

```bash
sudo docker compose --project-directory /opt/leckeep ps
sudo docker compose --project-directory /opt/leckeep logs --tail=100 frontend backend mongodb minio
```

The stack retains loopback-only ports. MongoDB has no published port, and MinIO API/console are not publicly exposed. From your workstation, forward the application and preview ports (replace the SSH destination; adjust both local/server ports if you changed defaults):

```bash
ssh -N -L 3000:127.0.0.1:3000 -L 5050:127.0.0.1:5050 -L 19000:127.0.0.1:19000 ubuntu@YOUR_EC2_DNS_OR_IP
```

With the tunnel open, use http://localhost:3000 and http://localhost:5050/swagger-ui.html. Verify login, PDF upload, preview, and download. MinIO signed previews use http://localhost:19000 through the tunnel. For temporary administration only, add `-L 19001:127.0.0.1:19001` and open http://localhost:19001; restrict this to trusted administrators. Local ports must be free (stop your local stack or select matching alternative ports and rebuild the frontend).

MongoDB and MinIO data remain in `<project>_mongodb_data` and `<project>_minio_data`. Do not run `docker compose down -v` during deployment. This step adds no public reverse proxy or HTTPS and does not change Terraform or enable Jenkins CD. Public browser access remains a later deployment step.
