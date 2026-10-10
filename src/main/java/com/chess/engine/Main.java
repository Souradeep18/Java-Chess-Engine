package com.chess.engine;

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.chess.engine.ui.ChessFrame;

import javax.swing.*;

public class Main {

	public static void main(String[] args) {
		
		SwingUtilities.invokeLater(() -> {

            ChessFrame frame =
                    new ChessFrame();

            frame.setVisible(true);
        });
		

	}

}
