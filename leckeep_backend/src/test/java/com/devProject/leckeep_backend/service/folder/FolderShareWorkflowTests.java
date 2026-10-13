package com.devProject.leckeep_backend.service.folder;

import com.devProject.leckeep_backend.adviser.AppWideExceptionHandler;
import com.devProject.leckeep_backend.controller.FolderShareController;
import com.devProject.leckeep_backend.controller.GroupController;
import com.devProject.leckeep_backend.dto.requests.FolderShareRequestDto;
import com.devProject.leckeep_backend.dto.requests.NoteFolderRequestDto;
import com.devProject.leckeep_backend.enums.*;
import com.devProject.leckeep_backend.exception.GroupNotFoundException;
import com.devProject.leckeep_backend.mappers.DocumentFileMapper;
import com.devProject.leckeep_backend.mappers.NoteFolderMapper;
import com.devProject.leckeep_backend.mappers.StudyGroupMapper;
import com.devProject.leckeep_backend.mappers.GroupMemberMapper;
import com.devProject.leckeep_backend.model.*;
import com.devProject.leckeep_backend.repository.*;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.document.DocumentServiceImpl;
import com.devProject.leckeep_backend.service.document.FileValidationService;
import com.devProject.leckeep_backend.service.document.storage.DownloadedObject;
import com.devProject.leckeep_backend.service.document.storage.ObjectStorageService;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandler;
import com.devProject.leckeep_backend.service.document.type.FileTypeHandlerResolver;
import com.devProject.leckeep_backend.service.group.GroupAccessService;
import com.devProject.leckeep_backend.service.group.GroupServiceImpl;
import com.devProject.leckeep_backend.service.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FolderShareWorkflowTests {
    private final NoteFolderRepository folders = mock(NoteFolderRepository.class);
    private final FolderShareRepository shares = mock(FolderShareRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final DocumentFileRepository documents = mock(DocumentFileRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final FileTypeHandlerResolver types = mock(FileTypeHandlerResolver.class);
    private final GroupAccessService groupAccess = new GroupAccessService(groups, members, currentUser);
    private final FolderShareService sharing = new FolderShareService(folders, shares, users, currentUser, groupAccess);
    private final FolderPermissionService permissions = new FolderPermissionService(currentUser, members, shares, groups);
    private final FolderServiceImpl folderService = new FolderServiceImpl(folders, new NoteFolderMapper(), currentUser, permissions, groupAccess);
    private final SearchService search = new SearchService(mongo, folders, currentUser, permissions);
    private final DocumentServiceImpl documentService = new DocumentServiceImpl(documents, folders, currentUser,
            mock(FileValidationService.class), storage, types, mock(DocumentFileMapper.class), permissions);
    private final Map<String, FolderShare> persistedShares = new LinkedHashMap<>();
    private String actor = "owner";
    private NoteFolder folder;
    private StudyGroup group;
    private DocumentFile document;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        folder = NoteFolder.builder().id("folder").ownerId("owner").title("Database notes")
                .visibility(FolderVisibility.PRIVATE).build();
        group = StudyGroup.builder().id("group").ownerId("owner").name("Study group").build();
        document = DocumentFile.builder().id("document").folderId("folder").ownerId("owner")
                .originalName("database.pdf").contentType("application/pdf").extension(".pdf").storedKey("internal-key").build();
        when(currentUser.requireCurrentUserId()).thenAnswer(invocation -> actor);
        when(folders.findById("folder")).thenReturn(Optional.of(folder));
        when(users.findById("recipient")).thenReturn(Optional.of(Users.builder().id("recipient").build()));
        when(groups.findById("group")).thenReturn(Optional.of(group));
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "recipient")).thenReturn(true);
        when(shares.findByFolderId(anyString())).thenAnswer(invocation -> persistedShares.values().stream()
                .filter(share -> share.getFolderId().equals(invocation.getArgument(0))).toList());
        when(shares.findByFolderIdAndTargetTypeAndTargetId(anyString(), any(), nullable(String.class)))
                .thenAnswer(invocation -> persistedShares.values().stream().filter(share ->
                        share.getFolderId().equals(invocation.getArgument(0)) && share.getTargetType() == invocation.getArgument(1)
                                && Objects.equals(share.getTargetId(), invocation.getArgument(2))).findFirst());
        when(shares.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(persistedShares.get(invocation.getArgument(0))));
        when(shares.insert(any(FolderShare.class))).thenAnswer(invocation -> {
            FolderShare share = invocation.getArgument(0);
            if (persistedShares.putIfAbsent(share.getId(), share) != null) throw new DuplicateKeyException("Duplicate share");
            return share;
        });
        doAnswer(invocation -> { persistedShares.remove(((FolderShare) invocation.getArgument(0)).getId()); return null; })
                .when(shares).delete(any(FolderShare.class));
        when(documents.findById("document")).thenReturn(Optional.of(document));
        when(documents.findByFolderId(eq("folder"), any(Pageable.class))).thenReturn(Page.empty());
        when(mongo.find(any(Query.class), eq(Subject.class))).thenReturn(List.of());
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        when(mongo.stream(any(Query.class), eq(DocumentFile.class))).thenAnswer(invocation -> Stream.of(document));
        when(storage.download("internal-key")).thenReturn(DownloadedObject.builder().contentType("application/pdf").sizeBytes(1).build());
        FileTypeHandler handler = mock(FileTypeHandler.class);
        when(types.resolve("application/pdf", ".pdf")).thenReturn(handler);
        when(handler.getPreviewMode(".pdf")).thenReturn(PreviewMode.INLINE_PDF);
        when(storage.generatePreviewUrl("internal-key")).thenReturn("https://preview.example/document");
        mvc = MockMvcBuilders.standaloneSetup(new FolderShareController(sharing))
                .setControllerAdvice(new AppWideExceptionHandler()).build();
    }

    @ParameterizedTest
    @EnumSource(SharePermission.class)
    void userShareGrantsReadsAndSearchUntilOwnerRevokes(SharePermission permission) {
        var share = sharing.createShare("folder", request(ShareTargetType.USER, "recipient", permission));
        assertEquals("owner", persistedShares.get(share.getId()).getOwnerId());
        assertNotNull(share.getCreatedAt());
        assertEquals(FolderVisibility.PRIVATE, folder.getVisibility());
        actor = "recipient";
        assertCanReadAndSearch();
        actor = "unrelated";
        assertCannotReadOrSearch();
        actor = "owner";
        sharing.revokeShare("folder", share.getId());
        assertTrue(sharing.getShares("folder").isEmpty());
        actor = "recipient";
        assertCannotReadOrSearch();
    }

    @Test
    void nonOwnerCannotCreateListOrRevokeShares() {
        var share = sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.READ));
        actor = "recipient";
        assertThrows(SecurityException.class, () -> sharing.createShare("folder", request(ShareTargetType.GLOBAL, null, SharePermission.READ)));
        assertThrows(SecurityException.class, () -> sharing.getShares("folder"));
        assertThrows(SecurityException.class, () -> sharing.revokeShare("folder", share.getId()));
        assertEquals(1, persistedShares.size());
    }

    @Test
    void groupShareGrantsOnlyMembersAndRevocationRemovesAccess() {
        var share = sharing.createShare("folder", request(ShareTargetType.GROUP, "group", SharePermission.READ));
        actor = "recipient";
        assertCanReadAndSearch();
        actor = "unrelated";
        assertCannotReadOrSearch();
        actor = "owner";
        sharing.revokeShare("folder", share.getId());
        actor = "recipient";
        assertCannotReadOrSearch();
    }

    @Test
    void removedGroupMemberLosesExplicitShareAccess() {
        sharing.createShare("folder", request(ShareTargetType.GROUP, "group", SharePermission.READ));
        actor = "recipient";
        assertCanReadAndSearch();
        when(members.existsByGroupIdAndUserId("group", "recipient")).thenReturn(false);
        assertCannotReadOrSearch();
    }

    @Test
    void globalShareGrantsAuthenticatedReadAndCanBeRevoked() {
        var share = sharing.createShare("folder", request(ShareTargetType.GLOBAL, null, SharePermission.READ));
        assertNull(share.getTargetId());
        actor = "unrelated";
        assertCanReadAndSearch();
        actor = "owner";
        sharing.revokeShare("folder", share.getId());
        actor = "unrelated";
        assertCannotReadOrSearch();
    }

    @Test
    void nonexistentUserAndGroupAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.USER, "missing", SharePermission.READ)));
        assertThrows(GroupNotFoundException.class, () -> sharing.createShare("folder", request(ShareTargetType.GROUP, "missing", SharePermission.READ)));
        assertTrue(persistedShares.isEmpty());
    }

    @Test
    void groupAdminMayShareButOrdinaryMemberAndOutsiderMayNot() {
        group.setOwnerId("another-owner");
        when(members.findByGroupIdAndUserId("group", "owner"))
                .thenReturn(Optional.of(GroupMember.builder().groupId("group").userId("owner").role(GroupRole.MEMBER).build()));
        assertThrows(SecurityException.class, () -> sharing.createShare("folder", request(ShareTargetType.GROUP, "group", SharePermission.READ)));
        when(members.findByGroupIdAndUserId("group", "owner"))
                .thenReturn(Optional.of(GroupMember.builder().groupId("group").userId("owner").role(GroupRole.ADMIN).build()));
        assertDoesNotThrow(() -> sharing.createShare("folder", request(ShareTargetType.GROUP, "group", SharePermission.READ)));
        when(members.findByGroupIdAndUserId("group", "owner")).thenReturn(Optional.empty());
        assertThrows(SecurityException.class, () -> sharing.createShare("folder", request(ShareTargetType.GROUP, "group", SharePermission.READ)));
    }

    @Test
    void duplicateEquivalentShareIsIdempotentAndPermissionChangeIsRejected() {
        var first = sharing.createShare("folder", request(ShareTargetType.USER, " recipient ", SharePermission.READ));
        var again = sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.READ));
        assertEquals(first.getId(), again.getId());
        assertEquals(1, persistedShares.size());
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.WRITE)));
        verify(shares, times(1)).insert(any(FolderShare.class));
    }

    @Test
    void concurrentDuplicateIsHandledWithoutOverwriting() {
        var request = request(ShareTargetType.USER, "recipient", SharePermission.READ);
        sharing.createShare("folder", request);
        FolderShare existing = persistedShares.values().iterator().next();
        when(shares.findByFolderIdAndTargetTypeAndTargetId("folder", ShareTargetType.USER, "recipient"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        assertEquals(existing.getId(), sharing.createShare("folder", request).getId());
        assertEquals(1, persistedShares.size());
    }

    @Test
    void resolvedIdsAreCanonicalizedBeforeCreatingAndRevokingShares() {
        when(folders.findById("folder-alias")).thenReturn(Optional.of(folder));
        when(users.findById("recipient-alias")).thenReturn(Optional.of(Users.builder().id("recipient").build()));
        var share = sharing.createShare("folder-alias", request(ShareTargetType.USER, "recipient-alias", SharePermission.READ));
        assertEquals("folder", share.getFolderId());
        assertEquals("recipient", share.getTargetId());
        assertEquals(share.getId(), sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.READ)).getId());
        assertEquals(1, sharing.getShares("folder-alias").size());
        sharing.revokeShare("folder-alias", share.getId());
        assertTrue(persistedShares.isEmpty());
    }

    @Test
    void malformedSharesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", null));
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(null, null, SharePermission.READ)));
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.USER, "recipient", null)));
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.USER, " ", SharePermission.READ)));
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.GROUP, null, SharePermission.READ)));
        assertThrows(IllegalArgumentException.class, () -> sharing.createShare("folder", request(ShareTargetType.GLOBAL, "recipient", SharePermission.READ)));
        assertTrue(persistedShares.isEmpty());
    }

    @Test
    void shareCannotBeRevokedThroughAnotherFolder() {
        persistedShares.put("foreign", FolderShare.builder().id("foreign").folderId("another-folder").ownerId("owner").build());
        assertThrows(IllegalArgumentException.class, () -> sharing.revokeShare("folder", "foreign"));
        assertTrue(persistedShares.containsKey("foreign"));
    }

    @Test
    void anotherGrantStillAppliesAfterRevocation() {
        var share = sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.READ));
        folder.setVisibility(FolderVisibility.GLOBAL);
        sharing.revokeShare("folder", share.getId());
        actor = "recipient";
        assertCanReadAndSearch();
    }

    @Test
    void writeShareDoesNotGrantMutationPermissions() {
        sharing.createShare("folder", request(ShareTargetType.USER, "recipient", SharePermission.WRITE));
        actor = "recipient";
        assertThrows(SecurityException.class, () -> folderService.deleteFolder("folder"));
        assertThrows(SecurityException.class, () -> folderService.updateFolder("folder", NoteFolderRequestDto.builder().title("Changed").build()));
        assertThrows(SecurityException.class, () -> documentService.deleteDocument("document"));
        assertThrows(SecurityException.class, () -> documentService.uploadDocument("folder", null));
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void groupAssignmentRequiresExistingGroupAndOwnerOrAdmin() {
        NoteFolderRequestDto request = NoteFolderRequestDto.builder().title("Database")
                .visibility(FolderVisibility.GROUP).groupId("missing").build();
        assertThrows(GroupNotFoundException.class, () -> folderService.createFolder(request));
        assertThrows(GroupNotFoundException.class, () -> folderService.updateFolder("folder", request));
        request.setGroupId("group");
        group.setOwnerId("other");
        assertThrows(SecurityException.class, () -> folderService.createFolder(request));
        assertThrows(SecurityException.class, () -> folderService.updateFolder("folder", request));
        group.setOwnerId("owner");
        when(folders.save(any(NoteFolder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(FolderVisibility.GROUP, folderService.createFolder(request).getVisibility());
        assertEquals("group", folderService.updateFolder("folder", request).getGroupId());
        actor = "recipient";
        assertTrue(permissions.canRead(folder));
        actor = "unrelated";
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void shareableGroupPickerIncludesAdminMembershipsAndIgnoresClientUserId() throws Exception {
        GroupServiceImpl service = new GroupServiceImpl(groups, members, users,
                new StudyGroupMapper(), new GroupMemberMapper(), currentUser);
        when(members.findByUserId("owner")).thenReturn(List.of(
                GroupMember.builder().groupId("admin-group").userId("owner").role(GroupRole.ADMIN).build(),
                GroupMember.builder().groupId("member-group").userId("owner").role(GroupRole.MEMBER).build()));
        when(groups.findByOwnerIdOrIdIn(eq("owner"), eq(List.of("admin-group")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(group, StudyGroup.builder().id("admin-group").ownerId("another-owner").name("Admin group").build())));
        MockMvc groupMvc = MockMvcBuilders.standaloneSetup(new GroupController(service))
                .setControllerAdvice(new AppWideExceptionHandler()).build();
        groupMvc.perform(get("/api/v1/groups").param("scope", "shareable").param("userId", "recipient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dataCount").value(2))
                .andExpect(jsonPath("$.data.dataList[1].id").value("admin-group"));
        verify(groups).findByOwnerIdOrIdIn(eq("owner"), eq(List.of("admin-group")), any(Pageable.class));
        verify(members, never()).findByUserId("recipient");
        groupMvc.perform(get("/api/v1/groups").param("scope", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void groupAdminMayAssignFolderAndMissingGroupIdIsRejected() {
        NoteFolderRequestDto request = NoteFolderRequestDto.builder().title("Database")
                .visibility(FolderVisibility.GROUP).build();
        assertThrows(IllegalArgumentException.class, () -> folderService.createFolder(request));
        request.setGroupId(" ");
        assertThrows(IllegalArgumentException.class, () -> folderService.updateFolder("folder", request));
        request.setGroupId("group");
        group.setOwnerId("another-owner");
        when(members.findByGroupIdAndUserId("group", "owner"))
                .thenReturn(Optional.of(GroupMember.builder().groupId("group").userId("owner").role(GroupRole.ADMIN).build()));
        when(folders.save(any(NoteFolder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("group", folderService.createFolder(request).getGroupId());
        assertEquals("group", folderService.updateFolder("folder", request).getGroupId());
    }

    @Test
    void controllerUsesActualIdentityAndRejectsInvalidEnums() throws Exception {
        actor = "unrelated";
        mvc.perform(post("/api/v1/folders/folder/share").contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"recipient\",\"permission\":\"READ\",\"ownerId\":\"owner\"}"))
                .andExpect(status().isForbidden());
        actor = "owner";
        mvc.perform(post("/api/v1/folders/folder/share").contentType("application/json")
                        .content("{\"targetType\":\"INVALID\",\"permission\":\"READ\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/folders/folder/share").contentType("application/json")
                        .content("{\"targetType\":\"GLOBAL\",\"permission\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/folders/folder/share").contentType("application/json")
                        .content("{\"targetType\":\"USER\",\"targetId\":\"recipient\",\"permission\":\"READ\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetId").value("recipient"));
        String shareId = persistedShares.keySet().iterator().next();
        mvc.perform(get("/api/v1/folders/folder/share")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        actor = "recipient";
        mvc.perform(delete("/api/v1/folders/folder/share").param("shareId", shareId)).andExpect(status().isForbidden());
        actor = "owner";
        mvc.perform(delete("/api/v1/folders/folder/share").param("shareId", shareId)).andExpect(status().isOk());
        assertTrue(persistedShares.isEmpty());
    }

    private FolderShareRequestDto request(ShareTargetType type, String target, SharePermission permission) {
        return new FolderShareRequestDto(type, target, permission);
    }

    private void assertCanReadAndSearch() {
        assertDoesNotThrow(() -> folderService.getFolderById("folder"));
        assertDoesNotThrow(() -> documentService.getFolderDocuments("folder", 0, 10));
        assertDoesNotThrow(() -> documentService.previewDocument("document"));
        assertDoesNotThrow(() -> documentService.downloadDocument("document"));
        assertEquals(2, search.search("database", "all").size());
    }

    private void assertCannotReadOrSearch() {
        assertThrows(SecurityException.class, () -> folderService.getFolderById("folder"));
        clearInvocations(storage);
        assertThrows(SecurityException.class, () -> documentService.getFolderDocuments("folder", 0, 10));
        assertThrows(SecurityException.class, () -> documentService.previewDocument("document"));
        assertThrows(SecurityException.class, () -> documentService.downloadDocument("document"));
        verifyNoInteractions(storage);
        assertTrue(search.search("database", "all").isEmpty());
    }
}
