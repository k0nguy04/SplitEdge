package dev.splitedge.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class MatchupFixtures {

    static final long PLAYER = 201_939L;
    static final long GSW = 1_610_612_744L;
    static final long BOS = 1_610_612_738L;
    static final long LAL = 1_610_612_747L;

    private MatchupFixtures() {}

    static PlayerGameLine game(
            String nbaGameId,
            LocalDate date,
            String season,
            GameLocation location,
            long opponent,
            String minutes,
            int points,
            int rebounds,
            int assists,
            int threes) {
        return new PlayerGameLine(
                nbaGameId,
                date,
                season,
                location,
                PLAYER,
                GSW,
                opponent,
                new BigDecimal(minutes),
                points,
                rebounds,
                assists,
                threes);
    }

    static PlayerGameLine game(
            String nbaGameId,
            LocalDate date,
            GameLocation location,
            long opponent,
            String minutes,
            int points,
            int rebounds,
            int assists,
            int threes) {
        return game(nbaGameId, date, "2024-25", location, opponent, minutes, points, rebounds, assists, threes);
    }

    static List<PlayerGameLine> eightGameSeason() {
        return List.of(
                game("0022400001", LocalDate.of(2024, 10, 22), GameLocation.HOME, BOS, "36.000", 30, 5, 8, 4),
                game("0022400002", LocalDate.of(2024, 10, 24), GameLocation.AWAY, LAL, "32.000", 22, 4, 6, 2),
                game("0022400003", LocalDate.of(2024, 11, 1), GameLocation.HOME, BOS, "28.000", 24, 6, 7, 3),
                game("0022400004", LocalDate.of(2024, 11, 5), GameLocation.AWAY, BOS, "18.000", 12, 2, 3, 1),
                game("0022400005", LocalDate.of(2024, 11, 10), GameLocation.HOME, LAL, "34.000", 27, 5, 9, 5),
                game("0022400006", LocalDate.of(2024, 12, 1), GameLocation.AWAY, BOS, "40.000", 24, 8, 6, 2),
                game("0022400007", LocalDate.of(2025, 1, 15), GameLocation.HOME, LAL, "36.000", 25, 5, 5, 3),
                game("0022400008", LocalDate.of(2025, 3, 1), GameLocation.AWAY, LAL, "30.000", 24, 4, 4, 2));
    }

    static List<PlayerGameLine> onlyPushLast5() {
        return List.of(
                game("0022400010", LocalDate.of(2024, 10, 1), GameLocation.HOME, BOS, "30.000", 30, 5, 5, 2),
                game("0022400011", LocalDate.of(2024, 11, 1), GameLocation.HOME, BOS, "32.000", 24, 4, 4, 1),
                game("0022400012", LocalDate.of(2024, 11, 2), GameLocation.AWAY, BOS, "31.000", 24, 4, 4, 1),
                game("0022400013", LocalDate.of(2024, 11, 3), GameLocation.HOME, BOS, "33.000", 24, 4, 4, 1),
                game("0022400014", LocalDate.of(2024, 11, 4), GameLocation.AWAY, BOS, "29.000", 24, 4, 4, 1),
                game("0022400015", LocalDate.of(2024, 11, 5), GameLocation.HOME, BOS, "34.000", 24, 4, 4, 1),
                game("0022400016", LocalDate.of(2024, 11, 6), GameLocation.HOME, LAL, "30.000", 20, 3, 3, 0));
    }

    static List<PlayerGameLine> sameDateTieBreak() {
        return List.of(
                game("0022400001", LocalDate.of(2024, 10, 1), GameLocation.HOME, BOS, "30.000", 10, 1, 1, 0),
                game("0022400002", LocalDate.of(2024, 10, 1), GameLocation.HOME, BOS, "30.000", 11, 1, 1, 0),
                game("0022400003", LocalDate.of(2024, 11, 1), GameLocation.HOME, BOS, "30.000", 12, 1, 1, 0),
                game("0022400004", LocalDate.of(2024, 11, 2), GameLocation.HOME, BOS, "30.000", 13, 1, 1, 0),
                game("0022400005", LocalDate.of(2024, 11, 3), GameLocation.HOME, BOS, "30.000", 14, 1, 1, 0),
                game("0022400006", LocalDate.of(2024, 11, 4), GameLocation.HOME, BOS, "30.000", 15, 1, 1, 0));
    }

    static List<PlayerGameLine> sequentialVsBos(int count) {
        return sequentialVsBos(count, 20);
    }

    static List<PlayerGameLine> sequentialVsBos(int count, int points) {
        List<PlayerGameLine> games = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            games.add(game(
                    "00224" + String.format("%05d", index),
                    LocalDate.of(2024, 10, 1).plusDays(index),
                    GameLocation.HOME,
                    BOS,
                    "30.000",
                    points,
                    1,
                    1,
                    0));
        }
        return games;
    }

    /**
     * Five oldest home games versus Boston, then five newest away games versus Boston.
     * HOME then LAST_5 keeps five games; LAST_5 then HOME would keep none.
     */
    static List<PlayerGameLine> homeGamesThenRecentAwayGames() {
        List<PlayerGameLine> games = new ArrayList<>(10);
        for (int index = 1; index <= 5; index++) {
            games.add(game(
                    "00224000" + String.format("%02d", index),
                    LocalDate.of(2024, 10, index),
                    GameLocation.HOME,
                    BOS,
                    "30.000",
                    20 + index,
                    1,
                    1,
                    0));
        }
        for (int index = 6; index <= 10; index++) {
            games.add(game(
                    "00224000" + String.format("%02d", index),
                    LocalDate.of(2024, 10, index),
                    GameLocation.AWAY,
                    BOS,
                    "30.000",
                    20 + index,
                    1,
                    1,
                    0));
        }
        return games;
    }

    /**
     * Five oldest 30-minute games versus Boston, then five newest 10-minute games.
     * Minutes &gt;= 20 then LAST_5 keeps five games; LAST_5 then minutes would keep none.
     */
    static List<PlayerGameLine> highMinutesThenRecentLowMinutes() {
        List<PlayerGameLine> games = new ArrayList<>(10);
        for (int index = 1; index <= 5; index++) {
            games.add(game(
                    "00224001" + String.format("%02d", index),
                    LocalDate.of(2024, 11, index),
                    GameLocation.HOME,
                    BOS,
                    "30.000",
                    20,
                    1,
                    1,
                    0));
        }
        for (int index = 6; index <= 10; index++) {
            games.add(game(
                    "00224001" + String.format("%02d", index),
                    LocalDate.of(2024, 11, index),
                    GameLocation.HOME,
                    BOS,
                    "10.000",
                    20,
                    1,
                    1,
                    0));
        }
        return games;
    }

    static List<PlayerGameLine> exactMinutesBoundary() {
        return List.of(
                game("0022400200", LocalDate.of(2024, 10, 1), GameLocation.HOME, BOS, "19.999", 10, 1, 1, 0),
                game("0022400201", LocalDate.of(2024, 10, 2), GameLocation.HOME, BOS, "20.000", 30, 1, 1, 0),
                game("0022400202", LocalDate.of(2024, 10, 3), GameLocation.HOME, BOS, "20.001", 12, 1, 1, 0));
    }

    /**
     * Five oldest 2024-25 games versus Boston, then five newest 2023-24 games.
     * Season then LAST_5 keeps five games; LAST_5 then season would keep none.
     */
    static List<PlayerGameLine> currentSeasonThenRecentPriorSeason() {
        List<PlayerGameLine> games = new ArrayList<>(10);
        for (int index = 1; index <= 5; index++) {
            games.add(game(
                    "00224002" + String.format("%02d", index),
                    LocalDate.of(2024, 10, index),
                    "2024-25",
                    GameLocation.HOME,
                    BOS,
                    "30.000",
                    20,
                    1,
                    1,
                    0));
        }
        for (int index = 6; index <= 10; index++) {
            games.add(game(
                    "00223002" + String.format("%02d", index),
                    LocalDate.of(2025, 3, index),
                    "2023-24",
                    GameLocation.HOME,
                    BOS,
                    "30.000",
                    20,
                    1,
                    1,
                    0));
        }
        return games;
    }

    static List<PlayerGameLine> oneHitTwoMissesVsBos() {
        return List.of(
                game("0022400301", LocalDate.of(2024, 10, 1), GameLocation.HOME, BOS, "30.000", 30, 1, 1, 0),
                game("0022400302", LocalDate.of(2024, 10, 2), GameLocation.HOME, BOS, "30.000", 20, 1, 1, 0),
                game("0022400303", LocalDate.of(2024, 10, 3), GameLocation.HOME, BOS, "30.000", 20, 1, 1, 0));
    }

    static MatchupQuery pointsOverBos(BigDecimal line, RecencyFilter recency, BigDecimal minMinutes) {
        return pointsOverBos(line, LocationFilter.ALL, recency, minMinutes);
    }

    static MatchupQuery pointsOverBos(int line, RecencyFilter recency, int minMinutes) {
        return pointsOverBos(BigDecimal.valueOf(line), recency, BigDecimal.valueOf(minMinutes));
    }

    static MatchupQuery pointsOverBos(BigDecimal line, RecencyFilter recency, int minMinutes) {
        return pointsOverBos(line, recency, BigDecimal.valueOf(minMinutes));
    }

    static MatchupQuery pointsOverBos(
            BigDecimal line, LocationFilter location, RecencyFilter recency, BigDecimal minMinutes) {
        return new MatchupQuery(
                BOS,
                PropType.POINTS,
                Line.of(line),
                Direction.OVER,
                "2024-25",
                location,
                recency,
                MinMinutes.of(minMinutes));
    }

    static MatchupQuery pointsOverBos(
            BigDecimal line, LocationFilter location, RecencyFilter recency, int minMinutes) {
        return pointsOverBos(line, location, recency, BigDecimal.valueOf(minMinutes));
    }
}
