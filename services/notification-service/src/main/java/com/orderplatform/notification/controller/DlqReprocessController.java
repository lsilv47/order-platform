package com.orderplatform.notification.controller;

import com.orderplatform.notification.service.DlqReprocessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class DlqReprocessController {

    private final DlqReprocessService dlqReprocessService;

    public DlqReprocessController(DlqReprocessService dlqReprocessService) {
        this.dlqReprocessService = dlqReprocessService;
    }

    @PostMapping("/dlq/reprocess")
    public ResponseEntity<Map<String, Integer>> reprocess() {
        int count = dlqReprocessService.reprocess();
        return ResponseEntity.ok(Map.of("reprocessed", count));
    }
}
