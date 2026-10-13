package com.devProject.leckeep_backend.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;

import java.time.LocalDateTime;

@Data
@Document(collection = "folder_shares")
@CompoundIndex(name = "folder_share_unique", def = "{'folderId': 1, 'targetType': 1, 'targetId': 1}", unique = true)
@Builder
public class FolderShare {
    @Id
    private String id;
    @Indexed
    private String folderId;
    @Indexed
    private String ownerId;
    private ShareTargetType targetType;
    private String targetId;
    @Builder.Default
    private SharePermission permission = SharePermission.READ;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
