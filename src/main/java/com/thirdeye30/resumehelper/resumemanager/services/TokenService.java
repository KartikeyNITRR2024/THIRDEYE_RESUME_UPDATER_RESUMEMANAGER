package com.thirdeye30.resumehelper.resumemanager.services;

import java.util.UUID;

public interface TokenService {
	
	Long getToken(UUID userid);
	void subtractToken(UUID userid, Long token);
	void updateNameAndEmail(UUID userId, String name, String email);

}
