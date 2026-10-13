package com.devProject.leckeep_backend.service.document.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinioPreviewUrlTests {
    @Test
    void previewUsesBrowserEndpointWithoutContactingInternalMinio() {
        MinioClient internal = mock(MinioClient.class);
        MinioClient publicSigner = MinioClient.builder().endpoint("http://localhost:19000")
                .region("us-east-1").credentials("local-test-user", "local-test-password").build();
        MinioObjectStorageAdapter adapter = new MinioObjectStorageAdapter(internal, publicSigner);
        ReflectionTestUtils.setField(adapter, "bucketName", "leckeep-documents");

        URI preview = URI.create(adapter.generatePreviewUrl("folders/test/lecture.pdf"));

        assertEquals("localhost", preview.getHost());
        assertEquals(19000, preview.getPort());
        assertEquals("/leckeep-documents/folders/test/lecture.pdf", preview.getPath());
        assertTrue(preview.getQuery().contains("X-Amz-Signature="));
        assertTrue(preview.getQuery().contains("X-Amz-Expires=900"));
        verifyNoInteractions(internal);
    }
}
