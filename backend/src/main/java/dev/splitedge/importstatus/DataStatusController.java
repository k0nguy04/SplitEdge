package dev.splitedge.importstatus;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DataStatusController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "foundation-ready",
                "dataImported", false,
                "checkedAt", Instant.now().toString());
    }
}
