package uucki.graphics;

import uucki.game.Board;
import uucki.type.Position;
import uucki.graphics.reversi.MonteCarlo;

import java.awt.*;

public class Window{

    private Frame frame = null;
    private Fields fields = null;

    public Window(Board board, MonteCarlo mc) {
        frame = new Frame();
        frame.setSize(500,500);


        fields = new Fields(board, null);
        frame.add(fields);
        frame.setVisible(true);
    }

    public void update(Board board) {
        fields.updateValues(board);
        fields.repaint();
    }

    public void repaint() {
        fields.repaint();
    }

    public Position getPosition() {
        fields.acceptingClick();

        Position newPosition = null;
        while(newPosition == null) {
            newPosition = fields.consumeClick();
            try {
                Thread.sleep(300);
            } catch(InterruptedException e) {

            }
        }
        return newPosition;
    }

    public void hide() {
        frame.setVisible(false);
        frame.dispose();
    }
}
