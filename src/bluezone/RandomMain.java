package bluezone;

import javax.swing.SwingUtilities;
import bluezone.view.BlueZoneFrame;
import bluezone.contoller.Simulator;

public class RandomMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Simulator sim = new Simulator(1000, 500);
            sim.randomMode = true; 
            BlueZoneFrame frame = new BlueZoneFrame(sim);
            frame.setLocation(100, 100);
            frame.setVisible(true);
        });
    }
}
