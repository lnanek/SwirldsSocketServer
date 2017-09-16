
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import com.swirlds.platform.Browser;
import com.swirlds.platform.Console;
import com.swirlds.platform.Platform;
import com.swirlds.platform.Statistics;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This demo collects statistics on the running of the network and consensus systems. It writes them to the
 * screen, and also saves them to disk in a comma separated value (.csv) file. Each transaction is 100
 * random bytes. So StatsDemoState.handleTransaction doesn't actually do anything.
 */
public class StatsDemoMain implements SwirldMain {
	// the first four come from the parameters in the config.txt file

	/** should this run with no windows? */
	private boolean headless = false;
	/** number of milliseconds between writes to the log file */
	private int writePeriod = 3000;
	/** bytes in each transaction */
	private int bytesPerTrans = 1;
	/** transactions in each Event */
	private int transPerEvent = 1;

	/** path and filename of the .csv file to write to */
	private String path;
	/** ID number for this member */
	private int selfId;
	/** the app is run by this */
	private Platform platform;
	/** a console window for text output */
	private Console console = null;
	/** used to make the transactions random, so they won't cheat and shrink when zipped */
	private Random random = new java.util.Random();

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

	/**
	 * Write a message to the log file. Also write it to the console, if there is one. In both cases, skip a
	 * line after writing, if newline is true. This method opens the file at the start and closes it at the
	 * end, to deconflict with any other process trying to read the same file. For example, this app could
	 * run headless on a server, and an FTP session could download the log file, and the file it received
	 * would have only complete log messages, never half a message.
	 * <p>
	 * The file is created if it doesn't exist. It will be named "StatsDemo0.csv", with the number
	 * incrementing for each member currently running on the local machine, if there is more than one. The
	 * location is the "current" directory. If run from a shell script, it will be the current folder that
	 * the shell script has. If run from Eclipse, it will be at the top of the project folder. If there is a
	 * console, it prints the location there. If not, it can be found by searching the file system for
	 * "StatsDemo0.csv".
	 * 
	 * @param message
	 *            the String to write
	 * @param newline
	 *            true if a new line should be started after this one
	 */
	private void write(String message, boolean newline) {
		BufferedWriter file = null;
		try {// create or append to file in current directory
			path = System.getProperty("user.dir") + File.separator + "StatsDemo"
					+ selfId + ".csv";
			file = new BufferedWriter(new FileWriter(path, true));
			if (newline) {
				file.write("\n");
			} else {
				file.write(message.trim().replaceAll(",", "") + ",");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
		}
		if (console != null) {
			console.out.print(newline ? "\n" : message);
		}
	}

	/** Erase the existing file (if one exists) */
	private void eraseFile() {
		BufferedWriter file = null;
		try {// erase file in current directory
			path = System.getProperty("user.dir") + File.separator + "StatsDemo"
					+ selfId + ".csv";
			file = new BufferedWriter(new FileWriter(path, false));
			file.write("");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * Same as writeToConsolAndFile, except it does not start a new line after it.
	 * 
	 * @param message
	 *            the String to write
	 */
	private void write(String message) {
		write(message, false);
	}

	/** Start the next line, for both console and file. */
	private void newline() {
		write("", true);
	}

	// ///////////////////////////////////////////////////////////////////

	@Override
	public void preEvent() {
		byte[] transaction = new byte[bytesPerTrans];
		for (int i = 0; i < transPerEvent; i++) {
			random.nextBytes(transaction); // make it non-compressible
			platform.createTransaction(transaction, null);
		}
	}

	@Override
	public void init(Platform platform, int id) {
		long syncDelay;
		this.platform = platform;
		String[] pars = platform.getParameters();
		selfId = id;
		headless = (pars[0].equals("1"));
		writePeriod = Integer.parseInt(pars[1]);
		syncDelay = Integer.parseInt(pars[2]);
		bytesPerTrans = Integer.parseInt(pars[3]);
		transPerEvent = Integer.parseInt(pars[4]);
		if (!headless) { // create the window, make it visible
			console = platform.createConsole(true);
		}
		platform.setAbout( // set the browser's "about" box
				"Stats Demo v. 1.1\nThis writes statistics to a log file,"
						+ " such as the number of transactions per second.");
		platform.setSleepAfterSync(syncDelay);
		platform.getStats().setStatsWritePeriod(writePeriod);
	}

	@Override
	public void run() {
		Statistics statsObj = platform.getStats();
		String[][] stats = statsObj.getAvailableStats();

		// erase the old file, if any
		eraseFile();

		// set the heading at the top of the console
		if (console != null) {
			String str = "";
			for (int i = 0; i < stats.length; i++) {
				str += String.format(
						"%" + statsObj.getStatString(i).length() + "s",
						stats[i][0]);
			}
			console.setHeading(str + "\n");
		}

		// write the definitions at the top (name is stats[i][0], description is stats[i][1])
		write(String.format("%14s: ", "filename"));
		write(String.format("%s", path));
		newline();
		for (int i = 0; i < stats.length; i++) {
			write(String.format("%14s: ", stats[i][0]));
			write(String.format("%s", stats[i][1]));
			newline();
		}
		newline();

		// write the column headings again
		write("");// indent by two columns
		write("");
		for (int i = 0; i < stats.length; i++) {
			write(String.format("%" + statsObj.getStatString(i).length() + "s",
					stats[i][0]));
		}
		newline();

		while (true) { // keep logging forever
			try {
				// write a row of numbers
				write("");
				write("");
				for (int i = 0; i < stats.length; i++) {
					write(statsObj.getStatString(i));
				}
				newline();
				Thread.sleep(writePeriod); // add new rows infrequently
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public SwirldState newState() {
		return new StatsDemoState();
	}
}