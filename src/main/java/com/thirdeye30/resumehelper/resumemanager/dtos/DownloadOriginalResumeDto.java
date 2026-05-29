package com.thirdeye30.resumehelper.resumemanager.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.MediaType;

import com.thirdeye30.resumehelper.resumemanager.enums.Status;

import lombok.Data;

@Data
public class DownloadOriginalResumeDto {

	byte[] bytes;
    MediaType contentType;
    String fileName;
}
