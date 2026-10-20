package com.zgl.zflow.domain.office.gateway;
public interface IArtifactStorage {
    record StoredFile(String objectKey, String contentHash, long sizeBytes) {}
    StoredFile writeImmutable(long ownerId, String artifactId, String revisionId, byte[] content);
    byte[] readVerified(String objectKey, String expectedHash, long expectedSize);
    long countOrphans(java.util.Set<String> referencedKeys);
}
