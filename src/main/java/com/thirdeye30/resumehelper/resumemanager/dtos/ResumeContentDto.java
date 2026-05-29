package com.thirdeye30.resumehelper.resumemanager.dtos;
import java.util.UUID;

import com.thirdeye30.resumehelper.resumemanager.enums.Status;

import lombok.Data;

@Data
public class ResumeContentDto {
	private UUID id;
    private Status status;
    private String name;
    private String email;
    private String content;
    private String contentUrl;
}
