package com.devProject.leckeep_backend.dto.response;

import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FolderShareResponseDto {
    private String id;
    private String folderId;
    private ShareTargetType targetType;
    private String targetId;
    private SharePermission permission;
    private LocalDateTime createdAt;
}
