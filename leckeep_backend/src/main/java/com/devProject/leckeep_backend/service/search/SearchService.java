package com.devProject.leckeep_backend.service.search;

import com.devProject.leckeep_backend.dto.response.SearchResultResponseDto;
import com.devProject.leckeep_backend.dto.response.SearchResultResponseDto.ResourceType;
import com.devProject.leckeep_backend.model.DocumentFile;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.model.Subject;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.folder.FolderPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SearchService {
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int RESULTS_PER_TYPE = 20;
    private static final Set<String> SCOPES = Set.of("all", "subjects", "folders", "documents");

    private final MongoTemplate mongoTemplate;
    private final NoteFolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final FolderPermissionService folderPermissionService;

    public List<SearchResultResponseDto> search(String input, String scope) {
        String userId = currentUserService.requireCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("Authenticated user is required");
        }
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase(Locale.ROOT);
        if (!SCOPES.contains(normalizedScope)) {
            throw new IllegalArgumentException("Unsupported search scope. Use all, subjects, folders, or documents");
        }
        String term = input == null ? "" : input.trim();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query must be at most 100 characters");
        }
        if (term.isEmpty()) {
            return List.of();
        }
        Pattern literal = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<SearchResultResponseDto> results = new ArrayList<>();

        if (normalizedScope.equals("all") || normalizedScope.equals("subjects")) {
            Query query = matching(literal, "name").addCriteria(Criteria.where("ownerId").is(userId))
                    .limit(RESULTS_PER_TYPE);
            mongoTemplate.find(query, Subject.class).forEach(subject -> results.add(SearchResultResponseDto.builder()
                    .type(ResourceType.SUBJECT).id(subject.getId()).name(subject.getName()).build()));
        }
        if (normalizedScope.equals("all") || normalizedScope.equals("folders")) {
            // Stream database matches: denied candidates do not consume the result allowance.
            try (Stream<NoteFolder> folders = mongoTemplate.stream(matching(literal, "title", "description"), NoteFolder.class)) {
                folders.filter(folderPermissionService::canRead).limit(RESULTS_PER_TYPE)
                        .forEach(folder -> results.add(SearchResultResponseDto.builder()
                                .type(ResourceType.FOLDER).id(folder.getId()).name(folder.getTitle())
                                .subjectId(folder.getSubjectId()).build()));
            }
        }
        if (normalizedScope.equals("all") || normalizedScope.equals("documents")) {
            try (Stream<DocumentFile> documents = mongoTemplate.stream(
                    matching(literal, "originalName", "contentType", "extension"), DocumentFile.class)) {
                documents.filter(this::canReadDocument).limit(RESULTS_PER_TYPE)
                        .forEach(document -> results.add(SearchResultResponseDto.builder()
                                .type(ResourceType.DOCUMENT).id(document.getId()).name(document.getOriginalName())
                                .folderId(document.getFolderId()).contentType(document.getContentType())
                                .extension(document.getExtension()).build()));
            }
        }
        return results;
    }

    private Query matching(Pattern literal, String... fields) {
        Criteria[] matches = Stream.of(fields).map(field -> Criteria.where(field).regex(literal)).toArray(Criteria[]::new);
        return new Query(new Criteria().orOperator(matches))
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .cursorBatchSize(100);
    }

    private boolean canReadDocument(DocumentFile document) {
        if (document.getFolderId() == null || document.getFolderId().isBlank()) {
            return false;
        }
        return folderRepository.findById(document.getFolderId())
                .map(folderPermissionService::canRead).orElse(false);
    }
}
