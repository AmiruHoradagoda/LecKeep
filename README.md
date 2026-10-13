# LecKeep

LecKeep is a lecture-note and academic document management application. It provides authenticated workspaces where students can organize subjects, note folders, uploaded documents, and study groups.

The repository contains a Spring Boot backend, a React/Vite frontend, and deployment scaffolding for Docker, Jenkins, Ansible, and Terraform.

## Project Walkthrough

```text
LecKeep/
  leckeep_backend/      Spring Boot API for auth, users, subjects, folders, documents, groups, and sharing
  leckeep_frontend/     React + Vite client application
  ansible/              EC2 Docker deployment playbook
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

## Local Development

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

## Docker

The repository includes Dockerfiles for both apps and a root `docker-compose.yml` scaffold.

Build images with the tags expected by `docker-compose.yml`:

```bash
cd leckeep_backend
./mvnw clean package
docker build -t note-backend-image:v1 .

cd ../leckeep_frontend
npm install
npm run build
docker build -t note-frontend-image:v1 .
```

Then run from the repository root:

```bash
docker compose up
```

Check the backend Dockerfile and compose port/profile settings before using this for production. The Spring app currently uses port `8081` in `application.yml`, while the Dockerfile and compose file are configured around container port `8080`. The compose file also does not currently define MongoDB or MinIO services.

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

## Deployment Notes

- `Jenkinsfile` builds backend and frontend images, pushes them to Docker Hub, and deploys with Ansible.
- `ansible/playbook.yml` pulls and restarts Docker containers on an EC2 host.
- `terraform/main.tf` attaches a security group to an existing EC2 instance.
- `leckeep_frontend/.env.production` points the production frontend to `https://app.amiru-web.xyz/api/v1`.

Before production deployment, review secrets, exposed ports, Docker image names, backend port configuration, and the MongoDB/MinIO runtime configuration.
