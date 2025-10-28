package bluezone.model;

public class Player {
    public int id;
    public double x,y; // adaptive map
    public int kills;
    public int distance;
    public double activity;
    public boolean alive;
    public double targetX = Double.NaN;
    public double targetY = Double.NaN;
    public boolean hasTarget = false;
}
