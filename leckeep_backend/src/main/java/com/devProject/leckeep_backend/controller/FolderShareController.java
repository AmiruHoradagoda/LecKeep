package com.devProject.leckeep_backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.devProject.leckeep_backend.dto.requests.FolderShareRequestDto;
import com.devProject.leckeep_backend.service.folder.FolderShareService;
import lombok.RequiredArgsConstructor;

import com.devProject.leckeep_backend.utils.StandardResponseDto;

@RestController
@RequestMapping("/api/v1/folders/{folderId}/share")
@RequiredArgsConstructor
public class FolderShareController {
    private final FolderShareService folderShareService;

    @GetMapping
    public StandardResponseDto getShares(@PathVariable String folderId) {
        return new StandardResponseDto(200, "Folder shares fetched", folderShareService.getShares(folderId));
    }

    @PostMapping
    public StandardResponseDto shareFolder(@PathVariable String folderId, @RequestBody FolderShareRequestDto request) {
        return new StandardResponseDto(200, "Folder shared", folderShareService.createShare(folderId, request));
    }

    @DeleteMapping
    public StandardResponseDto removeFolderShare(@PathVariable String folderId, @RequestParam String shareId) {
        folderShareService.revokeShare(folderId, shareId);
        return new StandardResponseDto(200, "Folder share removed", null);
    }
}
