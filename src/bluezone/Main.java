package bluezone;

import javax.swing.SwingUtilities;
import bluezone.view.BlueZoneFrame;
import bluezone.contoller.Simulator;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // create a single simulator and frame (adaptive map only)
            Simulator sim = new Simulator(1000, 500);
            BlueZoneFrame frame = new BlueZoneFrame(sim);
            frame.setLocation(100, 100);
            frame.setVisible(true);
        });
    }
}
