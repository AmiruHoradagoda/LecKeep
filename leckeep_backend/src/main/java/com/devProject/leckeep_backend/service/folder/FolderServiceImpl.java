package com.devProject.leckeep_backend.service.folder;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.devProject.leckeep_backend.dto.requests.NoteFolderRequestDto;
import com.devProject.leckeep_backend.dto.response.NoteFolderResponseDto;
import com.devProject.leckeep_backend.dto.response.pagination.NoteFolderPaginateResponseDto;
import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.exception.FolderNotFoundException;
import com.devProject.leckeep_backend.mappers.NoteFolderMapper;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.group.GroupAccessService;
import com.devProject.leckeep_backend.utils.PaginationUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {
    private static final int MAX_PAGE_SIZE = 100;

    private final NoteFolderRepository noteFolderRepository;
    private final NoteFolderMapper noteFolderMapper;
    private final CurrentUserService currentUserService;
    private final FolderPermissionService folderPermissionService;
    private final GroupAccessService groupAccessService;

    @Override
    public NoteFolderPaginateResponseDto getFolders(String scope, int page, int size) {
        Pageable pageable = PaginationUtils.buildPageable(
                page,
                size,
                MAX_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt")
        );
        String normalizedScope = normalizeScope(scope);

        Page<NoteFolder> folderPage = switch (normalizedScope) {
            case "private" -> noteFolderRepository.findByOwnerIdAndVisibility(
                    currentUserService.requireCurrentUserId(),
                    FolderVisibility.PRIVATE,
                    pageable
            );
            case "global" -> noteFolderRepository.findByVisibility(FolderVisibility.GLOBAL, pageable);
            case "group" -> getReadableGroupFolders(pageable);
            case "shared" -> noteFolderRepository.findByOwnerIdAndVisibilityIn(
                    currentUserService.requireCurrentUserId(),
                    List.of(FolderVisibility.GLOBAL, FolderVisibility.GROUP),
                    pageable
            );
            default -> noteFolderRepository.findByOwnerId(currentUserService.requireCurrentUserId(), pageable);
        };

        List<NoteFolderResponseDto> folders = folderPage.getContent()
                .stream()
                .map(noteFolderMapper::toNoteFolderResponseDto)
                .toList();

        return NoteFolderPaginateResponseDto.builder()
                .dataList(folders)
                .dataCount(folderPage.getTotalElements())
                .build();
    }

    @Override
    public NoteFolderResponseDto getFolderById(String id) {
        NoteFolder noteFolder = findFolderOrThrow(id);
        folderPermissionService.requireReadAccess(noteFolder);
        return noteFolderMapper.toNoteFolderResponseDto(noteFolder);
    }

    @Override
    public NoteFolderResponseDto createFolder(NoteFolderRequestDto request) {
        validateFolderRequest(request);

        NoteFolder noteFolder = noteFolderMapper.toNoteFolder(request, currentUserService.requireCurrentUserId());
        NoteFolder savedFolder = noteFolderRepository.save(noteFolder);
        return noteFolderMapper.toNoteFolderResponseDto(savedFolder);
    }

    @Override
    public NoteFolderResponseDto updateFolder(String id, NoteFolderRequestDto request) {
        NoteFolder noteFolder = findFolderOrThrow(id);
        requireFolderOwner(noteFolder);
        validateFolderRequest(request);
        noteFolderMapper.updateNoteFolder(noteFolder, request);

        NoteFolder updatedFolder = noteFolderRepository.save(noteFolder);
        return noteFolderMapper.toNoteFolderResponseDto(updatedFolder);
    }

    @Override
    public void deleteFolder(String id) {
        NoteFolder noteFolder = findFolderOrThrow(id);
        requireFolderOwner(noteFolder);
        noteFolderRepository.delete(noteFolder);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "private";
        }
        return scope.trim().toLowerCase();
    }

    private NoteFolder findFolderOrThrow(String id) {
        return noteFolderRepository.findById(id)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found with id: " + id));
    }

    private void validateFolderRequest(NoteFolderRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Folder request is required");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Folder title is required");
        }
        if (request.getVisibility() == FolderVisibility.GROUP) {
            request.setGroupId(groupAccessService.requireSharingAccess(request.getGroupId()).getId());
        }
    }

    private Page<NoteFolder> getReadableGroupFolders(Pageable pageable) {
        // Authorize before pagination so counts and pages cannot expose inaccessible folders.
        List<NoteFolder> readable = noteFolderRepository
                .findByVisibility(FolderVisibility.GROUP, Pageable.unpaged(pageable.getSort()))
                .stream()
                .filter(folderPermissionService::canRead)
                .toList();
        int start = (int) Math.min(pageable.getOffset(), readable.size());
        int end = (int) Math.min((long) start + pageable.getPageSize(), readable.size());
        return new PageImpl<>(readable.subList(start, end), pageable, readable.size());
    }

    private void requireFolderOwner(NoteFolder noteFolder) {
        String currentUserId = currentUserService.requireCurrentUserId();
        if (!currentUserId.equals(noteFolder.getOwnerId())) {
            throw new SecurityException("You do not have access to this folder");
        }
    }
}
