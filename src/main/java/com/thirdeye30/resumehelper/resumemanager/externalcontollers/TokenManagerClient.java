package com.thirdeye30.resumehelper.resumemanager.externalcontollers;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "TOKENMANAGER")
public interface TokenManagerClient {
	
    @PutMapping("/tokenmanager/v1/users/{id}/token/subtract/{amount}")
    ResponseEntity<Void> subtractToken(
        @PathVariable("id") UUID id,
        @PathVariable("amount") Long amount
    );
	
    @GetMapping("/tokenmanager/v1/users/{id}/token")
    ResponseEntity<Long> getToken(
        @PathVariable("id") UUID id
    );
}