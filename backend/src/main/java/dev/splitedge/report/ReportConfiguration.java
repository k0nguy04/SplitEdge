package dev.splitedge.report;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportConfiguration {

    @Bean
    public MatchupCalculator matchupCalculator() {
        return new MatchupCalculator();
    }
}
