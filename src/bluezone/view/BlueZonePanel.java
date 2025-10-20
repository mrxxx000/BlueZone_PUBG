package bluezone.view;

import bluezone.contoller.Simulator;
import bluezone.model.Player;


import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public class BlueZonePanel extends JPanel implements MouseMotionListener {
    private final Simulator sim;
    private JLabel hoverLabel = null;
    private final Timer animTimer;
    // single adaptive map only

    public BlueZonePanel(Simulator sim){
        this.sim = sim;
        setPreferredSize(new Dimension(sim.canvasW, sim.canvasH));
        setBackground(new Color(11,18,32));
        addMouseMotionListener(this);
        sim.reset(30);
        animTimer = new Timer(30, e -> { sim.stepAnimation(); repaint(); });
        animTimer.start();
    }

    public void setHoverLabel(JLabel l){ this.hoverLabel = l; }

    public int getRound(){ return sim.round; }
    public boolean isFinished(){ return sim.isFinished(); }

    public void reset(int count){ sim.reset(count); }
    public void advanceRound(){ sim.advanceRound(); if(sim.isFinished()){ showWinner(); } }

    private void showWinner(){
    // Show the adaptive zone winner and stats
        double currentRadius = sim.roundRadii[Math.min(sim.round, sim.roundRadii.length-1)];
        StringBuilder sb = new StringBuilder();
        sb.append("Adaptive zone:\n");
        if(sim.adaptiveLeft != null){
            int inLeft = 0;
            for(Player p : sim.players) if(p.alive && dist(p.x, p.y, sim.adaptiveLeft.x, sim.adaptiveLeft.y) <= currentRadius) inLeft++;
            sb.append(String.format("Players inside: %d\n", inLeft));
            int leftId = sim.winnerLeftId;
            if(leftId >= 0){ Player p = null; for(Player pp : sim.players) if(pp.id == leftId) p = pp; if(p != null) sb.append(String.format("Winner: Player %d — kills: %d, dist: %d, activity: %.2f\n", p.id, p.kills, p.distance, p.activity)); else sb.append("Winner: (unknown)\n"); }
            else sb.append("Winner: (none)\n");
        } else sb.append("No adaptive zone data.\n");
        JOptionPane.showMessageDialog(this, sb.toString(), "Zone Winner / Stats", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int canvasW = getWidth(), canvasH = getHeight();
        g2.setColor(new Color(27,43,58));
        g2.fillRect(0,0,canvasW,canvasH);

    double currentRadius = sim.roundRadii[Math.min(sim.round, sim.roundRadii.length-1)];
    if(sim.adaptiveLeft != null){ drawZone(g2, sim.adaptiveLeft.x, sim.adaptiveLeft.y, (int)currentRadius, new Color(59,130,246,32), new Color(59,130,246,100)); }

    g2.setColor(new Color(207,232,255)); g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    // draw zone labels and per-zone alive counts
    int inLeft = 0;
    for(Player p : sim.players){ if(!p.alive) continue; if(Math.hypot(p.x - sim.adaptiveLeft.x, p.y - sim.adaptiveLeft.y) <= currentRadius) inLeft++; }
    g2.drawString("Player inside: " + inLeft, 10, 20);

    for(Player p : sim.players){ if(!p.alive) continue; drawPlayer(g2, p.x, p.y, p); }

        g2.setColor(new Color(207,232,255)); g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    g2.drawString("Round: " + sim.round + " / " + sim.maxRounds, canvasW - 140, canvasH - 28);
    long aliveCount = sim.players.stream().filter(p->p.alive).count();
    g2.drawString("Alive: " + aliveCount, canvasW - 140, canvasH - 10);

        g2.dispose();
    }

    private void drawZone(Graphics2D g2, double cx, double cy, int r, Color fill, Color stroke){ g2.setColor(fill); g2.fillOval((int)(cx - r), (int)(cy - r), r*2, r*2); g2.setStroke(new BasicStroke(2f)); g2.setColor(stroke); g2.drawOval((int)(cx - r), (int)(cy - r), r*2, r*2); }
    private void drawPlayer(Graphics2D g2, double x, double y, Player p){ float a = (float)Math.max(0, Math.min(1, p.activity)); int r = 6 + p.kills * 2; Color outer = new Color(59,130,246, (int)(32 + 160 * a)); g2.setColor(outer); g2.fillOval((int)(x - r - 2), (int)(y - r - 2), (r+2)*2, (r+2)*2); g2.setColor(new Color(207,232,255)); g2.fillOval((int)(x - r), (int)(y - r), r*2, r*2); g2.setColor(new Color(11,18,32)); g2.fillOval((int)(x - 2), (int)(y - 2), 4, 4); }

    @Override public void mouseDragged(MouseEvent e) { }
    @Override public void mouseMoved(MouseEvent e) {
        int mx = e.getX(); int my = e.getY(); String text = "Hover over a player";
    for(Player p : sim.players){ if(!p.alive) continue; if(dist(mx, my, p.x, p.y) < 12){ text = String.format("Player %d — kills: %d, dist: %d, activity: %.2f", p.id, p.kills, p.distance, p.activity); break; } }
        if(hoverLabel != null) hoverLabel.setText(text);
    }

    private double dist(double ax, double ay, double bx, double by){ return Math.hypot(ax-bx, ay-by); }
}
