package com.devProject.leckeep_backend.service.search;

import com.devProject.leckeep_backend.dto.response.SearchResultResponseDto;
import com.devProject.leckeep_backend.dto.response.SearchResultResponseDto.ResourceType;
import com.devProject.leckeep_backend.enums.FolderVisibility;
import com.devProject.leckeep_backend.enums.SharePermission;
import com.devProject.leckeep_backend.enums.ShareTargetType;
import com.devProject.leckeep_backend.model.DocumentFile;
import com.devProject.leckeep_backend.model.FolderShare;
import com.devProject.leckeep_backend.model.NoteFolder;
import com.devProject.leckeep_backend.model.Subject;
import com.devProject.leckeep_backend.repository.FolderShareRepository;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.service.folder.FolderPermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTests {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final NoteFolderRepository folders = mock(NoteFolderRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final FolderShareRepository shares = mock(FolderShareRepository.class);
    private final StudyGroupRepository groups = mock(StudyGroupRepository.class);
    private final FolderPermissionService permissions = new FolderPermissionService(users, members, shares, groups);
    private final SearchService search = new SearchService(mongo, folders, users, permissions);

    @BeforeEach
    void setUp() {
        when(users.requireCurrentUserId()).thenReturn("reader");
        when(mongo.find(any(Query.class), eq(Subject.class))).thenReturn(List.of());
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.empty());
        when(mongo.stream(any(Query.class), eq(DocumentFile.class))).thenAnswer(invocation -> Stream.empty());
    }

    @Test
    void subjectPartialSearchIsConstrainedToCurrentOwnerInDatabase() {
        Subject subject = Subject.builder().id("subject").ownerId("reader").name("Database Systems").build();
        when(mongo.find(any(Query.class), eq(Subject.class))).thenReturn(List.of(subject));
        assertEquals("Database Systems", search.search("  database  ", "subjects").get(0).getName());
        Query query = subjectQuery();
        assertEquals("reader", query.getQueryObject().getString("ownerId"));
        assertEquals(20, query.getLimit());
        assertTrue(pattern(query, "name").matcher("Introduction to DATABASES").find());
        assertFalse(pattern(query, "name").matcher("Networks").find());
    }

    @ParameterizedTest
    @CsvSource({"title,Database Systems", "description,Introduction to DATABASES"})
    void folderQueriesMatchTitleAndDescription(String field, String text) {
        NoteFolder folder = folder("folder", FolderVisibility.PRIVATE, "reader");
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        assertEquals("folder", search.search("database", "folders").get(0).getId());
        assertTrue(pattern(streamQuery(NoteFolder.class), field).matcher(text).find());
    }

    @ParameterizedTest
    @CsvSource({"originalName,database-notes.pdf,database", "contentType,application/pdf,PDF", "extension,.pdf,PD"})
    void documentQueriesMatchFilenameAndMetadata(String field, String text, String term) {
        NoteFolder folder = folder("folder", FolderVisibility.PRIVATE, "reader");
        documentCandidates(folder, document("document", folder.getId()));
        assertEquals("document", search.search(term, "documents").get(0).getId());
        assertTrue(pattern(streamQuery(DocumentFile.class), field).matcher(text).find());
    }

    @Test
    void otherUsersPrivateFoldersAndDocumentsAreHidden() {
        NoteFolder folder = folder("private", FolderVisibility.PRIVATE, "other");
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        documentCandidates(folder, document("private-document", folder.getId()));
        assertTrue(search.search("database", "all").isEmpty());
        assertEquals("reader", subjectQuery().getQueryObject().getString("ownerId"));
    }

    @Test
    void globalFolderAndItsDocumentAreSearchable() {
        NoteFolder folder = folder("global", FolderVisibility.GLOBAL, "other");
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        documentCandidates(folder, document("global-document", folder.getId()));
        assertEquals(List.of(ResourceType.FOLDER, ResourceType.DOCUMENT),
                search.search("database", "all").stream().map(SearchResultResponseDto::getType).toList());
    }

    @Test
    void groupMembershipControlsFolderAndDocumentSearch() {
        NoteFolder folder = folder("group-folder", FolderVisibility.GROUP, "other");
        folder.setGroupId("group");
        when(groups.existsById("group")).thenReturn(true);
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        documentCandidates(folder, document("group-document", folder.getId()));
        assertTrue(search.search("database", "all").isEmpty());
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
        assertEquals(2, search.search("database", "all").size());
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(false);
        assertTrue(search.search("database", "all").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "GROUP", "GLOBAL"})
    void explicitShareControlsFolderAndDocumentSearch(String target) {
        NoteFolder folder = folder("shared", FolderVisibility.PRIVATE, "other");
        when(mongo.stream(any(Query.class), eq(NoteFolder.class))).thenAnswer(invocation -> Stream.of(folder));
        documentCandidates(folder, document("shared-document", folder.getId()));
        when(groups.existsById("group")).thenReturn(true);
        when(members.existsByGroupIdAndUserId("group", "reader")).thenReturn(true);
        FolderShare share = FolderShare.builder().folderId("shared").ownerId("other")
                .targetType(ShareTargetType.valueOf(target)).targetId(target.equals("USER") ? "reader" : "group")
                .permission(SharePermission.WRITE).build();
        when(shares.findByFolderId("shared")).thenReturn(List.of(share));
        assertEquals(2, search.search("database", "all").size());
        when(shares.findByFolderId("shared")).thenReturn(List.of());
        assertTrue(search.search("database", "all").isEmpty());
    }

    @Test
    void missingOrInvalidParentNeverExposesDocument() {
        when(mongo.stream(any(Query.class), eq(DocumentFile.class))).thenAnswer(invocation -> Stream.of(
                document("orphan", "missing"), document("no-parent", null), document("blank-parent", " ")));
        assertTrue(search.search("database", "documents").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankQueryReturnsEmptyWithoutDatabaseAccess(String term) {
        assertTrue(search.search(term, "all").isEmpty());
        verifyNoInteractions(mongo, folders);
    }

    @ParameterizedTest
    @ValueSource(strings = {".*", "[abc]", "a+b", "\\E.*\\Q", "(a+)+$"})
    void regexCharactersAreLiteral(String term) {
        search.search(term, "subjects");
        Pattern pattern = pattern(subjectQuery(), "name");
        assertTrue(pattern.matcher("prefix " + term + " suffix").find());
        assertFalse(pattern.matcher("aaaaa database").find());
    }

    @Test
    void resultLimitsApplyAfterPermissionFilteringAndCloseCursors() {
        List<NoteFolder> candidates = new ArrayList<>();
        IntStream.range(0, 25).forEach(i -> candidates.add(folder("hidden-" + i, FolderVisibility.PRIVATE, "other")));
        IntStream.range(0, 25).forEach(i -> candidates.add(folder("visible-" + i, FolderVisibility.PRIVATE, "reader")));
        Runnable foldersClosed = mock(Runnable.class);
        Runnable documentsClosed = mock(Runnable.class);
        when(mongo.stream(any(Query.class), eq(NoteFolder.class)))
                .thenAnswer(invocation -> candidates.stream().onClose(foldersClosed));
        for (NoteFolder folder : candidates) when(folders.findById(folder.getId())).thenReturn(Optional.of(folder));
        when(mongo.stream(any(Query.class), eq(DocumentFile.class))).thenAnswer(invocation -> candidates.stream()
                .map(folder -> document("doc-" + folder.getId(), folder.getId())).onClose(documentsClosed));
        List<SearchResultResponseDto> results = search.search("database", "all");
        assertEquals(20, results.stream().filter(item -> item.getType() == ResourceType.FOLDER).count());
        assertEquals(20, results.stream().filter(item -> item.getType() == ResourceType.DOCUMENT).count());
        assertTrue(results.stream().noneMatch(item -> item.getId().contains("hidden")));
        verify(foldersClosed).run();
        verify(documentsClosed).run();
        assertEquals(20, subjectQuery().getLimit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"private", "invalid", ""})
    void unsupportedScopesAreRejected(String scope) {
        assertThrows(IllegalArgumentException.class, () -> search.search("database", scope));
        verifyNoInteractions(mongo);
    }

    @Test
    void excessiveQueryLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> search.search("x".repeat(101), "all"));
        verifyNoInteractions(mongo);
    }

    @Test
    void unauthenticatedSearchIsDeniedEvenForBlankQuery() {
        when(users.requireCurrentUserId()).thenThrow(new IllegalStateException("Authenticated user is required"));
        assertThrows(IllegalStateException.class, () -> search.search("", "all"));
        verifyNoInteractions(mongo);
    }

    @Test
    void missingAuthenticatedUserIdCannotSearchOwnerlessResources() {
        when(users.requireCurrentUserId()).thenReturn(null);
        assertThrows(SecurityException.class, () -> search.search("database", "all"));
        verifyNoInteractions(mongo);
    }

    @Test
    void responseContainsOnlyPublicSearchMetadata() throws Exception {
        NoteFolder folder = folder("folder", FolderVisibility.GLOBAL, "other");
        DocumentFile doc = document("document", "folder");
        doc.setStoredKey("internal/storage/key");
        doc.setChecksum("secret-checksum");
        documentCandidates(folder, doc);
        var json = new ObjectMapper().valueToTree(search.search("database", "documents").get(0));
        assertEquals(6, json.size());
        assertEquals("DOCUMENT", json.get("type").asText());
        assertEquals("folder", json.get("folderId").asText());
        assertFalse(json.toString().contains("internal/storage/key"));
        assertFalse(json.toString().contains("secret-checksum"));
        assertFalse(json.has("ownerId"));
        assertFalse(json.has("previewUrl"));
    }

    private NoteFolder folder(String id, FolderVisibility visibility, String owner) {
        return NoteFolder.builder().id(id).ownerId(owner).visibility(visibility).title("Database Notes").build();
    }

    private DocumentFile document(String id, String folderId) {
        return DocumentFile.builder().id(id).folderId(folderId).ownerId("reader")
                .originalName("database-notes.pdf").contentType("application/pdf").extension(".pdf").build();
    }

    private void documentCandidates(NoteFolder folder, DocumentFile document) {
        when(folders.findById(folder.getId())).thenReturn(Optional.of(folder));
        when(mongo.stream(any(Query.class), eq(DocumentFile.class))).thenAnswer(invocation -> Stream.of(document));
    }

    private Query subjectQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo, atLeastOnce()).find(captor.capture(), eq(Subject.class));
        return captor.getValue();
    }

    private Query streamQuery(Class<?> type) {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).stream(captor.capture(), eq(type));
        return captor.getValue();
    }

    private Pattern pattern(Query query, String field) {
        List<Document> clauses = query.getQueryObject().getList("$or", Document.class);
        return clauses.stream().filter(clause -> clause.containsKey(field))
                .map(clause -> (Pattern) clause.get(field)).findFirst().orElseThrow();
    }
}
