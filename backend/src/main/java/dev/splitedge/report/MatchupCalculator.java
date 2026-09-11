package dev.splitedge.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MatchupCalculator {

    private static final long PERCENTAGE_POINTS = 100L;

    private static final Comparator<PlayerGameLine> RECENCY_ORDER = Comparator
            .comparing(PlayerGameLine::gameDate)
            .reversed()
            .thenComparing(PlayerGameLine::nbaGameId, Comparator.reverseOrder());

    private static final Comparator<ClassifiedGame> CHRONOLOGICAL_ORDER = Comparator
            .comparing((ClassifiedGame classified) -> classified.game().gameDate())
            .thenComparing(classified -> classified.game().nbaGameId());

    private final PropValueCalculator propValues;
    private final ResultClassifier classifier;
    private final SampleQualityCalculator sampleQuality;

    public MatchupCalculator() {
        this(new PropValueCalculator(), new ResultClassifier(), new SampleQualityCalculator());
    }

    public MatchupCalculator(
            PropValueCalculator propValues,
            ResultClassifier classifier,
            SampleQualityCalculator sampleQuality) {
        this.propValues = Objects.requireNonNull(propValues);
        this.classifier = Objects.requireNonNull(classifier);
        this.sampleQuality = Objects.requireNonNull(sampleQuality);
    }

    public MatchupCalculation calculate(List<PlayerGameLine> playerGames, MatchupQuery query) {
        Objects.requireNonNull(playerGames, "playerGames");
        Objects.requireNonNull(query, "query");
        List<PlayerGameLine> eligible = playerGames.stream()
                .filter(game -> query.appliesSeason(game.season()))
                .filter(game -> matchesLocation(game, query.location()))
                .filter(game -> meetsMinutes(game, query.minMinutes()))
                .toList();
        List<PlayerGameLine> matchupRows = applyRecency(
                eligible.stream()
                        .filter(game -> game.opponentNbaTeamId() == query.nbaOpponentTeamId())
                        .toList(),
                query.recency());
        List<PlayerGameLine> baselineRows = applyRecency(eligible, query.recency());
        List<ClassifiedGame> matchupGames = classify(matchupRows, query);
        List<ClassifiedGame> baselineGames = classify(baselineRows, query);
        SampleSummary matchup = summarize(matchupGames);
        SampleSummary baseline = summarize(baselineGames);
        return new MatchupCalculation(
                matchup,
                baseline,
                sampleQuality.fromQualifyingGames(matchup.qualifyingGames()),
                differencePoints(matchup.hitRate(), baseline.hitRate()),
                matchupGames.stream().sorted(CHRONOLOGICAL_ORDER).toList());
    }

    private static boolean matchesLocation(PlayerGameLine game, LocationFilter location) {
        return location == LocationFilter.ALL
                || (location == LocationFilter.HOME && game.location() == GameLocation.HOME)
                || (location == LocationFilter.AWAY && game.location() == GameLocation.AWAY);
    }

    private static boolean meetsMinutes(PlayerGameLine game, MinMinutes minMinutes) {
        return game.minutes().compareTo(minMinutes.value()) >= 0;
    }

    private static List<PlayerGameLine> applyRecency(List<PlayerGameLine> games, RecencyFilter recency) {
        List<PlayerGameLine> ordered = games.stream().sorted(RECENCY_ORDER).toList();
        if (recency.limit().isEmpty() || ordered.size() <= recency.limit().getAsInt()) {
            return ordered;
        }
        return ordered.subList(0, recency.limit().getAsInt());
    }

    private List<ClassifiedGame> classify(List<PlayerGameLine> games, MatchupQuery query) {
        List<ClassifiedGame> classified = new ArrayList<>(games.size());
        for (PlayerGameLine game : games) {
            BigDecimal value = propValues.value(query.prop(), game);
            classified.add(new ClassifiedGame(game, value, classifier.classify(value, query.line(), query.direction())));
        }
        return classified;
    }

    static SampleSummary summarize(List<ClassifiedGame> games) {
        int hits = 0;
        int misses = 0;
        int pushes = 0;
        List<BigDecimal> values = new ArrayList<>(games.size());
        for (ClassifiedGame game : games) {
            values.add(game.propValue());
            switch (game.result()) {
                case HIT -> hits++;
                case MISS -> misses++;
                case PUSH -> pushes++;
            }
        }
        return new SampleSummary(
                games.size(),
                hits,
                misses,
                pushes,
                hitRate(hits, misses),
                average(values),
                median(values));
    }

    static ExactFraction hitRate(int hits, int misses) {
        int decided = hits + misses;
        if (decided == 0) {
            return null;
        }
        return ExactFraction.of(hits, decided);
    }

    static ExactFraction average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        long sum = 0L;
        for (BigDecimal value : values) {
            sum = Math.addExact(sum, value.longValueExact());
        }
        return ExactFraction.of(sum, values.size());
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1).add(sorted.get(middle)).divide(new BigDecimal("2"));
    }

    static ExactFraction differencePoints(ExactFraction matchupRate, ExactFraction baselineRate) {
        if (matchupRate == null || baselineRate == null) {
            return null;
        }
        return matchupRate.subtract(baselineRate).multiply(PERCENTAGE_POINTS);
    }
}
