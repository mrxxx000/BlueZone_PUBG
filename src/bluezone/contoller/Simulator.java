package bluezone.contoller;

import bluezone.model.Player;
import bluezone.model.Zone;

import java.util.*;

public class Simulator {
    public final int canvasW, canvasH;
    public final List<Player> players = new ArrayList<>();
    private final Random rng = new Random();
    public int round = 0;
    public int maxRounds = 6;
    // minimum rounds that must be played before a winner can be declared
    public static final int MIN_ROUNDS = 3;
    // when true, candidateAdaptive() will return a random zone rather than sampling players
    public boolean randomMode = false;
    public final double[] roundRadii = new double[]{280, 240, 200, 160, 140, 100, 80};
    public final Map<Player, Long> outsideSince = new HashMap<>();

    public Zone adaptiveLeft;
    // per-zone winner ids (-1 means none)
    public int winnerLeftId = -1;
    public int winnerRightId = -1;

    public Simulator(int w, int h){ this.canvasW = w; this.canvasH = h; }

    public double rand(double a, double b){ return a + rng.nextDouble()*(b-a); }

    public void reset(int count){
        players.clear();
        int leftMaxX = canvasW - 20;
        for(int i=0;i<count;i++){
            Player p = new Player();
            p.id = i;
            p.x = rand(20, leftMaxX);
            p.y = rand(20, canvasH-20);
            p.kills = rng.nextInt(10);
            p.distance = rng.nextInt(1000);
            p.activity = rng.nextDouble();
            p.alive = true;
            players.add(p);
        }
        round = 0;
        if (randomMode) adaptiveLeft = candidateRandom(); else adaptiveLeft = new Zone(canvasW/2, canvasH/2);
    }

    public boolean isFinished(){
        long alive = players.stream().filter(p->p.alive).count();
        // require at least MIN_ROUNDS to have been played before declaring finished
        boolean basicFinished = (round >= maxRounds) || (alive <= 1);
        return basicFinished && round >= MIN_ROUNDS;
    }

    public void advanceRound(){
        if(round >= maxRounds) return;
    Zone[] adapt = candidateAdaptive();
    adaptiveLeft = adapt[0];

        round++;
        outsideSince.clear();
        // Randomly eliminate a random number of alive players each advance.
        List<Player> alive = new ArrayList<>();
        for (Player p : players) if (p.alive) alive.add(p);
        int aliveCount = alive.size();
        if (aliveCount > 1) {
            // scale max eliminations with number of alive players:
            // - more players => more elimination
            // - at most half of alive players
            // - absolute cap at 8
            int scaledMax = Math.min(8, Math.max(1, aliveCount / 2));
            int maxElim = Math.min(aliveCount - 1, scaledMax); // ensure we never eliminate all here
            int elimCount = 1 + rng.nextInt(maxElim); // 1..maxElim
            // shuffle and eliminate first elimCount players
            Collections.shuffle(alive, rng);
            for (int i = 0; i < elimCount; i++) {
                Player eliminated = alive.get(i);
                eliminated.alive = false;
            }
        }

        // If we've reached max rounds or only 0/1 players remain, determine the final winner
        long aliveNow = players.stream().filter(p -> p.alive).count();

        // compute inside count using currentRadius
        double currentRadius = roundRadii[Math.min(round, roundRadii.length - 1)];
        int inLeft = 0;
        for (Player p : players) {
            if (!p.alive) continue;
            double dLeft = Math.hypot(p.x - adaptiveLeft.x, p.y - adaptiveLeft.y);
            if (dLeft <= currentRadius) inLeft++;
        }

        // If a zone has exactly one alive player inside and we haven't recorded a winner for that zone yet,
        // register that player as the zone winner. This allows a zone to have a winner before the other zone.
        if (inLeft == 1 && winnerLeftId == -1) {
            for (Player p : players) {
                if (!p.alive) continue;
                double d = Math.hypot(p.x - adaptiveLeft.x, p.y - adaptiveLeft.y);
                if (d <= currentRadius) { winnerLeftId = p.id; break; }
            }
        }
        // no right zone anymore

        // If global only one or zero players remain, finish the game early and compute any missing winners
        if (round >= maxRounds || aliveNow <= 1) {
            checkFinalWinner();
        }
    }

