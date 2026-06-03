package com.thirdeye30.resumehelper.resumemanager.externalcontollers;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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
    
    @PutMapping("/tokenmanager/v1/users/nameandemail/{id}/{name}/{email}")
    ResponseEntity<Object> updateNameAndEmail(
        @PathVariable("id") UUID id,
        @PathVariable("name") String name,
        @PathVariable("email") String email
    );
}