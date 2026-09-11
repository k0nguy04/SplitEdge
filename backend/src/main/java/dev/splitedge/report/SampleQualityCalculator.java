package dev.splitedge.report;

public final class SampleQualityCalculator {

    public SampleQuality fromQualifyingGames(int qualifyingGames) {
        if (qualifyingGames <= 0) {
            return null;
        }
        if (qualifyingGames <= 4) {
            return SampleQuality.LOW;
        }
        if (qualifyingGames <= 9) {
            return SampleQuality.MODERATE;
        }
        return SampleQuality.HIGH;
    }
}
