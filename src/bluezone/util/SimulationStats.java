package bluezone.util;

import bluezone.contoller.Simulator;
import bluezone.model.Player;

import java.util.List;
import java.util.Locale;

public class SimulationStats {
    public static class Stats {
        public double avgDistanceToCenter;
        public int aliveCount;
    public double avgKills;
        public double killVariance;
        public double avgActivity;
        public int winnerKills;
        public int roundsPlayed;
        public double eliminationsPerRound;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append(String.format(Locale.ROOT, "\"avgDistanceToCenter\": %.4f,", avgDistanceToCenter));
            sb.append(String.format(Locale.ROOT, "\"aliveCount\": %d,", aliveCount));
            sb.append(String.format(Locale.ROOT, "\"avgKills\": %.4f,", avgKills));
            sb.append(String.format(Locale.ROOT, "\"killVariance\": %.4f,", killVariance));
            sb.append(String.format(Locale.ROOT, "\"avgActivity\": %.4f,", avgActivity));
            sb.append(String.format(Locale.ROOT, "\"winnerKills\": %d,", winnerKills));
            sb.append(String.format(Locale.ROOT, "\"roundsPlayed\": %d,", roundsPlayed));
            sb.append(String.format(Locale.ROOT, "\"eliminationsPerRound\": %.4f", eliminationsPerRound));
            sb.append('}');
            return sb.toString();
        }
    }

    public static Stats computeMetrics(Simulator sim, List<Integer> playersInZoneHistory, List<Integer> deathsOutsideHistory, int countdownSeconds) {
        Stats s = new Stats();
        List<Player> players = sim.players;
        int total = players.size();
        double sumDist = 0; int alive = 0; double sumKills = 0; double sumActivity = 0;
        // compute distance to current adaptive zone center (or canvas center if missing)
        double centerX = sim.adaptiveLeft != null ? sim.adaptiveLeft.x : sim.canvasW / 2.0;
        double centerY = sim.adaptiveLeft != null ? sim.adaptiveLeft.y : sim.canvasH / 2.0;
        for (Player p : players) {
            if (p.alive) {
                double dx = p.x - centerX;
                double dy = p.y - centerY;
                sumDist += Math.hypot(dx, dy);
                alive++;
            }
            sumKills += p.kills;
            sumActivity += p.activity;
        }
        s.aliveCount = alive;
        s.avgDistanceToCenter = alive > 0 ? sumDist / alive : 0.0;
    s.avgKills = total > 0 ? sumKills / total : 0.0;
        // variance of kills
        double meanKills = s.avgKills;
        double var = 0;
        for (Player p : players) { var += (p.kills - meanKills) * (p.kills - meanKills); }
        s.killVariance = total > 0 ? var / total : 0.0;
        s.avgActivity = total > 0 ? sumActivity / total : 0.0;
        Player winner = null; for (Player p : players) if (p.id == sim.winnerLeftId) { winner = p; break; }
        s.winnerKills = winner != null ? winner.kills : 0;
        s.roundsPlayed = sim.round;
        s.eliminationsPerRound = s.roundsPlayed > 0 ? (double)(total - s.aliveCount) / s.roundsPlayed : 0.0;
        return s;
    }
}
