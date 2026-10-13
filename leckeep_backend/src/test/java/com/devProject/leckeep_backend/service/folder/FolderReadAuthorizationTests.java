package com.devProject.leckeep_backend.service.folder;

import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.mappers.NoteFolderMapper;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.*;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.group.GroupAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FolderReadAuthorizationTests {
    private final NoteFolderRepository folders = mock(NoteFolderRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final FolderPermissionService permissions = new FolderPermissionService(users, members,
            mock(FolderShareRepository.class), groups);
    private final FolderServiceImpl service = new FolderServiceImpl(folders, new NoteFolderMapper(), users, permissions,
            new GroupAccessService(groups, members, users));
    private NoteFolder denied;
    private NoteFolder allowed;

    @BeforeEach
    void setUp() {
        when(users.requireCurrentUserId()).thenReturn("reader");
        denied = NoteFolder.builder().id("denied").ownerId("other")
                .visibility(FolderVisibility.GROUP).groupId("group").build();
        allowed = NoteFolder.builder().id("allowed").ownerId("reader")
                .visibility(FolderVisibility.GROUP).groupId("group").build();
    }

    @Test
    void individualFolderReadUsesPolicy() {
        when(folders.findById("denied")).thenReturn(Optional.of(denied));
        assertThrows(SecurityException.class, () -> service.getFolderById("denied"));
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
        assertEquals("denied", service.getFolderById("denied").getId());
    }

    @Test
    void groupListAuthorizesBeforePaginationAndCountsOnlyReadableFolders() {
        when(folders.findByVisibility(eq(FolderVisibility.GROUP), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(denied, allowed)));
        var firstPage = service.getFolders("group", 0, 1);
        assertEquals(1, firstPage.getDataCount());
        assertEquals(List.of("allowed"), firstPage.getDataList().stream().map(item -> item.getId()).toList());
        assertTrue(service.getFolders("group", 1, 1).getDataList().isEmpty());
    }

    @Test
    void missingFolderDeniesRead() {
        assertThrows(FolderNotFoundException.class, () -> service.getFolderById("missing"));
    }

    @Test
    void membershipDoesNotGrantFolderDeletePermission() {
        when(folders.findById("denied")).thenReturn(Optional.of(denied));
        assertThrows(SecurityException.class, () -> service.deleteFolder("denied"));
        verify(folders, never()).delete(any(NoteFolder.class));
    }
}
