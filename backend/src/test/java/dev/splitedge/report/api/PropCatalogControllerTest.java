package dev.splitedge.report.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropCatalogController.class)
class PropCatalogControllerTest {

    private static final String PATH = "/api/props";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsExactlyEightPropsInOrderWithComponentStats() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].code").value("POINTS"))
                .andExpect(jsonPath("$[0].displayName").value("Points"))
                .andExpect(jsonPath("$[0].componentStats.length()").value(1))
                .andExpect(jsonPath("$[0].componentStats[0]").value("POINTS"))
                .andExpect(jsonPath("$[1].code").value("REBOUNDS"))
                .andExpect(jsonPath("$[2].code").value("ASSISTS"))
                .andExpect(jsonPath("$[3].code").value("THREE_POINTERS_MADE"))
                .andExpect(jsonPath("$[4].code").value("PR"))
                .andExpect(jsonPath("$[4].componentStats.length()").value(2))
                .andExpect(jsonPath("$[4].componentStats[0]").value("POINTS"))
                .andExpect(jsonPath("$[4].componentStats[1]").value("REBOUNDS"))
                .andExpect(jsonPath("$[5].code").value("PA"))
                .andExpect(jsonPath("$[6].code").value("RA"))
                .andExpect(jsonPath("$[7].code").value("PRA"))
                .andExpect(jsonPath("$[7].componentStats.length()").value(3))
                .andExpect(jsonPath("$[7].componentStats[0]").value("POINTS"))
                .andExpect(jsonPath("$[7].componentStats[1]").value("REBOUNDS"))
                .andExpect(jsonPath("$[7].componentStats[2]").value("ASSISTS"));
    }

    @Test
    void repeatedRequestsReturnTheSameDeterministicCatalog() throws Exception {
        String first = mockMvc.perform(get(PATH)).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get(PATH)).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second);
    }
}
