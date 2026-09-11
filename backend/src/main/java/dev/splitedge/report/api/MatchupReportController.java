package dev.splitedge.report.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.splitedge.report.MatchupReportService;

@RestController
@RequestMapping("/api/reports")
public class MatchupReportController {

    private final MatchupReportService service;

    public MatchupReportController(MatchupReportService service) {
        this.service = service;
    }

    @PostMapping("/matchup")
    public MatchupReportResponse matchup(@Valid @RequestBody MatchupReportRequest request) {
        return MatchupReportResponseMapper.toResponse(
                service.generate(MatchupRequestMapper.toCommand(request)));
    }
}
