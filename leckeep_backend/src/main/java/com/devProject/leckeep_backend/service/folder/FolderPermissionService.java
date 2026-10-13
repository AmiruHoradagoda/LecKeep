package com.devProject.leckeep_backend.service.folder;

import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.model.FolderShare;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.FolderShareRepository;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FolderPermissionService {
    private final CurrentUserService currentUserService;
    private final GroupMemberRepository groupMemberRepository;
    private final FolderShareRepository folderShareRepository;
    private final StudyGroupRepository studyGroupRepository;

    public void requireReadAccess(NoteFolder folder) {
        if (!canRead(folder)) {
            throw new SecurityException("You do not have access to this folder");
        }
    }

    public boolean canRead(NoteFolder folder) {
        String userId = currentUserService.requireCurrentUserId();
        if (userId == null || userId.isBlank() || folder == null
                || folder.getId() == null || folder.getId().isBlank()) {
            return false;
        }
        if (userId.equals(folder.getOwnerId())) {
            return true;
        }
        if (folder.getVisibility() == FolderVisibility.GLOBAL) {
            return true;
        }
        if (folder.getVisibility() == FolderVisibility.GROUP && isMember(folder.getGroupId(), userId)) {
            return true;
        }
        return folderShareRepository.findByFolderId(folder.getId()).stream()
                .anyMatch(share -> grantsRead(share, folder, userId));
    }

    private boolean grantsRead(FolderShare share, NoteFolder folder, String userId) {
        if (share == null || !folder.getId().equals(share.getFolderId())
                || folder.getOwnerId() == null || folder.getOwnerId().isBlank()
                || !folder.getOwnerId().equals(share.getOwnerId())
                || (share.getPermission() != SharePermission.READ && share.getPermission() != SharePermission.WRITE)
                || share.getTargetType() == null) {
            return false;
        }
        return switch (share.getTargetType()) {
            case USER -> userId.equals(share.getTargetId());
            case GROUP -> isMember(share.getTargetId(), userId);
            case GLOBAL -> true;
        };
    }

    private boolean isMember(String groupId, String userId) {
        return groupId != null && !groupId.isBlank()
                && studyGroupRepository.existsById(groupId)
                && groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }
}
