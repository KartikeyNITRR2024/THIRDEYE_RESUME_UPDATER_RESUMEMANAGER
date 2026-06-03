package com.thirdeye30.resumehelper.resumemanager.services;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.thirdeye30.resumehelper.resumemanager.dtos.DownloadOriginalResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeAdminDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeMetadata;
import com.thirdeye30.resumehelper.resumemanager.dtos.TextResume;
import com.thirdeye30.resumehelper.resumemanager.enums.Status;
import com.thirdeye30.resumehelper.resumemanager.enums.Type;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeContentDto;

public interface ResumeService {
    ResumeDto uploadResumePdf(ResumeMetadata metadata, MultipartFile file);
    ResumeDto uploadResumeText(TextResume request);
    ResumeDto getResumeById(UUID id);
    List<ResumeDto> getResumesByUserId(UUID userId);
    void updateStatus(UUID id, Status status, String name, String email, String content);
    void updateUpdatedResume(UUID id, UUID awsPath);
    ResumeAdminDto getResumeByIdForAdmin(UUID id);
	List<ResumeAdminDto> getResumesByUserIdForAdmin(UUID userId);
	DownloadOriginalResumeDto downloadAndDecrypt(Type type, UUID fileKey);
	void updateStatusInBatch();
	void processStaleUpdates();
	ResumeContentDto getResumeContent(UUID id);
    Page<ResumeDto> getResumesByEmail(String email, Pageable pageable);
	ResumeDto finalSubmit(UUID id);
}