package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dev.splitedge.report.InconsistentPlayerAppearanceException;
import dev.splitedge.report.MatchupReportCommand;
import dev.splitedge.report.MatchupReportService;
import dev.splitedge.shared.api.ApiErrorCode;
import dev.splitedge.shared.api.ApiException;

@WebMvcTest(MatchupReportController.class)
class MatchupReportControllerTest {

    private static final String PATH = "/api/reports/matchup";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchupReportService service;

    @Test
    void returnsTheMappedReportForAValidRequest() throws Exception {
        given(service.generate(any())).willReturn(MatchupReportFixtures.oneThirdReport());
        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                 "line":24.5,"direction":"OVER","season":"2024-25",
                                 "location":"ALL","recency":"ALL","minMinutes":0}""")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria.player.nbaPlayerId").value(201939))
                .andExpect(jsonPath("$.criteria.player.fullName").value("Test Player"))
                .andExpect(jsonPath("$.criteria.opponent.abbreviation").value("BOS"))
                .andExpect(jsonPath("$.criteria.prop").value("POINTS"))
                .andExpect(jsonPath("$.criteria.line").value(24.5))
                .andExpect(jsonPath("$.criteria.direction").value("OVER"))
                .andExpect(jsonPath("$.criteria.seasonsApplied[0]").value("2024-25"))
                .andExpect(jsonPath("$.criteria.location").value("ALL"))
                .andExpect(jsonPath("$.criteria.recency").value("ALL"))
                .andExpect(jsonPath("$.criteria.minMinutes").value(0))
                .andExpect(jsonPath("$.matchup.qualifyingGames").value(3))
                .andExpect(jsonPath("$.matchup.hits").value(1))
                .andExpect(jsonPath("$.matchup.misses").value(2))
                .andExpect(jsonPath("$.matchup.pushes").value(0))
                .andExpect(jsonPath("$.matchup.hitRate").value(0.333))
                .andExpect(jsonPath("$.matchup.average").value(23.3))
                .andExpect(jsonPath("$.matchup.median").value(20.0))
                .andExpect(jsonPath("$.baseline.qualifyingGames").value(4))
                .andExpect(jsonPath("$.baseline.hits").value(2))
                .andExpect(jsonPath("$.baseline.misses").value(2))
                .andExpect(jsonPath("$.baseline.hitRate").value(0.500))
                .andExpect(jsonPath("$.baseline.average").value(24.5))
                .andExpect(jsonPath("$.baseline.median").value(24.0))
                .andExpect(jsonPath("$.comparison.hitRateDifferencePoints").value(-16.7))
                .andExpect(jsonPath("$.sampleQuality").value("LOW"))
                .andExpect(jsonPath("$.dataFreshness.importType").value("GAMES_STATS"))
                .andExpect(jsonPath("$.dataFreshness.completedAt").value("2025-03-02T08:15:30Z"))
                .andExpect(jsonPath("$.opponentContext.available").value(false))
                .andExpect(jsonPath("$.opponentContext.reason").value("TEAM_DEFENSE_NOT_IMPORTED"))
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.games[0].nbaGameId").value("0022400001"))
                .andExpect(jsonPath("$.games[0].gameDate").value("2024-10-22"))
                .andExpect(jsonPath("$.games[0].result").value("HIT"))
                .andExpect(jsonPath("$.games[0].propValue").value(30))
                .andExpect(jsonPath("$.games[2].nbaGameId").value("0022400003"))
                .andExpect(jsonPath("$.chart[0].nbaGameId").value("0022400001"))
                .andExpect(jsonPath("$.chart[2].nbaGameId").value("0022400003"))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain("homeScore").doesNotContain("awayScore").doesNotContain("%");
    }

    @Test
    void nullRatesAreEmittedAsJsonNull() throws Exception {
        given(service.generate(any())).willReturn(MatchupReportFixtures.emptyReport(null));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchup.hitRate").doesNotExist())
                .andExpect(jsonPath("$.matchup.average").doesNotExist())
                .andExpect(jsonPath("$.matchup.median").doesNotExist())
                .andExpect(jsonPath("$.comparison.hitRateDifferencePoints").doesNotExist())
                .andExpect(jsonPath("$.sampleQuality").doesNotExist())
                .andExpect(jsonPath("$.dataFreshness.completedAt").doesNotExist())
                .andExpect(jsonPath("$.warnings[0]").value("NO_QUALIFYING_GAMES"))
                .andExpect(jsonPath("$.warnings[1]").value("NO_SUCCESSFUL_GAMES_STATS_IMPORT"));
    }

    @Test
    void unknownPropertyIsRejected() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                 "line":24.5,"direction":"OVER","surprise":true}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PROPERTY"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("surprise"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void numericStringsBooleansArraysAndObjectsAreRejectedForLine() throws Exception {
        for (String invalid : List.of("\"24.5\"", "true", "[24.5]", "{\"value\":24.5}")) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("""
                                    {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                     "line":%s,"direction":"OVER"}""".formatted(invalid))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LINE"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("line"));
        }
    }

    @Test
    void minMinutesRejectsNonNumericJsonTokensThroughRealJacksonBinding() throws Exception {
        for (String invalid : List.of("\"20\"", "true", "[20]", "{\"value\":20}")) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("""
                                    {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                     "line":24.5,"direction":"OVER","minMinutes":%s}""".formatted(invalid))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_MIN_MINUTES"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("minMinutes"));
        }
    }

    @Test
    void minMinutesRejectsNegativeOutOfRangeAndExcessivePrecisionValues() throws Exception {
        for (String invalid : List.of("-0.001", "80.001", "1000", "20.0001")) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("""
                                    {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                     "line":24.5,"direction":"OVER","minMinutes":%s}""".formatted(invalid))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_MIN_MINUTES"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("minMinutes"));
        }
    }

    @Test
    void minMinutesExplicitNullBehavesLikeOmissionAndDefaultsToZero() throws Exception {
        given(service.generate(any())).willReturn(MatchupReportFixtures.oneThirdReport());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                 "line":24.5,"direction":"OVER","minMinutes":null}""")))
                .andExpect(status().isOk());
        ArgumentCaptor<MatchupReportCommand> captor = ArgumentCaptor.forClass(MatchupReportCommand.class);
        verify(service).generate(captor.capture());
        assertThat(captor.getValue().minMinutes().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void minMinutesOmittedDefaultsToZero() throws Exception {
        given(service.generate(any())).willReturn(MatchupReportFixtures.oneThirdReport());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
        ArgumentCaptor<MatchupReportCommand> captor = ArgumentCaptor.forClass(MatchupReportCommand.class);
        verify(service).generate(captor.capture());
        assertThat(captor.getValue().minMinutes().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void minMinutesValidNumericBoundariesSucceed() throws Exception {
        given(service.generate(any())).willReturn(MatchupReportFixtures.oneThirdReport());
        for (String boundary : List.of("0", "80", "80.000", "24.5")) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("""
                                    {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                     "line":24.5,"direction":"OVER","minMinutes":%s}""".formatted(boundary))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void nonFiniteNumbersAreRejected() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                 "line":NaN,"direction":"OVER"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void outOfRangeAndExcessivePrecisionLinesAreRejected() throws Exception {
        for (String invalid : List.of("-0.001", "999.001", "1000", "24.5000")) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("""
                                    {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                     "line":%s,"direction":"OVER"}""".formatted(invalid))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_LINE"));
        }
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"line\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.message").value("request body is not valid JSON"));
    }

    @Test
    void missingAndNullRequiredFieldsFailValidation() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                                 "line":null,"direction":"OVER"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("line"));
    }

    @Test
    void nonPositiveIdentifiersFailValidation() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":0,"nbaOpponentTeamId":-1,"prop":"POINTS",
                                 "line":24.5,"direction":"OVER"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void invalidEnumsReturnTheirOwnCodes() throws Exception {
        assertEnumFailure("\"prop\":\"STEALS\",\"direction\":\"OVER\"", "UNSUPPORTED_PROP");
        assertEnumFailure("\"prop\":\"POINTS\",\"direction\":\"over\"", "INVALID_DIRECTION");
        assertEnumFailure(
                "\"prop\":\"POINTS\",\"direction\":\"OVER\",\"location\":\"NEUTRAL\"", "INVALID_LOCATION");
        assertEnumFailure(
                "\"prop\":\"POINTS\",\"direction\":\"OVER\",\"recency\":\"LAST_2\"", "INVALID_RECENCY");
        assertEnumFailure(
                "\"prop\":\"POINTS\",\"direction\":\"OVER\",\"season\":\"2024\"", "INVALID_SEASON");
    }

    @Test
    void unknownIdentitiesMapToNotFound() throws Exception {
        willThrow(ApiException.field(ApiErrorCode.UNKNOWN_PLAYER, "nbaPlayerId", "no stored player"))
                .given(service)
                .generate(any());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PLAYER"));

        willThrow(ApiException.field(ApiErrorCode.UNKNOWN_OPPONENT, "nbaOpponentTeamId", "no stored team"))
                .given(service)
                .generate(any());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_OPPONENT"));
    }

    @Test
    void inconsistentStoredDataReturnsASafeServerError() throws Exception {
        willThrow(new InconsistentPlayerAppearanceException("0022400001", 3L, 1L, 2L))
                .given(service)
                .generate(any());
        MvcResult result = mockMvc.perform(
                        post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INVARIANT_VIOLATION"))
                .andExpect(jsonPath("$.message").value("stored player appearance data is inconsistent"))
                .andReturn();
        assertSafeBody(result);
    }

    @Test
    void unsupportedHttpMethodReturns405WithStableBody() throws Exception {
        MvcResult result = mockMvc.perform(get(PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();
        assertSafeBody(result);
    }

    @Test
    void unsupportedContentTypeReturns415WithStableBody() throws Exception {
        MvcResult result = mockMvc.perform(
                        post(PATH).contentType(MediaType.TEXT_PLAIN).content(validBody()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();
        assertSafeBody(result);
    }

    @Test
    void unexpectedFailuresReturnASafeServerError() throws Exception {
        willThrow(new IllegalStateException("SELECT * FROM players -- jdbc:postgresql://localhost:5432/splitedge"))
                .given(service)
                .generate(any());
        MvcResult result = mockMvc.perform(
                        post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("unexpected internal error"))
                .andReturn();
        assertSafeBody(result);
    }

    private void assertEnumFailure(String fields, String expectedCode) throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"line":24.5,%s}"""
                                .formatted(fields))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private static void assertSafeBody(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        assertThat(json)
                .doesNotContain("SELECT")
                .doesNotContain("jdbc:")
                .doesNotContain("Exception")
                .doesNotContain("at dev.splitedge");
    }

    private static String validBody() {
        return body("""
                {"nbaPlayerId":201939,"nbaOpponentTeamId":1610612738,"prop":"POINTS",
                 "line":24.5,"direction":"OVER"}""");
    }

    private static String body(String json) {
        return json.replace("\n", "");
    }
}
