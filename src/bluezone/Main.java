package bluezone;

import javax.swing.SwingUtilities;
import bluezone.view.BlueZoneFrame;
import bluezone.contoller.Simulator;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // create two independent simulators and frames: left (adaptive) and right (random)
            Simulator simLeft = new Simulator(1000, 500);
            Simulator simRight = new Simulator(1000, 500);
            BlueZoneFrame leftFrame = new BlueZoneFrame(simLeft, true);
            BlueZoneFrame rightFrame = new BlueZoneFrame(simRight, false);
            leftFrame.setLocation(100, 100);
            rightFrame.setLocation(750, 100);
            leftFrame.setVisible(true);
            rightFrame.setVisible(true);
        });
    }
}
