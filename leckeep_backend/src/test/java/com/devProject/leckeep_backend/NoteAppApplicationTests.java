package com.devProject.leckeep_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.devProject.leckeep_backend.repository.DocumentFileRepository;
import com.devProject.leckeep_backend.repository.FolderShareRepository;
import com.devProject.leckeep_backend.repository.GroupMemberRepository;
import com.devProject.leckeep_backend.repository.NoteFolderRepository;
import com.devProject.leckeep_backend.repository.StudyGroupRepository;
import com.devProject.leckeep_backend.repository.SubjectRepository;
import com.devProject.leckeep_backend.repository.UserRepository;
import com.devProject.leckeep_backend.service.document.storage.ObjectStorageService;
import io.minio.MinioClient;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration"
})
class NoteAppApplicationTests {

	@MockBean
	private MongoTemplate mongoTemplate;

	@MockBean
	private UserRepository userRepository;

	@MockBean
	private SubjectRepository subjectRepository;

	@MockBean
	private NoteFolderRepository noteFolderRepository;

	@MockBean
	private DocumentFileRepository documentFileRepository;

	@MockBean
	private StudyGroupRepository studyGroupRepository;

	@MockBean
	private GroupMemberRepository groupMemberRepository;

	@MockBean
	private FolderShareRepository folderShareRepository;

	@MockBean
	private ObjectStorageService objectStorageService;

	@MockBean
	private MinioClient minioClient;

	@Test
	void contextLoads() {
	}

}
