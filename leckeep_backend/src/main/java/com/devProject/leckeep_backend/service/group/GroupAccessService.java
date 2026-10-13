package com.devProject.leckeep_backend.service.group;

import com.devProject.leckeep_backend.enums.GroupRole;
import com.devProject.leckeep_backend.exception.GroupNotFoundException;
import com.devProject.leckeep_backend.model.StudyGroup;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.service.auth.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupAccessService {
    private final StudyGroupRepository groups;
    private final GroupMemberRepository members;
    private final CurrentUserService currentUserService;

    public StudyGroup requireSharingAccess(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("Group id is required");
        }
        String userId = currentUserService.requireCurrentUserId();
        StudyGroup group = groups.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        if (userId.equals(group.getOwnerId())) {
            return group;
        }
        boolean admin = members.findByGroupIdAndUserId(groupId, userId)
                .map(member -> member.getRole() == GroupRole.ADMIN).orElse(false);
        if (!admin) {
            throw new SecurityException("Only a group owner or admin may assign or share folders with this group");
        }
        return group;
    }
}
