package com.devProject.leckeep_backend.service.document;

import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.enums.PreviewMode;
import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.mappers.DocumentFileMapper;
import com.devProject.leckeep_backend.model.DocumentFile;
import com.devProject.leckeep_backend.model.FolderShare;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.*;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.document.storage.DownloadedObject;
import com.devProject.leckeep_backend.service.document.storage.ObjectStorageService;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandler;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandlerResolver;
import com.devProject.leckeep_backend.service.folder.FolderPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentReadAuthorizationTests {
    private final DocumentFileRepository documents = mock(DocumentFileRepository.class);
    private final NoteFolderRepository folders = mock(NoteFolderRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final FolderShareRepository shares = mock(FolderShareRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final FileTypeHandlerResolver types = mock(FileTypeHandlerResolver.class);
    private final DocumentFileMapper mapper = mock(DocumentFileMapper.class);
    private final FolderPermissionService permissions = new FolderPermissionService(users, members, shares, groups);
    private final DocumentServiceImpl service = new DocumentServiceImpl(documents, folders, users,
            mock(FileValidationService.class), storage, types, mapper, permissions);
    private NoteFolder folder;

    enum ReadOperation { LIST, DOWNLOAD, PREVIEW }

    @BeforeEach
    void setUp() {
        when(users.requireCurrentUserId()).thenReturn("reader");
        folder = NoteFolder.builder().id("folder").ownerId("owner")
                .visibility(FolderVisibility.GROUP).groupId("group").build();
        when(folders.findById("folder")).thenReturn(Optional.of(folder));
        // Document ownership alone must never bypass the parent folder policy.
        when(documents.findById("document")).thenReturn(Optional.of(DocumentFile.builder()
                .id("document").folderId("folder").ownerId("reader")
                .storedKey("key").contentType("application/pdf").extension(".pdf").build()));
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void nonMemberCannotReadDocuments(ReadOperation operation) {
        when(groups.existsById("group")).thenReturn(true);
        assertThrows(SecurityException.class, () -> read(operation));
        verifyNoInteractions(storage, types, mapper);
        verify(documents, never()).findByFolderId(anyString(), any(Pageable.class));
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void memberCanReadDocuments(ReadOperation operation) {
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
        stubRead(operation);
        assertDoesNotThrow(() -> read(operation));
        verify(members).existsByGroupIdAndUserId("group", "reader");
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void missingParentFolderDeniesRead(ReadOperation operation) {
        when(folders.findById("folder")).thenReturn(Optional.empty());
        assertThrows(FolderNotFoundException.class, () -> read(operation));
        verifyNoInteractions(storage, types, mapper);
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void privateFolderOwnerCanReadDocuments(ReadOperation operation) {
        folder.setVisibility(FolderVisibility.PRIVATE);
        folder.setOwnerId("reader");
        stubRead(operation);
        assertDoesNotThrow(() -> read(operation));
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void revokedShareRemovesDocumentReadAccess(ReadOperation operation) {
        folder.setVisibility(FolderVisibility.PRIVATE);
        FolderShare share = FolderShare.builder().folderId("folder").ownerId("owner")
                .targetType(ShareTargetType.USER).targetId("reader").permission(SharePermission.WRITE).build();
        when(shares.findByFolderId("folder")).thenReturn(List.of(share), List.of());
        stubRead(operation);
        assertDoesNotThrow(() -> read(operation));
        clearInvocations(storage, types, mapper, documents);
        assertThrows(SecurityException.class, () -> read(operation));
        verifyNoInteractions(storage, types, mapper);
        verify(documents, never()).findByFolderId(anyString(), any(Pageable.class));
    }

    @Test
    void membershipDoesNotGrantDocumentDeletePermission() {
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
        assertThrows(SecurityException.class, () -> service.deleteDocument("document"));
        verifyNoInteractions(storage);
        verify(documents, never()).delete(any(DocumentFile.class));
    }

    private void read(ReadOperation operation) {
        switch (operation) {
            case LIST -> service.getFolderDocuments("folder", 0, 10);
            case DOWNLOAD -> service.downloadDocument("document");
            case PREVIEW -> service.previewDocument("document");
        }
    }

    private void stubRead(ReadOperation operation) {
        switch (operation) {
            case LIST -> when(documents.findByFolderId(eq("folder"), any(Pageable.class))).thenReturn(Page.empty());
            case DOWNLOAD -> when(storage.download("key")).thenReturn(DownloadedObject.builder()
                    .contentType("application/pdf").sizeBytes(10).build());
            case PREVIEW -> {
                FileTypeHandler handler = mock(FileTypeHandler.class);
                when(types.resolve("application/pdf", ".pdf")).thenReturn(handler);
                when(handler.getPreviewMode(".pdf")).thenReturn(PreviewMode.INLINE_PDF);
            }
        }
    }
}
