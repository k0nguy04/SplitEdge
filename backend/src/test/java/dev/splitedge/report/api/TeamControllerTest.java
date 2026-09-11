package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import dev.splitedge.report.TeamIdentityRepository;
import dev.splitedge.report.TeamProfile;

@WebMvcTest(TeamController.class)
class TeamControllerTest {

    private static final String PATH = "/api/teams";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamIdentityRepository teamIdentities;

    @Test
    void returnsExactMappedFieldsInRepositoryOrder() throws Exception {
        given(teamIdentities.findAllOrderedByAbbreviation())
                .willReturn(List.of(
                        new TeamProfile(1610612744L, "GSW", "Golden State", "Warriors", "Golden State Warriors"),
                        new TeamProfile(1610612738L, "BOS", "Boston", "Celtics", "Boston Celtics")));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nbaTeamId").value(1610612744L))
                .andExpect(jsonPath("$[0].abbreviation").value("GSW"))
                .andExpect(jsonPath("$[0].city").value("Golden State"))
                .andExpect(jsonPath("$[0].nickname").value("Warriors"))
                .andExpect(jsonPath("$[0].fullName").value("Golden State Warriors"))
                .andExpect(jsonPath("$[1].abbreviation").value("BOS"));
    }

    @Test
    void preservesTheRepositoriesDeterministicOrdering() throws Exception {
        given(teamIdentities.findAllOrderedByAbbreviation())
                .willReturn(List.of(
                        new TeamProfile(1L, "AAA", "City A", "Nick A", "Full A"),
                        new TeamProfile(2L, "BBB", "City B", "Nick B", "Full B"),
                        new TeamProfile(3L, "CCC", "City C", "Nick C", "Full C")));

        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body.indexOf("AAA")).isLessThan(body.indexOf("BBB"));
        assertThat(body.indexOf("BBB")).isLessThan(body.indexOf("CCC"));
    }

    @Test
    void emptyTableReturns200WithEmptyList() throws Exception {
        given(teamIdentities.findAllOrderedByAbbreviation()).willReturn(List.of());

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unexpectedRepositoryFailureReturnsSafeInternalError() throws Exception {
        willThrow(new RuntimeException("jdbc:postgresql://internal-host/secret"))
                .given(teamIdentities)
                .findAllOrderedByAbbreviation();

        MvcResult result = mockMvc.perform(get(PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc:", "secret", "RuntimeException", "internal-host");
    }
}
