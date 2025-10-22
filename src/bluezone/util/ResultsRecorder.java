package bluezone.util;

import bluezone.contoller.Simulator;
import bluezone.model.Player;
import bluezone.util.SimulationStats.Stats;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ResultsRecorder {
    private static final DateTimeFormatter TF = DateTimeFormatter.ISO_INSTANT;

    public static void recordRun(Simulator sim, List<Integer> playersInZoneHistory, List<Integer> deathsOutsideHistory, int countdownSeconds) {
        String ts = TF.format(Instant.now().atZone(ZoneOffset.UTC));
        String mode = sim.randomMode ? "random" : "adaptive";
        File dir = new File("results");
        if (!dir.exists()) dir.mkdirs();
        String filename = String.format("results/run-%s.json", ts.replaceAll("[:\\\\/\\s]","-"));

        StringBuilder sb = new StringBuilder();
        sb.append('{').append('\n');
        sb.append("  \"timestamp\": \"").append(escape(ts)).append("\",\n");
        sb.append("  \"mode\": \"").append(mode).append("\",\n");
        sb.append("  \"rounds\": ").append(sim.round).append(",\n");
        sb.append("  \"maxRounds\": ").append(sim.maxRounds).append(",\n");
        sb.append("  \"winnerLeftId\": ").append(sim.winnerLeftId).append(",\n");
        sb.append("  \"countdownRemaining\": ").append(countdownSeconds).append(",\n");

        // players
        sb.append("  \"players\": [\n");
        for (int i = 0; i < sim.players.size(); i++) {
            Player p = sim.players.get(i);
            sb.append("    {");
            sb.append("\"id\":").append(p.id).append(",");
            sb.append("\"alive\":").append(p.alive).append(",");
            sb.append("\"kills\":").append(p.kills).append(",");
            sb.append("\"distance\":").append(p.distance).append(",");
            sb.append("\"x\":").append(String.format(Locale.ROOT, "%.2f", p.x)).append(",");
            sb.append("\"y\":").append(String.format(Locale.ROOT, "%.2f", p.y)).append(",");
            sb.append("\"activity\":").append(String.format(Locale.ROOT, "%.4f", p.activity));
            sb.append("}");
            if (i < sim.players.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ],\n");

        // histories
        sb.append("  \"playersInZoneHistory\": ");
        sb.append(listToJsonArray(playersInZoneHistory)).append(",\n");
        sb.append("  \"deathsOutsideHistory\": ");
        sb.append(listToJsonArray(deathsOutsideHistory)).append(",\n");

        // metrics
        try {
            Stats stats = SimulationStats.computeMetrics(sim, playersInZoneHistory, deathsOutsideHistory, countdownSeconds);
            sb.append("  \"metrics\": ");
            sb.append(stats.toJson()).append('\n');
        } catch (Exception ex) {
            sb.append("  \"metrics\": {}\n");
        }

        sb.append('}').append('\n');

        try (FileWriter fw = new FileWriter(filename)) {
            fw.write(sb.toString());
        } catch (IOException ex) {
            System.err.println("Failed to write results to " + filename + ": " + ex.getMessage());
        }
    }

    private static String listToJsonArray(List<Integer> l) {
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; i < l.size(); i++) {
            b.append(l.get(i));
            if (i < l.size() - 1) b.append(',');
        }
        b.append(']');
        return b.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
