package dev.splitedge.report.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.splitedge.report.PlayerIdentityRepository;
import dev.splitedge.report.PlayerProfile;
import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

/**
 * Serves stored player identity only. Never fetches game history and never calls an
 * external NBA source.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private static final int DEFAULT_ACTIVE_LIMIT = 600;
    private static final int MIN_ACTIVE_LIMIT = 1;
    private static final int MAX_ACTIVE_LIMIT = 1000;

    private final PlayerIdentityRepository playerIdentities;

    public PlayerController(PlayerIdentityRepository playerIdentities) {
        this.playerIdentities = playerIdentities;
    }

    /**
     * Active-only player discovery for the frontend picker. Historical/inactive
     * players remain reachable only through {@link #player(String)}.
     */
    @GetMapping
    public List<PlayerResponse> activePlayers(@RequestParam(name = "limit", required = false) String limit) {
        int parsedLimit = parseLimit(limit);
        return playerIdentities.findActive(parsedLimit).stream()
                .map(PlayerResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/{nbaPlayerId}")
    public PlayerResponse player(@PathVariable String nbaPlayerId) {
        long id = parsePositivePlayerId(nbaPlayerId);
        PlayerProfile profile = playerIdentities
                .findProfileByNbaPlayerId(id)
                .orElseThrow(() -> ApiException.field(
                        ApiErrorCode.UNKNOWN_PLAYER, "nbaPlayerId", "no stored player has that NBA player ID"));
        return PlayerResponseMapper.toResponse(profile);
    }

    private static long parsePositivePlayerId(String rawValue) {
        long value;
        try {
            value = Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            throw invalidPlayerId();
        }
        if (value <= 0) {
            throw invalidPlayerId();
        }
        return value;
    }

    private static ApiException invalidPlayerId() {
        return ApiException.field(
                ApiErrorCode.INVALID_PLAYER_ID,
                "nbaPlayerId",
                "nbaPlayerId must be a positive integer NBA player ID");
    }

    private static int parseLimit(String rawValue) {
        if (rawValue == null) {
            return DEFAULT_ACTIVE_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(rawValue);
        } catch (NumberFormatException ex) {
            throw invalidLimit();
        }
        if (value < MIN_ACTIVE_LIMIT || value > MAX_ACTIVE_LIMIT) {
            throw invalidLimit();
        }
        return value;
    }

    private static ApiException invalidLimit() {
        return ApiException.field(
                ApiErrorCode.VALIDATION_FAILED,
                "limit",
                "limit must be an integer from 1 through 1000");
    }
}