   public void stepAnimation(){
        for(Player p : players){
            if(!p.alive) continue;
            double speed = 0.4 + p.activity * 1.2;
            p.x += rand(-1,1) * speed; p.y += rand(-1,1) * speed;
            p.x = clamp(p.x, 12, canvasW - 12);
            p.y = clamp(p.y, 12, canvasH - 12);
        }
        // Simple combat: players close to each other may fight and one dies.
        // This simulates player vs player eliminations during the animation ticks.
        double combatRadius = 12.0; // pixels
        List<Player> aliveList = new ArrayList<>();
        for (Player p : players) if (p.alive) aliveList.add(p);
        // shuffle order so fights are randomized
        Collections.shuffle(aliveList, rng);
        for (int i = 0; i < aliveList.size(); i++) {
            Player a = aliveList.get(i);
            if (!a.alive) continue; // might have been killed earlier this tick
            for (int j = i+1; j < aliveList.size(); j++) {
                Player b = aliveList.get(j);
                if (!b.alive) continue;
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double d = Math.hypot(dx, dy);
                if (d <= combatRadius) {
                    // resolve duel uniformly at random (50/50)
                    if (rng.nextBoolean()) {
                        // a wins
                        b.alive = false;
                        a.kills += 1;
                    } else {
                        // b wins
                        a.alive = false;
                        b.kills += 1;
                        break; // a is dead, stop checking further opponents for a
                    }
                }
            }
        }
        // Outside-of-zone handling: if a player remains outside the current zone for >= 10 seconds, eliminate them.
        long now = System.currentTimeMillis();
        double currentRadius = roundRadii[Math.min(round, roundRadii.length-1)];
        for (Player p : players) {
            if (!p.alive) continue;
            double dx = p.x - adaptiveLeft.x;
            double dy = p.y - adaptiveLeft.y;
            double d = Math.hypot(dx, dy);
            if (d > currentRadius) {
                outsideSince.putIfAbsent(p, now);
                long since = now - outsideSince.get(p);
                if (since >= 10000) { // 10 seconds outside
                    p.alive = false;
                    outsideSince.remove(p);
                }
            } else {
                // back inside -> reset timer
                outsideSince.remove(p);
            }
        }
    }
    private Zone[] candidateAdaptive(){
        // if randomMode is enabled, always pick a random zone instead of sampling players
        if (randomMode) {
            return new Zone[]{ candidateRandom() };
        }

        List<Player> alive = new ArrayList<>();
        for(Player p : players) if(p.alive) alive.add(p);
        if(alive.isEmpty()){
            return new Zone[]{ new Zone(canvasW/2, canvasH/2) };
        }
        double[] weights = new double[alive.size()];
        double sum = 0;
        for(int i=0;i<alive.size();i++){
            Player p = alive.get(i);
            weights[i] = 1 + p.kills*0.8 + p.activity*2;
            sum += weights[i];
        }
        Zone left = sampleWeighted(alive, weights, sum, true);
        return new Zone[]{left};
    }

    private Zone sampleWeighted(List<Player> alive, double[] weights, double sum, boolean useLeft){
        double r = rng.nextDouble() * sum; int idx = 0;
        while(r > 0 && idx < weights.length){ r -= weights[idx++]; }
        Player p = alive.get(Math.max(0, idx-1));
        double jitter = 60;
        double x = clamp(p.x + rand(-jitter, jitter), 60, canvasW - 60);
        double y = clamp(p.y + rand(-jitter, jitter), 60, canvasH - 60);
        return new Zone(x,y);
    }

