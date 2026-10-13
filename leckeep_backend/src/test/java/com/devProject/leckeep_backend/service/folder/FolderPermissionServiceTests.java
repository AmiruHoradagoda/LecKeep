package com.devProject.leckeep_backend.service.folder;

import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import com.devProject.leckeep_backend.model.FolderShare;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.FolderShareRepository;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FolderPermissionServiceTests {
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final FolderShareRepository shares = mock(FolderShareRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final FolderPermissionService permissions = new FolderPermissionService(users, members, shares, groups);
    private NoteFolder folder;

    @BeforeEach
    void setUp() {
        when(users.requireCurrentUserId()).thenReturn("reader");
        folder = NoteFolder.builder().id("folder").ownerId("owner").visibility(FolderVisibility.PRIVATE).build();
    }

    @Test
    void ownerCanReadPrivateFolder() {
        folder.setOwnerId("reader");
        assertDoesNotThrow(() -> permissions.requireReadAccess(folder));
    }

    @Test
    void unrelatedUserCannotReadPrivateFolder() {
        assertThrows(SecurityException.class, () -> permissions.requireReadAccess(folder));
    }

    @Test
    void authenticatedUserCanReadGlobalFolder() {
        folder.setVisibility(FolderVisibility.GLOBAL);
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void groupMemberCanReadGroupFolder() {
        folder.setVisibility(FolderVisibility.GROUP);
        folder.setGroupId("group");
        allowMembership();
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void nonMemberCannotReadGroupFolder() {
        folder.setVisibility(FolderVisibility.GROUP);
        folder.setGroupId("group");
        when(groups.existsById("group")).thenReturn(true);
        assertFalse(permissions.canRead(folder));
    }

    @ParameterizedTest
    @EnumSource(SharePermission.class)
    void userRecipientCanReadWithReadOrWriteShare(SharePermission permission) {
        share(ShareTargetType.USER, "reader", permission);
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void unrelatedUserCannotUseAnotherUsersShare() {
        share(ShareTargetType.USER, "someone-else", SharePermission.READ);
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void groupShareMemberCanRead() {
        share(ShareTargetType.GROUP, "group", SharePermission.READ);
        allowMembership();
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void groupShareNonMemberCannotRead() {
        share(ShareTargetType.GROUP, "group", SharePermission.READ);
        when(groups.existsById("group")).thenReturn(true);
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void globalSharePermitsAuthenticatedRead() {
        share(ShareTargetType.GLOBAL, null, SharePermission.READ);
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void deletedShareImmediatelyRemovesAccess() {
        FolderShare share = validShare(ShareTargetType.USER, "reader", SharePermission.READ);
        when(shares.findByFolderId("folder")).thenReturn(List.of(share), List.of());
        assertTrue(permissions.canRead(folder));
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void nullVisibilityDoesNotGrantAccess() {
        folder.setVisibility(null);
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void independentOwnerOrShareGrantStillWorksWithNullVisibility() {
        folder.setVisibility(null);
        share(ShareTargetType.USER, "reader", SharePermission.READ);
        assertTrue(permissions.canRead(folder));
        folder.setOwnerId("reader");
        assertTrue(permissions.canRead(folder));
    }

    @Test
    void unknownVisibilityCannotBeRepresentedByEnum() {
        assertThrows(IllegalArgumentException.class, () -> FolderVisibility.valueOf("UNKNOWN"));
    }

    @Test
    void missingOrBlankGroupIdDoesNotGrantAccess() {
        folder.setVisibility(FolderVisibility.GROUP);
        assertFalse(permissions.canRead(folder));
        folder.setGroupId(" ");
        assertFalse(permissions.canRead(folder));
        verifyNoInteractions(members, groups);
    }

    @Test
    void deletedGroupCannotGrantAccessThroughStaleMembership() {
        folder.setVisibility(FolderVisibility.GROUP);
        folder.setGroupId("group");
        assertFalse(permissions.canRead(folder));
        share(ShareTargetType.GROUP, "group", SharePermission.READ);
        assertFalse(permissions.canRead(folder));
        verifyNoInteractions(members);
    }

    @Test
    void shareMustMatchActualOwnerAndFolder() {
        FolderShare share = validShare(ShareTargetType.USER, "reader", SharePermission.READ);
        when(shares.findByFolderId("folder")).thenReturn(List.of(share));
        share.setOwnerId("forged-owner");
        assertFalse(permissions.canRead(folder));
        share.setOwnerId("owner");
        share.setFolderId("another-folder");
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void malformedShareDoesNotGrantAccess() {
        FolderShare share = validShare(ShareTargetType.USER, "reader", null);
        when(shares.findByFolderId("folder")).thenReturn(List.of(share));
        assertFalse(permissions.canRead(folder));
        share.setPermission(SharePermission.READ);
        share.setTargetType(null);
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void missingFolderDoesNotGrantAccess() {
        assertFalse(permissions.canRead(null));
        folder.setId(null);
        assertFalse(permissions.canRead(folder));
    }

    @Test
    void globalAccessStillRequiresAuthenticatedIdentity() {
        folder.setVisibility(FolderVisibility.GLOBAL);
        when(users.requireCurrentUserId()).thenThrow(new IllegalStateException("Authenticated user is required"));
        assertThrows(IllegalStateException.class, () -> permissions.canRead(folder));
    }

    private void allowMembership() {
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
    }

    private void share(ShareTargetType type, String target, SharePermission permission) {
        when(shares.findByFolderId("folder")).thenReturn(List.of(validShare(type, target, permission)));
    }

    private FolderShare validShare(ShareTargetType type, String target, SharePermission permission) {
        return FolderShare.builder().folderId("folder").ownerId("owner")
                .targetType(type).targetId(target).permission(permission).build();
    }
}
