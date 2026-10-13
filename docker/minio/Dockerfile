# Official source release: https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z
# Build official source without requiring access to a prebuilt MinIO registry image.
FROM golang:1.24.8-alpine AS build
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOBIN=/out go install github.com/minio/minio@RELEASE.2025-10-15T17-29-55Z

FROM alpine:3.22
COPY --from=build /out/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
