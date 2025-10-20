package bluezone.view;

import bluezone.contoller.Simulator;

import javax.swing.*;
import java.awt.*;

public class BlueZoneFrame extends JFrame {
    private final BlueZonePanel panel;

    public BlueZoneFrame(Simulator sim){
        setTitle("Adaptive - BlueZone");
    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setLocationRelativeTo(null);

    panel = new BlueZonePanel(sim);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Players:"));
        SpinnerNumberModel model = new SpinnerNumberModel(30, 4, 200, 1);
        JSpinner spinner = new JSpinner(model);
        top.add(spinner);
        JButton resetBtn = new JButton("Reset");
        JButton roundBtn = new JButton("Advance Round");
        top.add(resetBtn);
        top.add(roundBtn);
        JLabel roundLabel = new JLabel("Round: 0");
        top.add(roundLabel);
        top.add(Box.createHorizontalStrut(16));
        JLabel hover = new JLabel("Hover over a player");
        top.add(hover);

        resetBtn.addActionListener(e -> { int count = (Integer) spinner.getValue(); panel.reset(count); roundLabel.setText("Round: 0"); roundBtn.setEnabled(true); spinner.setEnabled(true); });
        roundBtn.addActionListener(e -> { panel.advanceRound(); roundLabel.setText("Round: " + panel.getRound()); if(panel.isFinished()){ roundBtn.setEnabled(false); spinner.setEnabled(false); } });

        panel.setHoverLabel(hover);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(panel, BorderLayout.CENTER);
        pack();
    }
}
