package dev.splitedge.importstatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DataStatusController {

    private final DataStatusService service;

    public DataStatusController(DataStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public DataStatusResponse status() {
        return DataStatusResponseMapper.toResponse(service.currentStatus());
    }
}
