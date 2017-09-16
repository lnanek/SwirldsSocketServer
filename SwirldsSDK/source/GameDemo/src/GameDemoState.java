
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
import java.io.IOException;
import java.time.Instant;

import com.swirlds.platform.Address;
import com.swirlds.platform.AddressBook;
import com.swirlds.platform.FCDataInputStream;
import com.swirlds.platform.FCDataOutputStream;
import com.swirlds.platform.FastCopyable;
import com.swirlds.platform.Platform;
import com.swirlds.platform.RandomExtended;
import com.swirlds.platform.SwirldState;
import com.swirlds.platform.Utilities;

/**
 * The state for the game demo. See the comments for GameDemoMain
 */
public class GameDemoState implements SwirldState {
	/** code in transaction to move player one step north */
	public static final byte TRANS_NORTH = 0;
	/** code in transaction to move player one step south */
	public static final byte TRANS_SOUTH = 1;
	/** code in transaction to move player one step east */
	public static final byte TRANS_EAST = 2;
	/** code in transaction to move player one step west */
	public static final byte TRANS_WEST = 3;

	/** used to randomly move the target after a point is scored */
	private RandomExtended random;
	/** the names and addresses of all members */
	private AddressBook addressBook;
	/** width of the board, in cells */
	private int xBoardSize = 10;
	/** height of the board, in cells */
	private int yBoardSize = 20;
	/** current x coordinate of the goal */
	private int xGoal = 0;
	/** current y coordinate of the goal */
	private int yGoal = 0;
	/** sum of all players' scores */
	private int totalScore = 0;
	/** score for each player */
	private int score[];
	/** # transactions so far, per player */
	private int numTrans[];
	/** x coordinate of each player */
	private int xPlayer[];
	/** y coordinate of each player */
	private int yPlayer[];
	/** color of each player */
	private Color color[];
	/** color of the icon of the goal */
	private Color colorGoal;

	/** @return current board width, in cells */
	public synchronized int getxBoardSize() {
		return xBoardSize;
	}

	/** @return current board height, in cells */
	public synchronized int getyBoardSize() {
		return yBoardSize;
	}

	/** @return x coordinate of goal */
	public synchronized int getxGoal() {
		return xGoal;
	}

	/** @return y coordinate of goal */
	public synchronized int getyGoal() {
		return yGoal;
	}

	/** @return sum of all player scores */
	public synchronized int getTotalScore() {
		return totalScore;
	}

	/** @return score for each player */
	public synchronized int[] getScore() {
		return score;
	}

	/** @return number of transactions so far for each player */
	public synchronized int[] getNumTrans() {
		return numTrans;
	}

	/** @return x coordinate for each player */
	public synchronized int[] getxPlayer() {
		return xPlayer;
	}

	/** @return y coordinate for each player */
	public synchronized int[] getyPlayer() {
		return yPlayer;
	}

	/** @return color for each player */
	public synchronized Color[] getColor() {
		return color;
	}

	/** @return color of the goal */
	public synchronized Color getColorGoal() {
		return colorGoal;
	}

	/** @return the random number generator used to move the goal after each point is scored */
	public synchronized RandomExtended getRandom() {
		return random;
	}

	/**
	 * return a random color
	 * 
	 * @return the random color
	 */
	private Color randColor() {
		return Color.getHSBColor(random.nextFloat(),
				random.nextFloat() * .25f + .75f,
				random.nextFloat() * .25f + .75f);
	}

	// ///////////////////////////////////////////////////////////////////

	@Override
	public void init(Platform platform, AddressBook addressBook) {
		int numMembers = addressBook.getSize();

		random = new RandomExtended(0); // must seed with a constant, not the time
		this.addressBook = addressBook;
		String[] pars = platform.getParameters();
		if (pars.length >= 2) {
			xBoardSize = Integer.valueOf(pars[1].trim());
			yBoardSize = Integer.valueOf(pars[0].trim());
		}
		xGoal = random.nextInt(xBoardSize);
		yGoal = random.nextInt(yBoardSize);
		totalScore = 0;
		score = new int[numMembers];
		numTrans = new int[numMembers];
		xPlayer = new int[numMembers];
		yPlayer = new int[numMembers];
		color = new Color[numMembers];
		colorGoal = randColor();
		for (int i = 0; i < numMembers; i++) {
			xPlayer[i] = random.nextInt(xBoardSize);
			yPlayer[i] = random.nextInt(yBoardSize);
			color[i] = Color.getHSBColor((float) i / (numMembers + 1), 1, 1);
			score[i] = 0;
		}
	};

