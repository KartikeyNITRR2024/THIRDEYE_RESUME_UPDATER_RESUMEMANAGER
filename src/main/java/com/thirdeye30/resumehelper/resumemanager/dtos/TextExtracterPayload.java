package com.thirdeye30.resumehelper.resumemanager.dtos;
import java.util.UUID;

import lombok.Data;
@Data
public class TextExtracterPayload {
	private UUID resumeId;
	private UUID awsPath;
	private String encryptionkey;
	private ResumeMetadata resumeMetadata;
}
