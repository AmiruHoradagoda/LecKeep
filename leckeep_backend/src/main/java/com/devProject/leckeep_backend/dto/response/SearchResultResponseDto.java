package com.devProject.leckeep_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultResponseDto {
    public enum ResourceType { SUBJECT, FOLDER, DOCUMENT }

    private ResourceType type;
    private String id;
    private String name;
    private String folderId;
    private String subjectId;
    private String contentType;
    private String extension;
}
