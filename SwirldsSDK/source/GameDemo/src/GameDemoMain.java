
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF 
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR 
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR 
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.swirlds.platform.AddressBook;
import com.swirlds.platform.Browser;
import com.swirlds.platform.Network;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This game allows the user to wander around the board using the arrow keys. The first user to hit the goal
 * gets one point, and the goal resets to a pseudorandom location.
 * 
 * The transactions are each a byte, saying the user moved one step north, south, east, or west, or did
 * nothing.
 * 
 * The user can hit the spacebar to start automatic movement, which is on by default. Hitting any arrow or
 * WASD key will turn off automatic movement.
 */
public class GameDemoMain implements SwirldMain {
	/** delay after each time through the main game loop in milliseconds (which updates screen, etc) */
	private long gameLoopDelay = 200;
	/** the app is run by this */
	public Platform platform;
	/** ID number for this member */
	public int selfId;
	/** so user can use arrows and spacebar */
	GuiKeyListener keyListener = new GuiKeyListener();
	/** the entire window */
	JFrame frame;
	/** should computer play for the user? */
	boolean automove = true;

	/**
	 * Listen for input from the keyboard, and remember the last key typed.
	 */
	private class GuiKeyListener implements KeyListener {
		@Override
		public void keyReleased(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			keyboardAndRedraw(false, true, e);
		}

		@Override
		public void keyTyped(KeyEvent e) {
		}
	}

	/** the graphics area where the game board is drawn */
	private class Board extends JPanel {
		/** used for serializing */
		private static final long serialVersionUID = 1L;

		public void paintComponent(Graphics g) {
			super.paintComponent(g);

			GameDemoState state = (GameDemoState) platform.getState();

			// the state methods are all synchronized, but we still want to put them all in a
			// single synchronized block to ensure the state doesn't change in between them
			synchronized (state) {
				int textHeight = 15;
				AddressBook addr = state.getAddressBookCopy();
				int numMem = addr.getSize();
				int width = getWidth();
				int height1 = Math.max(6, numMem) * textHeight;   // scoreboard
				int height2 = getHeight() - height1; // playing board

				int x = width * state.getxGoal() / state.getxBoardSize();
				int y = height1
						+ height2 * state.getyGoal() / state.getyBoardSize();
				int dx = Math.max(18, width / state.getxBoardSize());
				int dy = Math.max(18, height2 / state.getyBoardSize());
				g.setColor(Color.RED);
				g.fillOval(x, y, dx, dy); // outer ring around the prize
				g.setColor(Color.WHITE);
				g.fillOval(x + dx / 6, y + dy / 6, 2 * dx / 3, 2 * dy / 3); // inner ring around the prize
				g.setColor(state.getColorGoal());
				g.fillOval(x + dx / 3, y + dy / 3, dx / 3, dy / 3); // innermost ring around the prize

				for (int i = 0; i < platform.getState().getAddressBookCopy()
						.getSize(); i++) {
					x = width * state.getxPlayer()[i] / state.getxBoardSize();
					y = height1 + height2 * state.getyPlayer()[i]
							/ state.getyBoardSize();
					dx = Math.max(18, width / state.getxBoardSize());
					dy = Math.max(18, height2 / state.getyBoardSize());
					g.setColor(state.getColor()[i]);
					g.fillRect(x, y, dx, dy);  // a player
					g.fillRect(0,   // a color box on the scoreboard
							(int) ((i) * textHeight), textHeight, textHeight);
				}

				g.setColor(Color.BLACK);
				g.setFont(new Font(Font.MONOSPACED, 12, 12));
				g.drawLine(0, height1, width, height1);

				int row = 1;
				int col = 190;
				g.drawString("Trans/sec:  "
						+ (long) platform.getStats().getStat("trans/sec"), col,
						row++ * textHeight - 3);
				g.drawString("Events/sec: "
						+ (long) platform.getStats().getStat("events/sec"), col,
						row++ * textHeight - 3);
				g.drawString(
						"Internal: " + Network.getInternalIPAddress() + " : "
								+ platform.getAddress().getPortInternalIpv4(),
						col, row++ * textHeight - 3);
				g.drawString(
						"External: "
								+ (Network.getExternalIpAddress().equals("")
										? ""
										: Network.getExternalIpAddress() + " : "
												+ platform.getAddress()
														.getPortExternalIpv4()),
						col, row++ * textHeight - 3);
				g.drawString("Arrows/WASD move", col, row++ * textHeight - 3);
				g.drawString("Spacebar automoves", col, row++ * textHeight - 3);

				for (int i = 0; i < numMem; i++) {
					g.drawString(String.format( // scores and names on the scoreboard
							"% 5d %-5s (trans: %d)", state.getScore()[i],
							addr.getAddress(i).getSelfName(),
							state.getNumTrans()[i]), 0,
							(int) ((i + .9) * textHeight));
				}
			}
		}
	}

