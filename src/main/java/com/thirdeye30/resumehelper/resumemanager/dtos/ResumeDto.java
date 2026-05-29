package com.thirdeye30.resumehelper.resumemanager.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import com.thirdeye30.resumehelper.resumemanager.enums.Status;

import lombok.Data;

@Data
public class ResumeDto {
	private UUID id;
    private UUID userId;
    private Status status;
    private String name;
    private String email;
    private String originalURL;
    private String updatedURL;
    private LocalDateTime createTime;
}
