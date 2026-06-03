package com.thirdeye30.resumehelper.resumemanager.repos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.thirdeye30.resumehelper.resumemanager.entities.Resume;
import com.thirdeye30.resumehelper.resumemanager.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    /**
     * Finds the resume record using the S3 file key.
     * Required for decryption logic in ResumeServiceImpl.
     */
    Optional<Resume> findByAwsPathOriginal(UUID awsPathOriginal);
    Optional<Resume> findByAwsPathUpdated(UUID awsPathUpdated);

    List<Resume> findByUserId(UUID userId);
    Page<Resume> findByEmail(String email, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE Resume r SET r.status = :status WHERE r.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") Status status);
    
    @Transactional
    @Modifying
    @Query("UPDATE Resume r SET r.status = :status, r.name = :name, r.email = :email, r.awsPathUpdated = :awsPathUpdated, r.encryptionkeyForUpdatedResume = :encryptionkeyForUpdatedResume, r.isUpdatedResumeUploaded = true WHERE r.id = :id")
    int updateResumeDetails(
        @Param("id") UUID id, 
        @Param("status") Status status, 
        @Param("name") String name, 
        @Param("email") String email,
        @Param("awsPathUpdated") UUID fileKey, 
        @Param("encryptionkeyForUpdatedResume") String encKey
    );

    @Transactional
    @Modifying
    @Query("UPDATE Resume r SET r.awsPathOriginal = :awsPath, r.isOriginalResumeUploaded = true WHERE r.id = :id")
    int updateOriginalResumeDetails(@Param("id") UUID id, @Param("awsPath") UUID awsPath);

    @Transactional
    @Modifying
    @Query("UPDATE Resume r SET r.awsPathUpdated = :awsPath, r.isUpdatedResumeUploaded = true WHERE r.id = :id")
    int updateUpdatedResumeDetails(@Param("id") UUID id, @Param("awsPath") UUID awsPath);
}