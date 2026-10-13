package com.devProject.leckeep_backend.service.group;

import com.devProject.leckeep_backend.dto.requests.GroupMemberRequestDto;
import com.devProject.leckeep_backend.dto.requests.StudyGroupRequestDto;
import com.devProject.leckeep_backend.dto.response.GroupMemberResponseDto;
import com.devProject.leckeep_backend.dto.response.StudyGroupResponseDto;
import com.devProject.leckeep_backend.dto.response.pagination.StudyGroupPaginateResponseDto;
import com.devProject.leckeep_backend.enums.GroupRole;
import com.devProject.leckeep_backend.exception.GroupNotFoundException;
import com.devProject.leckeep_backend.exception.UserNotFoundException;
import com.devProject.leckeep_backend.mappers.GroupMemberMapper;
import com.devProject.leckeep_backend.mappers.StudyGroupMapper;
import com.devProject.leckeep_backend.model.GroupMember;
import com.devProject.leckeep_backend.model.StudyGroup;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.repository.UserRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import com.devProject.leckeep_backend.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private static final int MAX_PAGE_SIZE = 100;

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final StudyGroupMapper studyGroupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final CurrentUserService currentUserService;

    @Override
    public StudyGroupPaginateResponseDto getGroups(int page, int size) {
        Pageable pageable = PaginationUtils.buildPageable(
                page,
                size,
                MAX_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt")
        );
        Page<StudyGroup> groupPage = studyGroupRepository.findByOwnerId(
                currentUserService.requireCurrentUserId(),
                pageable
        );

        return StudyGroupPaginateResponseDto.builder()
                .dataList(groupPage.getContent()
                        .stream()
                        .map(studyGroupMapper::toStudyGroupResponseDto)
                        .toList())
                .dataCount(groupPage.getTotalElements())
                .build();
    }

    @Override
    public StudyGroupPaginateResponseDto getShareableGroups(int page, int size) {
        String userId = currentUserService.requireCurrentUserId();
        List<String> adminGroupIds = groupMemberRepository.findByUserId(userId).stream()
                .filter(member -> member.getRole() == GroupRole.ADMIN)
                .map(GroupMember::getGroupId)
                .filter(id -> id != null && !id.isBlank()).distinct().toList();
        Pageable pageable = PaginationUtils.buildPageable(page, size, MAX_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt", "createdAt"));
        Page<StudyGroup> groupPage = studyGroupRepository.findByOwnerIdOrIdIn(userId, adminGroupIds, pageable);
        return StudyGroupPaginateResponseDto.builder()
                .dataList(groupPage.getContent().stream().map(studyGroupMapper::toStudyGroupResponseDto).toList())
                .dataCount(groupPage.getTotalElements()).build();
    }

    @Override
    public StudyGroupResponseDto createGroup(StudyGroupRequestDto request) {
        validateGroupRequest(request);

        String currentUserId = currentUserService.requireCurrentUserId();
        StudyGroup studyGroup = studyGroupMapper.toStudyGroup(request, currentUserId);
        StudyGroup savedGroup = studyGroupRepository.save(studyGroup);

        groupMemberRepository.save(GroupMember.builder()
                .groupId(savedGroup.getId())
                .userId(currentUserId)
                .role(GroupRole.ADMIN)
                .build());

        return studyGroupMapper.toStudyGroupResponseDto(savedGroup);
    }

    @Override
    public StudyGroupResponseDto getGroupById(String id) {
        StudyGroup studyGroup = findGroupOrThrow(id);
        requireGroupOwner(studyGroup);
        return studyGroupMapper.toStudyGroupResponseDto(studyGroup);
    }

    @Override
    public StudyGroupResponseDto updateGroup(String id, StudyGroupRequestDto request) {
        validateGroupRequest(request);

        StudyGroup studyGroup = findGroupOrThrow(id);
        requireGroupOwner(studyGroup);
        studyGroupMapper.updateStudyGroup(studyGroup, request);

        StudyGroup updatedGroup = studyGroupRepository.save(studyGroup);
        return studyGroupMapper.toStudyGroupResponseDto(updatedGroup);
    }

    @Override
    public void deleteGroup(String id) {
        StudyGroup studyGroup = findGroupOrThrow(id);
        requireGroupOwner(studyGroup);
        groupMemberRepository.deleteByGroupId(id);
        studyGroupRepository.delete(studyGroup);
    }

    @Override
    public GroupMemberResponseDto addGroupMember(String groupId, GroupMemberRequestDto request) {
        validateGroupMemberRequest(request);
        StudyGroup studyGroup = findGroupOrThrow(groupId);
        requireGroupOwner(studyGroup);

        String userId = request.getUserId().trim();
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found with id: " + userId);
        }
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember groupMember = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(request.getRole() != null ? request.getRole() : GroupRole.MEMBER)
                .build();

        GroupMember savedGroupMember = groupMemberRepository.save(groupMember);
        return groupMemberMapper.toGroupMemberResponseDto(savedGroupMember);
    }

    @Override
    public void removeGroupMember(String groupId, String userId) {
        StudyGroup studyGroup = findGroupOrThrow(groupId);
        requireGroupOwner(studyGroup);

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (studyGroup.getOwnerId().equals(userId)) {
            throw new IllegalArgumentException("Group owner cannot be removed from the group");
        }
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("User is not a member of this group");
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    private StudyGroup findGroupOrThrow(String id) {
        return studyGroupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + id));
    }

    private void validateGroupRequest(StudyGroupRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Group request is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Group name is required");
        }
    }

    private void validateGroupMemberRequest(GroupMemberRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Group member request is required");
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
    }

    private void requireGroupOwner(StudyGroup studyGroup) {
        String currentUserId = currentUserService.requireCurrentUserId();
        if (!currentUserId.equals(studyGroup.getOwnerId())) {
            throw new SecurityException("You do not have access to this group");
        }
    }
}
