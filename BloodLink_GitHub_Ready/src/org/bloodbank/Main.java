package org.bloodbank;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BloodBankGUI gui = new BloodBankGUI();
            gui.setVisible(true);
        });
    }
}
