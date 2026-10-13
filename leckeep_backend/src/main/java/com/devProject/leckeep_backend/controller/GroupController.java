package com.devProject.leckeep_backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devProject.leckeep_backend.dto.requests.GroupMemberRequestDto;
import com.devProject.leckeep_backend.dto.requests.StudyGroupRequestDto;
import com.devProject.leckeep_backend.dto.response.GroupMemberResponseDto;
import com.devProject.leckeep_backend.dto.response.StudyGroupResponseDto;
import com.devProject.leckeep_backend.dto.response.pagination.StudyGroupPaginateResponseDto;
import com.devProject.leckeep_backend.service.group.GroupService;
import com.devProject.leckeep_backend.utils.StandardResponseDto;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public StandardResponseDto getGroups(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "owned") String scope
    ) {
        StudyGroupPaginateResponseDto groups = switch (scope) {
            case "owned" -> groupService.getGroups(page, size);
            case "shareable" -> groupService.getShareableGroups(page, size);
            default -> throw new IllegalArgumentException("Unsupported group scope. Use owned or shareable");
        };
        return new StandardResponseDto(200, "Groups fetched", groups);
    }

    @PostMapping
    public StandardResponseDto createGroup(@RequestBody StudyGroupRequestDto request) {
        StudyGroupResponseDto group = groupService.createGroup(request);
        return new StandardResponseDto(201, "Group created", group);
    }

    @GetMapping("/{id}")
    public StandardResponseDto getGroupById(@PathVariable String id) {
        StudyGroupResponseDto group = groupService.getGroupById(id);
        return new StandardResponseDto(200, "Group fetched", group);
    }

    @PutMapping("/{id}")
    public StandardResponseDto updateGroup(@PathVariable String id, @RequestBody StudyGroupRequestDto request) {
        StudyGroupResponseDto group = groupService.updateGroup(id, request);
        return new StandardResponseDto(200, "Group updated", group);
    }

    @DeleteMapping("/{id}")
    public StandardResponseDto deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
        return new StandardResponseDto(200, "Group deleted", null);
    }

    @PostMapping("/{id}/members")
    public StandardResponseDto addGroupMember(@PathVariable String id, @RequestBody GroupMemberRequestDto request) {
        GroupMemberResponseDto member = groupService.addGroupMember(id, request);
        return new StandardResponseDto(200, "Group member added", member);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public StandardResponseDto removeGroupMember(@PathVariable String id, @PathVariable String userId) {
        groupService.removeGroupMember(id, userId);
        return new StandardResponseDto(200, "Group member removed", null);
    }
}
