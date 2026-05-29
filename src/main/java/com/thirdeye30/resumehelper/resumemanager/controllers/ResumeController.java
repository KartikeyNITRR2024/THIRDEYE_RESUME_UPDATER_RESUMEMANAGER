package com.thirdeye30.resumehelper.resumemanager.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.thirdeye30.resumehelper.resumemanager.dtos.DownloadOriginalResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeAdminDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeContentDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeMetadata;
import com.thirdeye30.resumehelper.resumemanager.dtos.TextResume;
import com.thirdeye30.resumehelper.resumemanager.enums.Status;
import com.thirdeye30.resumehelper.resumemanager.enums.Type;
import com.thirdeye30.resumehelper.resumemanager.services.ResumeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/resumemanager/v1/resumes")
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ResumeService resumeService;


    @PostMapping(value = "/upload/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeDto> uploadPdf(
            @RequestPart("metadata") ResumeMetadata metadata, 
            @RequestPart("file") MultipartFile file) {
        
        log.info("REST request to upload PDF for user: {}", metadata.getUserId());
        return ResponseEntity.ok(resumeService.uploadResumePdf(metadata, file));
    }

    @PostMapping("/upload/text")
    public ResponseEntity<ResumeDto> uploadText(@RequestBody TextResume request) {
        log.info("REST request to upload text resume for user: {}", request.getMetadata().getUserId());
        return ResponseEntity.ok(resumeService.uploadResumeText(request));
    }

    @GetMapping("/view/{type}/{fileKey}")
    public ResponseEntity<byte[]> viewResume(@PathVariable String type, @PathVariable UUID fileKey) {
    	Type t = Type.ORIGINAL;
    	if(type.equalsIgnoreCase("updated"))
    	{
    		t = Type.UPDATED;
    	}
        log.info("REST request to view/decrypt file: {} and type: {}", fileKey, t);
        
        DownloadOriginalResumeDto downloadOriginalResumeDto  = resumeService.downloadAndDecrypt(t, fileKey);
        

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadOriginalResumeDto.getFileName() + "\"")
                .contentType(downloadOriginalResumeDto.getContentType())
                .body(downloadOriginalResumeDto.getBytes());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ResumeDto>> getResumesByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(resumeService.getResumesByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeDto> getResume(@PathVariable UUID id) {
        return ResponseEntity.ok(resumeService.getResumeById(id));
    }
    
    @GetMapping("/{id}/content")
    public ResponseEntity<ResumeContentDto> getResumeContent(@PathVariable UUID id) {
        return ResponseEntity.ok(resumeService.getResumeContent(id));
    }

    @GetMapping("/admin/user/{userId}")
    public ResponseEntity<List<ResumeAdminDto>> getResumesByUserAdmin(@PathVariable UUID userId) {
        return ResponseEntity.ok(resumeService.getResumesByUserIdForAdmin(userId));
    }

    @GetMapping("/admin/{id}")
    public ResponseEntity<ResumeAdminDto> getResumeAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(resumeService.getResumeByIdForAdmin(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestParam Status status) {
        resumeService.updateStatus(id, status, null, null, null);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/processed-path")
    public ResponseEntity<Void> updateProcessedPath(@PathVariable UUID id, @RequestParam UUID awsPath) {
        resumeService.updateUpdatedResume(id, awsPath);
        return ResponseEntity.noContent().build();
    }
}