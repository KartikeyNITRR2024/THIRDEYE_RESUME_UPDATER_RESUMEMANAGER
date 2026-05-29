package com.thirdeye30.resumehelper.resumemanager.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.thirdeye30.resumehelper.resumemanager.enums.Status;

import lombok.Data;

@Data
public class ResumeAdminDto {
	private UUID id;
    private UUID userId;
    private Boolean isOriginalResumeUploaded;
    private UUID awsPathOriginal;
    private Boolean isUpdatedResumeUploaded;
    private UUID awsPathUpdated;
    private Status status;
    private String encryptionkey;
    private String encryptionkeyForUpdatedResume;
    private String name;
    private String email;
    private String originalURL;
    private String updatedURL;
    private LocalDateTime createTime;
}
