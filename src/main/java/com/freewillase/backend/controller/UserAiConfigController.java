package com.freewillase.backend.controller;

import com.freewillase.backend.dto.UserAiConfigSaveRequest;
import com.freewillase.backend.dto.UserAiConfigStatusResponse;
import com.freewillase.backend.service.UserAiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-ai-config")
@RequiredArgsConstructor
public class UserAiConfigController {

    private final UserAiConfigService userAiConfigService;

    @GetMapping("/status")
    public UserAiConfigStatusResponse getStatus(Authentication authentication) {
        return userAiConfigService.getStatus(authentication.getName());
    }

    @PutMapping
    public UserAiConfigStatusResponse save(Authentication authentication, @RequestBody UserAiConfigSaveRequest request) {
        return userAiConfigService.save(authentication.getName(), request);
    }
}
