package com.thirdeye30.resumehelper.resumemanager.dtos;

import java.util.UUID;

import lombok.Data;

@Data
public class AlProcesserPayload {
	private UUID resumeId;
	private ResumeMetadata resumeMetadata;
	private String content;
}
