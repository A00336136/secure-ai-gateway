package com.secureai.controller;

import com.secureai.model.AskRequest;
import com.secureai.model.AskResponse;
import com.secureai.pii.PiiRedactionService;
import com.secureai.service.OllamaClient;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AskController {

    @Autowired
    private OllamaClient ollamaClient;

    @Autowired
    private PiiRedactionService piiRedactionService;

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        String llmResponse = ollamaClient.generateResponse(request.getPrompt());

        boolean hasPii = piiRedactionService.containsPii(llmResponse);
        String finalResponse = hasPii ? piiRedactionService.redact(llmResponse) : llmResponse;

        AskResponse response = new AskResponse(finalResponse, hasPii);
        return ResponseEntity.ok(response);
    }
}