    private Zone candidateRandom(){
        double x = rand(60, canvasW - 60);
        double y = rand(60, canvasH - 60);
        return new Zone(x,y);
    }

    private double clamp(double v, double a, double b){ return Math.max(a, Math.min(b, v)); }

    private void checkFinalWinner(){
        // Ensure we end with at most one alive player before declaring winner.
        long aliveNow = players.stream().filter(p -> p.alive).count();
        if (aliveNow > 1) {
            eliminateUntilOneLeft();
            aliveNow = players.stream().filter(p -> p.alive).count();
        }

        // If exactly one remains, that player is the winner for the adaptive zone.
        if (aliveNow == 1) {
            Player w = null; for (Player p : players) if (p.alive) { w = p; break; }
            winnerLeftId = w != null ? w.id : -1;
        } else {
            // fallback: pick a winner by heuristic
            winnerLeftId = findZoneWinner(true);
        }
    }

    // Public API to force finish the game immediately (calculate final winners)
    public void finishGame(){
        // ensure we respect the minimum rounds requirement
        if(this.round < MIN_ROUNDS) this.round = MIN_ROUNDS;
        checkFinalWinner();
        // mark as finished by advancing round to maxRounds
        this.round = this.maxRounds;
    }

    // Repeatedly eliminate random alive players until at most one remains.
    private void eliminateUntilOneLeft(){
        List<Player> aliveList = new ArrayList<>();
        while(true){
            aliveList.clear();
            for(Player p : players) if(p.alive) aliveList.add(p);
            int aliveCount = aliveList.size();
            if(aliveCount <= 1) break;
            int maxElim = Math.min(aliveCount - 1, 5);
            int elimCount = 1 + rng.nextInt(maxElim);
            Collections.shuffle(aliveList, rng);
            for(int i=0;i<elimCount && aliveList.size()>1;i++){
                Player out = aliveList.get(i);
                out.alive = false;
            }
        }
    }

    // returns the winning player's id for the left zone (useLeft=true) or right zone (false), or -1
    private int findZoneWinner(boolean useLeft){
        double bestScore = Double.NEGATIVE_INFINITY;
        Player best = null;
    double zx = adaptiveLeft != null ? adaptiveLeft.x : -1;
    double zy = adaptiveLeft != null ? adaptiveLeft.y : -1;
        double currentRadius = roundRadii[Math.min(round, roundRadii.length - 1)];

        // prefer alive players inside the zone
        for (Player p : players) {
            if (!p.alive) continue;
            double px = p.x;
            double py = p.y;
            if (zx < 0 || zy < 0) continue;
            double d = Math.hypot(px - zx, py - zy);
            if (d > currentRadius) continue;
            double score = p.kills * 2 + p.activity;
            if (best == null || score > bestScore || (score == bestScore && tieBreak(p, best))) {
                best = p; bestScore = score;
            }
        }

        // if none alive inside, fall back to best alive anywhere
        if (best == null) {
            for (Player p : players) {
                if (!p.alive) continue;
                double score = p.kills * 2 + p.activity;
                if (best == null || score > bestScore || (score == bestScore && tieBreak(p, best))) {
                    best = p; bestScore = score;
                }
            }
        }

        if (best != null) return best.id;
        // final fallback: pick best overall even if dead
        best = null; bestScore = Double.NEGATIVE_INFINITY;
        for (Player p : players) {
            double score = p.kills * 2 + p.activity;
            if (best == null || score > bestScore || (score == bestScore && tieBreak(p, best))) {
                best = p; bestScore = score;
            }
        }
        return best != null ? best.id : -1;
    }

    // tie-break heuristic: prefer higher kills, then higher activity, then lower id
    private boolean tieBreak(Player a, Player b) {
        if (a.kills != b.kills) return a.kills > b.kills;
        if (Double.compare(a.activity, b.activity) != 0) return a.activity > b.activity;
        return a.id < b.id;
    }
}