	@Override
	public AddressBook getAddressBookCopy() {
		return addressBook.copy();
	};

	@Override
	public void copyFrom(SwirldState oldGameDemoState) {
		GameDemoState old = (GameDemoState) oldGameDemoState;
		random = old.random.clone(); // RandomExtended is cloneable, unlike Random
		addressBook = old.addressBook;
		xBoardSize = old.xBoardSize;
		yBoardSize = old.yBoardSize;
		xGoal = old.xGoal;
		yGoal = old.yGoal;
		totalScore = old.totalScore;
		score = old.score.clone(); // array of primitives, so clone is ok
		numTrans = old.numTrans.clone();// array of immutable int, so clone is ok
		xPlayer = old.xPlayer.clone();// array of immutable int, so clone is ok
		yPlayer = old.yPlayer.clone();// array of immutable int, so clone is ok
		color = old.color.clone();// array of immutable Color, so clone is ok
		colorGoal = old.colorGoal;
	}

	@Override
	public void handleTransaction(long id, boolean isConsensus,
			Instant timeCreated, byte[] trans, Address address) {
		int mem = (int) id;

		// You can make the consensus latency visible by making
		// the goal jump around while it's deciding the consensus.
		// You can do that by making the random number generator
		// depend on the EXACT order of all the transactions.
		// Do do that, uncomment the following line:

		// random.absorbEntropy(mem + trans[0]);

		numTrans[mem]++; // remember how many transactions handled for each member

		// handle the 4 types of transactions
		switch (trans[0]) {
			case TRANS_NORTH:
				yPlayer[mem]--;
				break;
			case TRANS_SOUTH:
				yPlayer[mem]++;
				break;
			case TRANS_EAST:
				xPlayer[mem]++;
				break;
			case TRANS_WEST:
				xPlayer[mem]--;
				break;
		}

		// wrap around the board (it's a torus)
		yPlayer[mem] = ((yPlayer[mem] % yBoardSize) + yBoardSize) % yBoardSize;
		xPlayer[mem] = ((xPlayer[mem] % xBoardSize) + xBoardSize) % xBoardSize;

		// handle a point being scored
		if (xPlayer[mem] == xGoal && yPlayer[mem] == yGoal) {
			score[mem]++;              // the winner gets a point
			totalScore++;              // so the sum of everyon'es point increments
			color[mem] = colorGoal;    // the winner's color changes to match the goal
			xGoal = random.nextInt(xBoardSize); // goal jumps to random location, color
			yGoal = random.nextInt(yBoardSize);
			colorGoal = randColor();
		}
	}

	@Override
	public void noMoreTransactions() { // there aren't any threads to stop
	}

	@Override
	public FastCopyable copy() {
		GameDemoState copy = new GameDemoState();
		copy.copyFrom(this);
		return copy;
	}

	@Override
	public void copyTo(FCDataOutputStream outStream) {
		try {
			random.copyTo(outStream);
			addressBook.copyTo(outStream);
			outStream.writeInt(xBoardSize);
			outStream.writeInt(yBoardSize);
			outStream.writeInt(xGoal);
			outStream.writeInt(yGoal);
			outStream.writeInt(totalScore);
			Utilities.writeIntArray(outStream, score);
			Utilities.writeIntArray(outStream, numTrans);
			Utilities.writeIntArray(outStream, xPlayer);
			Utilities.writeIntArray(outStream, yPlayer);
			Utilities.writeColorArray(outStream, color);
			Utilities.writeColor(outStream, colorGoal);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void copyFrom(FCDataInputStream inStream) {
		try {
			random.copyFrom(inStream);
			addressBook.copyFrom(inStream);
			xBoardSize = inStream.readInt();
			yBoardSize = inStream.readInt();
			xGoal = inStream.readInt();
			yGoal = inStream.readInt();
			totalScore = inStream.readInt();
			score = Utilities.readIntArray(inStream);
			numTrans = Utilities.readIntArray(inStream);
			xPlayer = Utilities.readIntArray(inStream);
			yPlayer = Utilities.readIntArray(inStream);
			color = Utilities.readColorArray(inStream);
			colorGoal = Utilities.readColor(inStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
