package com.thirdeye30.resumehelper.resumemanager.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.thirdeye30.resumehelper.resumemanager.enums.Status;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resumes", indexes = {
    @Index(name = "idx_resume_user_id", columnList = "userId"),
    @Index(name = "idx_resume_aws_path_orig", columnList = "awsPathOriginal"),
    @Index(name = "idx_resume_aws_path_upda", columnList = "awsPathUpdated"),
    @Index(name = "idx_email", columnList = "email")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Builder.Default
    private Boolean isOriginalResumeUploaded = false;

    @Column(unique = true)
    private UUID awsPathOriginal;

    @Builder.Default
    private Boolean isUpdatedResumeUploaded = false;

    @Column(unique = true)
    private UUID awsPathUpdated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(length = 255)
    private String encryptionkey;
    
    @Column(length = 255)
    private String encryptionkeyForUpdatedResume;
    
    private String name;
    
    private String email;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createTime;
}