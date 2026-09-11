package dev.splitedge.importstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dev.splitedge.report.ImportRunSummary;

@WebMvcTest(DataStatusController.class)
class DataStatusControllerTest {

    private static final String PATH = "/api/data/status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataStatusService service;

    @Test
    void readyStatusReturnsFullShapeWithRealValues() throws Exception {
        ImportRunSummary run = new ImportRunSummary(4, Instant.parse("2025-03-02T08:15:30Z"), 82747, 0);
        given(service.currentStatus())
                .willReturn(new DataStatus(
                        true,
                        DataStatusLevel.READY,
                        run,
                        List.of("2023-24", "2024-25", "2025-26"),
                        new DataStatusCounts(3690, 79057, 801, 525, 30)));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataImported").value(true))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.latestGamesStatsImport.runId").value(4))
                .andExpect(jsonPath("$.latestGamesStatsImport.completedAt").value("2025-03-02T08:15:30Z"))
                .andExpect(jsonPath("$.latestGamesStatsImport.recordsProcessed").value(82747))
                .andExpect(jsonPath("$.latestGamesStatsImport.recordsFailed").value(0))
                .andExpect(jsonPath("$.seasons.length()").value(3))
                .andExpect(jsonPath("$.seasons[0]").value("2023-24"))
                .andExpect(jsonPath("$.counts.games").value(3690))
                .andExpect(jsonPath("$.counts.playerGameStats").value(79057))
                .andExpect(jsonPath("$.counts.players").value(801))
                .andExpect(jsonPath("$.counts.activePlayers").value(525))
                .andExpect(jsonPath("$.counts.teams").value(30));
    }

    @Test
    void emptyStatusWithNoImportOmitsLatestGamesStatsImport() throws Exception {
        given(service.currentStatus())
                .willReturn(new DataStatus(
                        false, DataStatusLevel.EMPTY, null, List.of(), new DataStatusCounts(0, 0, 0, 0, 0)));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataImported").value(false))
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.latestGamesStatsImport").doesNotExist())
                .andExpect(jsonPath("$.seasons.length()").value(0));
    }

    @Test
    void emptyStatusWithPartialDataStillReportsRealCounts() throws Exception {
        given(service.currentStatus())
                .willReturn(new DataStatus(
                        false,
                        DataStatusLevel.EMPTY,
                        null,
                        List.of("2024-25"),
                        new DataStatusCounts(5, 0, 12, 9, 3)));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataImported").value(false))
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.counts.games").value(5))
                .andExpect(jsonPath("$.counts.playerGameStats").value(0))
                .andExpect(jsonPath("$.counts.players").value(12));
    }

    @Test
    void oldHardcodedFoundationResponseShapeIsGone() throws Exception {
        given(service.currentStatus())
                .willReturn(new DataStatus(
                        false, DataStatusLevel.EMPTY, null, List.of(), new DataStatusCounts(0, 0, 0, 0, 0)));

        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("foundation-ready", "checkedAt");
    }

    @Test
    void unexpectedServiceFailureReturnsSafeInternalError() throws Exception {
        willThrow(new RuntimeException("jdbc:postgresql://internal-host/secret"))
                .given(service)
                .currentStatus();

        MvcResult result = mockMvc.perform(get(PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc:", "secret", "RuntimeException", "internal-host");
    }
}
