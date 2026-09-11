package dev.splitedge.report.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.splitedge.report.TeamIdentityRepository;

/** Serves stored team identity only. Carries no defensive statistics. */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamIdentityRepository teamIdentities;

    public TeamController(TeamIdentityRepository teamIdentities) {
        this.teamIdentities = teamIdentities;
    }

    @GetMapping
    public List<TeamResponse> teams() {
        return teamIdentities.findAllOrderedByAbbreviation().stream()
                .map(TeamResponseMapper::toResponse)
                .toList();
    }
}
