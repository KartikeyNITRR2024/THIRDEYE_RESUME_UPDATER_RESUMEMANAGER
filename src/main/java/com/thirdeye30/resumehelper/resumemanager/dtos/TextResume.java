package com.thirdeye30.resumehelper.resumemanager.dtos;
import lombok.Data;

@Data
public class TextResume {
	private ResumeMetadata metadata;
    private String content;
}
