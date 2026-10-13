package com.devProject.leckeep_backend.service.document;

import com.devProject.leckeep_backend.dto.response.DocumentDownloadResponseDto;
import com.devProject.leckeep_backend.dto.response.DocumentFileResponseDto;
import com.devProject.leckeep_backend.dto.response.DocumentPreviewResponseDto;
import com.devProject.leckeep_backend.dto.response.pagination.DocumentFilePaginateResponseDto;
import com.devProject.leckeep_backend.enums.PreviewMode;
import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.mappers.DocumentFileMapper;
import com.devProject.leckeep_backend.model.DocumentFile;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.DocumentFileRepository;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.folder.FolderPermissionService;
import com.devProject.leckeep_backend.service.document.storage.DownloadedObject;
import com.devProject.leckeep_backend.service.document.storage.FileUploadCommand;
import com.devProject.leckeep_backend.service.document.storage.ObjectStorageService;
import com.devProject.leckeep_backend.service.document.storage.StoredObject;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandler;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandlerResolver;
import com.devProject.leckeep_backend.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {
    private static final int DIGEST_BUFFER_SIZE = 8192;
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentFileRepository documentFileRepository;
    private final NoteFolderRepository noteFolderRepository;
    private final CurrentUserService currentUserService;
    private final FileValidationService fileValidationService;
    private final ObjectStorageService objectStorageService;
    private final FileTypeHandlerResolver fileTypeHandlerResolver;
    private final DocumentFileMapper documentFileMapper;
    private final FolderPermissionService folderPermissionService;

    @Override
    public DocumentFileResponseDto uploadDocument(String folderId, MultipartFile file) {
        FileValidationService.FileValidationResult validationResult = fileValidationService.validate(file);
        String currentUserId = currentUserService.requireCurrentUserId();
        NoteFolder folder = noteFolderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + folderId));
        requireFolderOwner(folder, currentUserId, "You do not have access to upload documents to this folder");

        String objectKey = buildObjectKey(folderId, validationResult.getSafeFilename());
        String checksum = calculateSha256(file);
        StoredObject storedObject = uploadToStorage(file, objectKey, validationResult.getSafeFilename());

        LocalDateTime now = LocalDateTime.now();
        DocumentFile documentFile = DocumentFile.builder()
                .folderId(folderId)
                .ownerId(currentUserId)
                .originalName(validationResult.getSafeFilename())
                .storedKey(storedObject.getObjectKey())
                .contentType(storedObject.getContentType())
                .extension(validationResult.getExtension())
                .sizeBytes(storedObject.getSizeBytes())
                .checksum(checksum)
                .createdAt(now)
                .updatedAt(now)
                .build();

        DocumentFile savedDocument = documentFileRepository.save(documentFile);
        return documentFileMapper.toDocumentFileResponseDto(savedDocument, validationResult);
    }

    @Override
    public DocumentFilePaginateResponseDto getFolderDocuments(String folderId, int page, int size) {
        NoteFolder folder = noteFolderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + folderId));
        folderPermissionService.requireReadAccess(folder);

        Pageable pageable = PaginationUtils.buildPageable(
                page,
                size,
                MAX_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt")
        );
        Page<DocumentFile> documentPage = documentFileRepository.findByFolderId(folderId, pageable);

        return DocumentFilePaginateResponseDto.builder()
                .dataList(documentPage.getContent()
                        .stream()
                        .map(documentFileMapper::toDocumentFileResponseDto)
                        .toList())
                .dataCount(documentPage.getTotalElements())
                .build();
    }

    @Override
    public DocumentDownloadResponseDto downloadDocument(String documentId) {
        DocumentFile documentFile = findDocumentOrThrow(documentId);
        NoteFolder folder = noteFolderRepository.findById(documentFile.getFolderId())
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + documentFile.getFolderId()));
        folderPermissionService.requireReadAccess(folder);

        DownloadedObject downloadedObject = objectStorageService.download(documentFile.getStoredKey());
        return DocumentDownloadResponseDto.builder()
                .documentId(documentFile.getId())
                .originalName(documentFile.getOriginalName())
                .contentType(resolveContentType(downloadedObject.getContentType(), documentFile.getContentType()))
                .sizeBytes(resolveSizeBytes(downloadedObject.getSizeBytes(), documentFile.getSizeBytes()))
                .inputStream(downloadedObject.getInputStream())
                .build();
    }

    @Override
    public DocumentPreviewResponseDto previewDocument(String documentId) {
        DocumentFile documentFile = findDocumentOrThrow(documentId);
        NoteFolder folder = noteFolderRepository.findById(documentFile.getFolderId())
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + documentFile.getFolderId()));
        folderPermissionService.requireReadAccess(folder);

        FileTypeHandler fileTypeHandler = fileTypeHandlerResolver.resolve(
                documentFile.getContentType(),
                documentFile.getExtension()
        );
        PreviewMode previewMode = fileTypeHandler.getPreviewMode(documentFile.getExtension());
        return DocumentPreviewResponseDto.builder()
                .documentId(documentFile.getId())
                .originalName(documentFile.getOriginalName())
                .contentType(documentFile.getContentType())
                .extension(documentFile.getExtension())
                .sizeBytes(documentFile.getSizeBytes())
                .previewMode(previewMode)
                .previewUrl(objectStorageService.generatePreviewUrl(documentFile.getStoredKey()))
                .inlinePreview(isInlinePreview(previewMode))
                .build();
    }

    @Override
    public void deleteDocument(String documentId) {
        DocumentFile documentFile = findDocumentOrThrow(documentId);
        NoteFolder folder = noteFolderRepository.findById(documentFile.getFolderId())
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + documentFile.getFolderId()));
        requireFolderOwner(folder, currentUserService.requireCurrentUserId(), "You do not have access to delete this document");

        objectStorageService.delete(documentFile.getStoredKey());
        documentFileRepository.delete(documentFile);
    }

//  *************Helper Methods*****************

    private String buildObjectKey(String folderId, String safeFilename) {
        return "folders/%s/%s-%s".formatted(folderId, UUID.randomUUID(), safeFilename);
    }

    private DocumentFile findDocumentOrThrow(String documentId) {
        return documentFileRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + documentId));
    }

    private String resolveContentType(String storageContentType, String metadataContentType) {
        if (storageContentType != null && !storageContentType.isBlank()) {
            return storageContentType;
        }
        if (metadataContentType != null && !metadataContentType.isBlank()) {
            return metadataContentType;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private long resolveSizeBytes(long storageSizeBytes, long metadataSizeBytes) {
        return storageSizeBytes > 0 ? storageSizeBytes : metadataSizeBytes;
    }

    private boolean isInlinePreview(PreviewMode previewMode) {
        return previewMode == PreviewMode.INLINE_IMAGE || previewMode == PreviewMode.INLINE_PDF;
    }

    private void requireFolderOwner(NoteFolder folder, String currentUserId, String message) {
        if (!currentUserId.equals(folder.getOwnerId())) {
            throw new SecurityException(message);
        }
    }

    private StoredObject uploadToStorage(MultipartFile file, String objectKey, String safeFilename) {
        try {
            return objectStorageService.upload(FileUploadCommand.builder()
                    .objectKey(objectKey)
                    .originalName(safeFilename)
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .inputStream(file.getInputStream())
                    .build());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read uploaded file", ex);
        }
    }

    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream();
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
                while (digestInputStream.read(buffer) != -1) {
                    // Drain the stream so DigestInputStream can update the digest.
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to calculate uploaded file checksum", ex);
        }
    }

}
