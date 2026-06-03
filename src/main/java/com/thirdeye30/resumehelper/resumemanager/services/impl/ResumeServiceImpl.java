package com.thirdeye30.resumehelper.resumemanager.services.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;

import com.thirdeye30.resumehelper.resumemanager.dtos.AlProcesserPayload;
import com.thirdeye30.resumehelper.resumemanager.dtos.DownloadOriginalResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeMetadata;
import com.thirdeye30.resumehelper.resumemanager.dtos.StatusResume;
import com.thirdeye30.resumehelper.resumemanager.dtos.TextExtracterPayload;
import com.thirdeye30.resumehelper.resumemanager.dtos.TextResume;
import com.thirdeye30.resumehelper.resumemanager.entities.Resume;
import com.thirdeye30.resumehelper.resumemanager.enums.Status;
import com.thirdeye30.resumehelper.resumemanager.enums.Type;
import com.thirdeye30.resumehelper.resumemanager.repos.ResumeRepository;
import com.thirdeye30.resumehelper.resumemanager.services.MessageBrokerService;
import com.thirdeye30.resumehelper.resumemanager.services.ResumeService;
import com.thirdeye30.resumehelper.resumemanager.services.TokenService;
import com.thirdeye30.resumehelper.resumemanager.utils.CryptoUtils;
import com.thirdeye30.resumehelper.resumemanager.dtos.Message;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeAdminDto;
import com.thirdeye30.resumehelper.resumemanager.dtos.ResumeContentDto;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final S3Template s3Template;
    private final MessageBrokerService messageBrokerService;
    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;
    
    @Value("${thirdeye.bucket.name}")
    private String bucketName;

    @Value("${thirdeye.multimedia.url.starter}")
    private String urlStarter;

    @Value("${thirdeye.redis.resume-prefix}")
    private String redisResumePrefix;

    @Override
    @Transactional
    public ResumeDto uploadResumePdf(ResumeMetadata metadata, MultipartFile file) {
        
        Long tokenLeft = tokenService.getToken(metadata.getUserId());
        
        if(tokenLeft < 2) {
            log.warn("Action blocked: User {} has insufficient tokens ({})", metadata.getUserId(), tokenLeft);
            throw new RuntimeException("Insufficient tokens to proceed");
        }
        
        log.info("Initiating secure PDF upload for user: {}", metadata.getUserId());
        UUID fileKey = UUID.randomUUID();
        String encKey = generateKey() + "-RE-PDF";
        try {
            byte[] encryptedData = CryptoUtils.encrypt(file.getBytes(), encKey);
            uploadToS3(fileKey.toString() + ".pdf", encryptedData);

            Resume resume = Resume.builder()
                    .userId(metadata.getUserId())
                    .awsPathOriginal(fileKey)
                    .encryptionkey(encKey)
                    .isOriginalResumeUploaded(true)
                    .status(Status.UPLOADED)
                    .build();
            
            Resume saved = resumeRepository.save(resume);
            
            TextExtracterPayload textExtracterPayload = new TextExtracterPayload(); 
            textExtracterPayload.setResumeId(saved.getId());
            textExtracterPayload.setAwsPath(saved.getAwsPathOriginal());
            textExtracterPayload.setEncryptionkey(saved.getEncryptionkey());
            textExtracterPayload.setResumeMetadata(metadata);
            messageBrokerService.sendMessages("textextracter", textExtracterPayload);
            tokenService.subtractToken(metadata.getUserId(), 2L);
            return mapToDtoForUser(saved);
        } catch (Exception e) {
            log.error("PDF Upload failed for user: {}", metadata.getUserId(), e);
            throw new RuntimeException("PDF Secure Upload Failed", e);
        }
    }

    @Override
    @Transactional
    public ResumeDto uploadResumeText(TextResume request) {
        log.info("Initiating secure Text upload for user: {}", request.getMetadata().getUserId());
        UUID fileKey = UUID.randomUUID();
        String encKey = generateKey() + "-RE-TEXT";
        try {
            byte[] encryptedData = CryptoUtils.encrypt(request.getContent().getBytes(StandardCharsets.UTF_8), encKey);
            uploadToS3(fileKey.toString() + ".txt", encryptedData);

            Resume resume = Resume.builder()
                    .userId(request.getMetadata().getUserId())
                    .awsPathOriginal(fileKey)
                    .encryptionkey(encKey)
                    .isOriginalResumeUploaded(true)
                    .status(Status.UPLOADED)
                    .build();
            
            Resume saved = resumeRepository.save(resume);
     
            AlProcesserPayload alProcesserPayload = new AlProcesserPayload();
            alProcesserPayload.setResumeId(saved.getId());
            alProcesserPayload.setResumeMetadata(request.getMetadata());
            alProcesserPayload.setContent(request.getContent());
            messageBrokerService.sendMessages("aiprocesser", alProcesserPayload);

            return mapToDtoForUser(saved);
        } catch (Exception e) {
            log.error("Text Upload failed for user: {}", request.getMetadata().getUserId(), e);
            throw new RuntimeException("Text Secure Upload Failed", e);
        }
    }

    @Override
    public DownloadOriginalResumeDto downloadAndDecrypt(Type type, UUID fileKey) {
        log.info("Request to download and decrypt file: {}", fileKey);
        Resume resume = null;
        if(type.equals(Type.ORIGINAL)) {
            resume = resumeRepository.findByAwsPathOriginal(fileKey)
                    .orElseThrow(() -> new RuntimeException("Resume file not found in database"));
        } else {
            resume = resumeRepository.findByAwsPathUpdated(fileKey)
                    .orElseThrow(() -> new RuntimeException("Resume updated file not found in database"));
        }
        
        DownloadOriginalResumeDto downloadOriginalResumeDto = new DownloadOriginalResumeDto();

        MediaType contentType;
        String fileName;
        String extension;

        if ((type.equals(Type.ORIGINAL) && resume.getEncryptionkey().endsWith("-RE-TEXT")) || type.equals(Type.UPDATED)) {
            contentType = MediaType.TEXT_PLAIN;
            fileName = fileKey + ".txt";
            extension = ".txt";
        } else {
            contentType = MediaType.APPLICATION_PDF;
            fileName = fileKey + ".pdf";
            extension = ".pdf";
        }
        
        try {
            Resource resource = s3Template.download(bucketName, fileKey.toString() + extension);
            byte[] encryptedData = StreamUtils.copyToByteArray(resource.getInputStream());

            byte[] decryptedData = CryptoUtils.decrypt(encryptedData, type.equals(Type.ORIGINAL) ? resume.getEncryptionkey() : resume.getEncryptionkeyForUpdatedResume());

            downloadOriginalResumeDto.setBytes(decryptedData); 
            downloadOriginalResumeDto.setContentType(contentType);
            downloadOriginalResumeDto.setFileName(fileName);
            
            return downloadOriginalResumeDto;
        } catch (Exception e) {
            log.error("Download/Decryption failed for file: {}", fileKey, e);
            throw new RuntimeException("Failed to process resume: " + e.getMessage());
        }
    }
    
    @Override
    public ResumeDto getResumeById(UUID id) {
        ResumeDto resumeDto = resumeRepository.findById(id)
                .map(this::mapToDtoForUser)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        // UPDATED: Use dynamic prefix
        String dataKey = redisResumePrefix + "buffer:" + id;
        Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
        if (!bufferedData.isEmpty()) {
            log.info("Fetching resume {} from Redis buffer", id);
            resumeDto.setStatus(Status.valueOf((String) bufferedData.get("status")));
            resumeDto.setName((String) bufferedData.get("name"));
            resumeDto.setEmail((String) bufferedData.get("email"));
        }
        return resumeDto;
    }

    @Override
    public ResumeAdminDto getResumeByIdForAdmin(UUID id) {
        ResumeAdminDto resumeDto = resumeRepository.findById(id)
                .map(this::mapToDtoForAdmin)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        // UPDATED: Use dynamic prefix
        String dataKey = redisResumePrefix + "buffer:" + id;
        Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
        if (!bufferedData.isEmpty()) {
            log.info("Fetching resume {} from Redis buffer", id);
            resumeDto.setStatus(Status.valueOf((String) bufferedData.get("status")));
            resumeDto.setName((String) bufferedData.get("name"));
            resumeDto.setEmail((String) bufferedData.get("email"));
        }
        return resumeDto;
    }

    @Override
    public List<ResumeDto> getResumesByUserId(UUID userId) {
        List<ResumeDto> resumeDtos = resumeRepository.findByUserId(userId).stream()
                .map(this::mapToDtoForUser)
                .collect(Collectors.toList());
        for(ResumeDto resumeDto : resumeDtos) {
            // UPDATED: Use dynamic prefix
            String dataKey = redisResumePrefix + "buffer:" + resumeDto.getId();
            Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
            if (!bufferedData.isEmpty()) {
                log.info("Fetching resume {} from Redis buffer", resumeDto.getId());
                resumeDto.setStatus(Status.valueOf((String) bufferedData.get("status")));
                resumeDto.setName((String) bufferedData.get("name"));
                resumeDto.setEmail((String) bufferedData.get("email"));
            }
            resumeDto.setUpdatedURL(urlStarter+"/pdfgenerater/v1/resumes/"+resumeDto.getId()+"/{PDFTYPE}/download");
        }
        return resumeDtos;
    }
    
    @Override
    public Page<ResumeDto> getResumesByEmail(String email, Pageable pageable) {
        Page<Resume> resumePage = resumeRepository.findByEmail(email, pageable);
        return resumePage.map(resume -> {
            ResumeDto resumeDto = mapToDtoForUser(resume);
            String dataKey = redisResumePrefix + "buffer:" + resumeDto.getId();
            Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
            if (!bufferedData.isEmpty()) {
                log.info("Fetching resume {} from Redis buffer", resumeDto.getId());
                resumeDto.setStatus(Status.valueOf((String) bufferedData.get("status")));
                resumeDto.setName((String) bufferedData.get("name"));
                resumeDto.setEmail((String) bufferedData.get("email"));
            }
            resumeDto.setUpdatedURL(urlStarter + "/pdfgenerater/v1/resumes/" + resumeDto.getId() + "/{PDFTYPE}/download");
            return resumeDto;
        });
    }

    @Override
    public List<ResumeAdminDto> getResumesByUserIdForAdmin(UUID userId) {
        List<ResumeAdminDto> resumeDtos = resumeRepository.findByUserId(userId).stream()
                .map(this::mapToDtoForAdmin)
                .collect(Collectors.toList());
        for(ResumeAdminDto resumeDto : resumeDtos) {
            // UPDATED: Use dynamic prefix
            String dataKey = redisResumePrefix + "buffer:" + resumeDto.getId();
            Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
            if (!bufferedData.isEmpty()) {
                log.info("Fetching resume {} from Redis buffer", resumeDto.getId());
                resumeDto.setStatus(Status.valueOf((String) bufferedData.get("status")));
                resumeDto.setName((String) bufferedData.get("name"));
                resumeDto.setEmail((String) bufferedData.get("email"));
            }
            resumeDto.setUpdatedURL(urlStarter+"/pdfgenerater/v1/resumes/"+resumeDto.getId()+"/{PDFTYPE}/download");
        }
        return resumeDtos;
    }
    
    @Override
    @Transactional(readOnly = true)
    public ResumeContentDto getResumeContent(UUID id) {
        // UPDATED: Use dynamic prefix
        String dataKey = redisResumePrefix + "buffer:" + id;
        Map<Object, Object> bufferedData = redisTemplate.opsForHash().entries(dataKey);
        if (!bufferedData.isEmpty()) {
            log.info("Fetching resume {} from Redis buffer", id);
            ResumeContentDto dto = new ResumeContentDto();
            dto.setId(id);
            dto.setStatus(Status.valueOf((String) bufferedData.get("status")));
            dto.setName((String) bufferedData.get("name"));
            dto.setEmail((String) bufferedData.get("email"));
            dto.setContent((String) bufferedData.get("content"));
            return dto;
        }
        log.info("Resume {} not in buffer, fetching from Database", id);
        return resumeRepository.findById(id)
                .map(resume -> {
                    ResumeContentDto dto = new ResumeContentDto();
                    dto.setId(resume.getId());
                    dto.setStatus(resume.getStatus());
                    dto.setName(resume.getName());
                    dto.setEmail(resume.getEmail());
                    dto.setContentUrl(urlStarter+"/resumemanager/v1/resumes/view/updated/"+resume.getAwsPathUpdated());
                    return dto;
                })
                .orElseThrow(() -> new RuntimeException("Resume not found"));
    }
    
    @Override
    @Transactional
    public void updateStatus(UUID id, Status status, String name, String email, String content) {
        log.info("Buffering update for resume {} in Redis", id);
        // UPDATED: Use dynamic prefix
        String dataKey = redisResumePrefix + "buffer:" + id;
        String trackerKey = redisResumePrefix + "update:tracker";
        Map<String, String> data = new HashMap<>();
        data.put("id", id.toString());
        data.put("status", status.name());
        data.put("name", name != null ? name : "");
        data.put("email", email != null ? email : "");
        data.put("content", content != null ? content : "");
        
        redisTemplate.opsForHash().putAll(dataKey, data);
        redisTemplate.opsForZSet().add(trackerKey, id.toString(), System.currentTimeMillis());
        try {
            updateNameAndEmail(id, name, email);
        } catch(Exception ex) {
            log.error(ex.getMessage());
        }
    }
    
    @Override
    @Transactional
    public void processStaleUpdates() {
        // UPDATED: Use dynamic prefix
        String trackerKey = redisResumePrefix + "update:tracker";
        long fifteenMinsAgo = System.currentTimeMillis() - (1 * 60 * 1000);
        Set<String> staleIds = redisTemplate.opsForZSet().rangeByScore(trackerKey, 0, fifteenMinsAgo);
        if (staleIds == null || staleIds.isEmpty()) return;
        for (String idStr : staleIds) {
            UUID id = UUID.fromString(idStr);
            // UPDATED: Use dynamic prefix
            String dataKey = redisResumePrefix + "buffer:" + idStr;
            Map<Object, Object> data = redisTemplate.opsForHash().entries(dataKey);
            if (data.isEmpty()) continue;

            try {
                finalizeUpload(id, data);
                redisTemplate.delete(dataKey);
                redisTemplate.opsForZSet().remove(trackerKey, idStr);
            } catch (Exception e) {
                log.error("Failed to sync stale resume {} to permanent storage", id, e);
            }
        }
        log.info("Successfully uploaded {} stale resumes", staleIds.size());
    }
    
    @Override
    @Transactional
    public ResumeDto finalSubmit(UUID id)
    {
    	ResumeDto resumeDto = null;
    	Integer count = 0;
    	String trackerKey = redisResumePrefix + "update:tracker";
        String dataKey = redisResumePrefix + "buffer:" + id.toString();
        Map<Object, Object> data = redisTemplate.opsForHash().entries(dataKey);
        if (data.isEmpty())
        {
        	count = resumeRepository.updateStatus(id, Status.COMPLETED);
        }
        else
        {
	        try {
	        	Status status = Status.valueOf((String) data.get("status"));
	            String name = (String) data.get("name");
	            String email = (String) data.get("email");
	            String content = (String) data.get("content");
	
	            if (status.equals(Status.FAILED) || status.equals(Status.EXTRACTING_TEXT)) {
	            	count = resumeRepository.updateStatus(id, status);
	            } else {
	                String encKey = generateKey() + "-RE-TEXT";
	                UUID fileKey = UUID.randomUUID();
	                byte[] encryptedData = CryptoUtils.encrypt(content.getBytes(StandardCharsets.UTF_8), encKey);
	                
	                uploadToS3(fileKey.toString() + ".txt", encryptedData);
	                count = resumeRepository.updateResumeDetails(id, Status.COMPLETED, name, email, fileKey, encKey);
	            }
	            redisTemplate.delete(dataKey);
	            redisTemplate.opsForZSet().remove(trackerKey, id.toString());
	        } catch (Exception e) {
	            log.error("Failed to sync stale resume {} to permanent storage", id, e);
	        }
        }
        return getResumeById(id);
    }

    private void finalizeUpload(UUID id, Map<Object, Object> data) throws Exception {
        Status status = Status.valueOf((String) data.get("status"));
        String name = (String) data.get("name");
        String email = (String) data.get("email");
        String content = (String) data.get("content");

        if (status.equals(Status.FAILED) || status.equals(Status.EXTRACTING_TEXT)) {
            resumeRepository.updateStatus(id, status);
        } else {
            String encKey = generateKey() + "-RE-TEXT";
            UUID fileKey = UUID.randomUUID();
            byte[] encryptedData = CryptoUtils.encrypt(content.getBytes(StandardCharsets.UTF_8), encKey);
            
            uploadToS3(fileKey.toString() + ".txt", encryptedData);
            resumeRepository.updateResumeDetails(id, Status.COMPLETED, name, email, fileKey, encKey);
        }
    }

    @Override
    @Transactional
    public void updateUpdatedResume(UUID id, UUID awsPath) {
        log.info("Linking processed resume path {} to record {}", awsPath, id);
        int updated = resumeRepository.updateUpdatedResumeDetails(id, awsPath);
        if (updated == 0) throw new RuntimeException("Update failed: Resume not found");
    }

    private void uploadToS3(String s3Key, byte[] data) throws Exception {
        try (InputStream is = new ByteArrayInputStream(data)) {
            s3Template.upload(bucketName, s3Key, is);
        }
    }
    
    @Override
    public void updateStatusInBatch() {
        while(true) {
            try {
                List<Message<StatusResume>> messages = messageBrokerService.getMessage("statusupdater");
                if(messages.isEmpty()) {
                    break;
                }
                
                for(Message<StatusResume> message : messages) {
                    updateStatus(message.getMessage().getResumeId(), message.getMessage().getStatus(), message.getMessage().getName(), message.getMessage().getEmail(), message.getMessage().getUpdatedContent());
                        
                }
            } catch (Exception ex) {
                log.error("Error in update status loop", ex);
                break;
            }
        }
    }
    
    @Async
    public void updateNameAndEmail(UUID id, String name, String email) {
        ResumeAdminDto resumeAdminDto = getResumeByIdForAdmin(id);
        tokenService.updateNameAndEmail(resumeAdminDto.getUserId(), name, email);
    }


    private String generateKey() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private ResumeDto mapToDtoForUser(Resume resume) {
        ResumeDto dto = new ResumeDto();
        dto.setId(resume.getId());
        dto.setStatus(resume.getStatus());
        dto.setCreateTime(resume.getCreateTime());
        dto.setOriginalURL(urlStarter+"/resumemanager/v1/resumes/view/original/"+resume.getAwsPathOriginal());
        if(resume.getStatus().equals(Status.COMPLETED)) {
            dto.setUpdatedURL(urlStarter+"/resumemanager/v1/resumes/view/updated/"+resume.getAwsPathUpdated());
            dto.setName(resume.getName());
            dto.setEmail(resume.getEmail());
        }
        return dto;
    }

    private ResumeAdminDto mapToDtoForAdmin(Resume resume) {
        ResumeAdminDto dto = new ResumeAdminDto();
        dto.setId(resume.getId());
        dto.setUserId(resume.getUserId());
        dto.setIsOriginalResumeUploaded(resume.getIsOriginalResumeUploaded());
        dto.setAwsPathOriginal(resume.getAwsPathOriginal());
        dto.setIsUpdatedResumeUploaded(resume.getIsUpdatedResumeUploaded());
        dto.setAwsPathUpdated(resume.getAwsPathUpdated());
        dto.setStatus(resume.getStatus());
        dto.setEncryptionkey(resume.getEncryptionkey());
        dto.setName(resume.getName());
        dto.setEmail(resume.getEmail());
        dto.setEncryptionkeyForUpdatedResume(resume.getEncryptionkeyForUpdatedResume());
        dto.setOriginalURL(urlStarter+"/resumemanager/v1/resumes/view/original/"+resume.getAwsPathOriginal());
        dto.setUpdatedURL(urlStarter+"/resumemanager/v1/resumes/view/updated/"+resume.getAwsPathUpdated());
        dto.setCreateTime(resume.getCreateTime());
        return dto;
    }
}