	/**
	 * Create a new transaction and send it to the platform. This must be synchronized so that the threads
	 * in run() and preEvent() won't conflict.
	 * 
	 * @param type
	 *            the direction to move: 0=NOP 1=North 2=South 3=East 4=West
	 */
	synchronized private void sendTransaction(int type) {
		byte[] transaction = new byte[1];

		// Send the transaction to the Platform, which will then
		// forward it to the State object, and also send it to
		// all the other members of the community by sending them
		// an Event containing it during syncs with them.
		transaction[0] = (byte) type; // 0=NOP 1=N 2=S 3=E 4=W
		platform.createTransaction(transaction, null);
	}

	/**
	 * This is the body of the main event loop, and is also called for each preEvent, and is also called for
	 * each key pressed. It handles the given keyboard event, creates needed transactions, and updates the
	 * screen based on the current state. If insertNop is true, then it makes sure the event won't be empty,
	 * by adding a "no operation" transaction if there are no others in it.
	 *
	 * @param insertNop
	 *            should a "no operation" transaction be created if none other is created?
	 * @param repaint
	 *            should this end by repainting the screen?
	 * @param event
	 *            if non-null, a recent KeyPressed event
	 */
	private void keyboardAndRedraw(boolean insertNop, boolean repaint,
			KeyEvent event) {
		if (event != null) {
			int c = event.getKeyCode();
			char r = event.getKeyChar();
			if (r == 'w' || c == KeyEvent.VK_UP) {
				sendTransaction(GameDemoState.TRANS_NORTH);
				automove = false;
			} else if (r == 's' || c == KeyEvent.VK_DOWN) {
				sendTransaction(GameDemoState.TRANS_SOUTH);
				automove = false;
			} else if (r == 'd' || c == KeyEvent.VK_RIGHT) {
				sendTransaction(GameDemoState.TRANS_EAST);
				automove = false;
			} else if (r == 'a' || c == KeyEvent.VK_LEFT) {
				sendTransaction(GameDemoState.TRANS_WEST);
				automove = false;
			} else if (c == ' ') {
				automove = true;
			}
		}

		if (frame != null && repaint) {
			frame.repaint();
		}
	}

	/**
	 * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a
	 * particular SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle
	 * icon).
	 * 
	 * @param args
	 *            these are not used
	 */
	public static void main(String[] args) {
		Browser.main(null);
	}

	// ///////////////////////////////////////////////////////////////////

	@Override
	public void init(Platform platform, int id) {
		this.platform = platform;
		this.selfId = id;
		platform.setAbout("Game Demo v. 1.0\n");
		frame = platform.createWindow(false); // create the default size window, and be visible
		frame.addKeyListener(keyListener);
		frame.add(new Board());
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	public void run() {
		while (true) {
			int dx, dy;
			// get a reference to the state repeatedly, because it changes sometimes
			GameDemoState state = (GameDemoState) platform.getState();
			dx = state.getxGoal() - state.getxPlayer()[selfId];
			dy = state.getyGoal() - state.getyPlayer()[selfId];
			if (automove) { // automatically move one step toward the goal
				boolean v = (dx == 0) || (((dx + dy) % 2) == 0); // vertical move ok?

				if ((dy < 0) && v) {
					sendTransaction(GameDemoState.TRANS_NORTH);
				} else if ((dy > 0) && v) {
					sendTransaction(GameDemoState.TRANS_SOUTH);
				} else if (dx > 0) {
					sendTransaction(GameDemoState.TRANS_EAST);
				} else {
					sendTransaction(GameDemoState.TRANS_WEST);
				}
			}
			keyboardAndRedraw(false, true, null);
			try {
				Thread.sleep(gameLoopDelay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void preEvent() {
		keyboardAndRedraw(true, false, null);
	}

	@Override
	public SwirldState newState() {
		return new GameDemoState();
	}
}
