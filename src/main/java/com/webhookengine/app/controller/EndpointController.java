package com.webhookengine.app.controller;

import com.webhookengine.app.dto.EndpointRequest;
import com.webhookengine.app.dto.EndpointResponse;
import com.webhookengine.app.service.EndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks/endpoints")
@RequiredArgsConstructor
public class EndpointController {
    private final EndpointService endpointService;

    @PostMapping
    public ResponseEntity<EndpointResponse> register(@Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<EndpointResponse>> list() {
        return ResponseEntity.ok(endpointService.listEndpoints());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        endpointService.deleteEndpoint(id);
        return ResponseEntity.noContent().build();
    }
}
