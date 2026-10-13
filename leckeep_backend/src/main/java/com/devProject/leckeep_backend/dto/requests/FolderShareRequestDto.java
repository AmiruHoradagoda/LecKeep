package com.devProject.leckeep_backend.dto.requests;

import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderShareRequestDto {
    private ShareTargetType targetType;
    private String targetId;
    private SharePermission permission;
}
