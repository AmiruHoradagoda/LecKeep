package com.devProject.leckeep_backend.service.folder;

import com.devProject.leckeep_backend.dto.requests.FolderShareRequestDto;
import com.devProject.leckeep_backend.dto.response.FolderShareResponseDto;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.model.FolderShare;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.FolderShareRepository;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.repository.UserRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.group.GroupAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FolderShareService {
    private final NoteFolderRepository folders;
    private final FolderShareRepository shares;
    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final GroupAccessService groupAccessService;

    public List<FolderShareResponseDto> getShares(String folderId) {
        NoteFolder folder = requireOwner(folderId);
        return shares.findByFolderId(folder.getId()).stream().map(this::toResponse).toList();
    }

    public FolderShareResponseDto createShare(String folderId, FolderShareRequestDto request) {
        NoteFolder folder = requireOwner(folderId);
        folderId = folder.getId();
        if (request == null || request.getTargetType() == null || request.getPermission() == null) {
            throw new IllegalArgumentException("Share target type and permission are required");
        }
        String targetId = request.getTargetId() == null ? null : request.getTargetId().trim();
        if (request.getTargetType() == ShareTargetType.GLOBAL) {
            if (targetId != null && !targetId.isEmpty()) {
                throw new IllegalArgumentException("GLOBAL shares must not specify a target id");
            }
            targetId = null;
        } else {
            if (targetId == null || targetId.isEmpty()) {
                throw new IllegalArgumentException("Target id is required for USER and GROUP shares");
            }
            if (request.getTargetType() == ShareTargetType.USER) {
                targetId = users.findById(targetId)
                        .orElseThrow(() -> new IllegalArgumentException("Target user does not exist")).getId();
            }
            if (request.getTargetType() == ShareTargetType.GROUP) {
                targetId = groupAccessService.requireSharingAccess(targetId).getId();
            }
        }
        FolderShare existing = shares.findByFolderIdAndTargetTypeAndTargetId(folderId, request.getTargetType(), targetId)
                .orElse(null);
        if (existing != null) {
            return equivalentShare(existing, folder, request);
        }
        // A stable primary key also prevents concurrent duplicates when Mongo's optional compound index is absent.
        String shareId = UUID.nameUUIDFromBytes((folderId + "\n" + request.getTargetType() + "\n"
                + (targetId == null ? "" : targetId)).getBytes(StandardCharsets.UTF_8)).toString();
        FolderShare share = FolderShare.builder().id(shareId).folderId(folderId).ownerId(folder.getOwnerId())
                .targetType(request.getTargetType()).targetId(targetId).permission(request.getPermission()).build();
        try {
            return toResponse(shares.insert(share));
        } catch (DuplicateKeyException ex) {
            FolderShare concurrent = shares.findByFolderIdAndTargetTypeAndTargetId(folderId, request.getTargetType(), targetId)
                    .orElseThrow(() -> new IllegalArgumentException("Share already exists; reload current shares"));
            return equivalentShare(concurrent, folder, request);
        }
    }

    public void revokeShare(String folderId, String shareId) {
        NoteFolder folder = requireOwner(folderId);
        if (shareId == null || shareId.isBlank()) {
            throw new IllegalArgumentException("Share id is required");
        }
        FolderShare share = shares.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!folder.getId().equals(share.getFolderId())) {
            throw new IllegalArgumentException("Share does not belong to this folder");
        }
        shares.delete(share);
    }

    private NoteFolder requireOwner(String folderId) {
        String userId = currentUserService.requireCurrentUserId();
        NoteFolder folder = folders.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found"));
        if (!userId.equals(folder.getOwnerId())) {
            throw new SecurityException("Only the folder owner may manage shares");
        }
        return folder;
    }

    private FolderShareResponseDto equivalentShare(FolderShare existing, NoteFolder folder, FolderShareRequestDto request) {
        if (!folder.getOwnerId().equals(existing.getOwnerId()) || existing.getPermission() != request.getPermission()) {
            throw new IllegalArgumentException("A different share exists for this target; revoke it before creating a new share");
        }
        return toResponse(existing);
    }

    private FolderShareResponseDto toResponse(FolderShare share) {
        return FolderShareResponseDto.builder().id(share.getId()).folderId(share.getFolderId())
                .targetType(share.getTargetType()).targetId(share.getTargetId()).permission(share.getPermission())
                .createdAt(share.getCreatedAt()).build();
    }
}
