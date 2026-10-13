package com.devProject.leckeep_backend.service.group;

import com.devProject.leckeep_backend.dto.requests.GroupMemberRequestDto;
import com.devProject.leckeep_backend.dto.requests.StudyGroupRequestDto;
import com.devProject.leckeep_backend.dto.response.GroupMemberResponseDto;
import com.devProject.leckeep_backend.dto.response.StudyGroupResponseDto;
import com.devProject.leckeep_backend.dto.response.pagination.StudyGroupPaginateResponseDto;

public interface GroupService {
    StudyGroupPaginateResponseDto getGroups(int page, int size);

    StudyGroupPaginateResponseDto getShareableGroups(int page, int size);

    StudyGroupResponseDto createGroup(StudyGroupRequestDto request);

    StudyGroupResponseDto getGroupById(String id);

    StudyGroupResponseDto updateGroup(String id, StudyGroupRequestDto request);

    void deleteGroup(String id);

    GroupMemberResponseDto addGroupMember(String groupId, GroupMemberRequestDto request);

    void removeGroupMember(String groupId, String userId);
}
