package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dev.splitedge.report.PlayerIdentityRepository;
import dev.splitedge.report.PlayerProfile;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerIdentityRepository playerIdentities;

    @Test
    void returnsTheStoredIdentityForAValidActivePlayer() throws Exception {
        given(playerIdentities.findProfileByNbaPlayerId(201939))
                .willReturn(Optional.of(
                        new PlayerProfile(201939, "Stephen", "Curry", "Stephen Curry", 1610612744L, true)));

        mockMvc.perform(get("/api/players/201939"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbaPlayerId").value(201939))
                .andExpect(jsonPath("$.firstName").value("Stephen"))
                .andExpect(jsonPath("$.lastName").value("Curry"))
                .andExpect(jsonPath("$.fullName").value("Stephen Curry"))
                .andExpect(jsonPath("$.nbaTeamId").value(1610612744L))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void inactiveHistoricalPlayerRemainsQueryableWithActiveFalse() throws Exception {
        given(playerIdentities.findProfileByNbaPlayerId(999))
                .willReturn(Optional.of(new PlayerProfile(999, "Old", "Timer", "Old Timer", null, false)));

        mockMvc.perform(get("/api/players/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.nbaTeamId").doesNotExist());
    }

    @Test
    void nullableCurrentTeamStaysNullWhenAbsent() throws Exception {
        given(playerIdentities.findProfileByNbaPlayerId(500))
                .willReturn(Optional.of(new PlayerProfile(500, "Free", "Agent", "Free Agent", null, true)));

        mockMvc.perform(get("/api/players/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbaTeamId").doesNotExist())
                .andExpect(jsonPath("$.fullName").value("Free Agent"));
    }

    @Test
    void activePlayersUsesTheDefaultLimitWhenOmitted() throws Exception {
        given(playerIdentities.findActive(600))
                .willReturn(List.of(new PlayerProfile(201939, "Stephen", "Curry", "Stephen Curry", 1610612744L, true)));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nbaPlayerId").value(201939))
                .andExpect(jsonPath("$[0].fullName").value("Stephen Curry"))
                .andExpect(jsonPath("$[0].active").value(true));
        verify(playerIdentities).findActive(600);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void activePlayersPassesAnExplicitValidLimitThrough(int limit) throws Exception {
        given(playerIdentities.findActive(limit)).willReturn(List.of());

        mockMvc.perform(get("/api/players").param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(playerIdentities).findActive(limit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1001", "12.5", "abc", "1e3"})
    void activePlayersRejectsAnInvalidLimitWithoutTouchingTheRepository(String rawLimit) throws Exception {
        mockMvc.perform(get("/api/players").param("limit", rawLimit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
        verifyNoInteractions(playerIdentities);
    }

    @Test
    void activePlayersOmitsNbaTeamIdForAnActiveFreeAgent() throws Exception {
        given(playerIdentities.findActive(600))
                .willReturn(List.of(new PlayerProfile(500, "Free", "Agent", "Free Agent", null, true)));

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].nbaTeamId").doesNotExist());
    }

    @Test
    void activePlayersReturnsEmptyArrayWhenNoActivePlayersAreStored() throws Exception {
        given(playerIdentities.findActive(600)).willReturn(List.of());

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void activePlayersUnexpectedRepositoryFailureReturnsSafeInternalError() throws Exception {
        willThrow(new RuntimeException("jdbc:postgresql://internal-host/secret"))
                .given(playerIdentities)
                .findActive(anyInt());

        MvcResult result = mockMvc.perform(get("/api/players"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc:", "secret", "RuntimeException", "internal-host");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "abc", "12.5", "1e3"})
    void invalidPlayerIdIsRejectedWithoutTouchingTheRepository(String rawValue) throws Exception {
        mockMvc.perform(get("/api/players/{id}", rawValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAYER_ID"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nbaPlayerId"));
        verifyNoInteractions(playerIdentities);
    }

    @Test
    void unknownPositivePlayerIdReturns404() throws Exception {
        given(playerIdentities.findProfileByNbaPlayerId(123456789)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/players/123456789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PLAYER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nbaPlayerId"));
    }

    @Test
    void unexpectedRepositoryFailureReturnsSafeInternalError() throws Exception {
        willThrow(new RuntimeException("jdbc:postgresql://internal-host/secret"))
                .given(playerIdentities)
                .findProfileByNbaPlayerId(anyLong());

        MvcResult result = mockMvc.perform(get("/api/players/201939"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc:", "secret", "RuntimeException", "internal-host");
    }
}
