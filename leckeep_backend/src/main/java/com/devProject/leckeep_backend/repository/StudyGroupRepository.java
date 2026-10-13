package com.devProject.leckeep_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.devProject.leckeep_backend.model.StudyGroup;

import java.util.List;
import java.util.Collection;

@Repository
public interface StudyGroupRepository extends MongoRepository<StudyGroup, String> {
    List<StudyGroup> findByOwnerId(String ownerId);

    Page<StudyGroup> findByOwnerId(String ownerId, Pageable pageable);

    Page<StudyGroup> findByOwnerIdOrIdIn(String ownerId, Collection<String> ids, Pageable pageable);

    List<StudyGroup> findByNameContainingIgnoreCase(String name);
}
