
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.swirlds.platform.Browser;
import com.swirlds.platform.Console;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This HelloSwirld creates a single transaction, consisting of the string
 * "Hello Swirld", and then goes into a busy loop (checking once a second) to
 * see when the state gets the transaction. When it does, it prints it, too.
 */
public class HelloSwirldDemoMain implements SwirldMain {
	/** the platform running this app */
	public Platform platform;
	/** ID number for this member */
	public int selfId;
	/** a console window for text output */
	public Console console;
	/** sleep this many milliseconds after each sync */
	public final int sleepPeriod = 100;

	/**
	 * This is just for debugging: it allows the app to run in Eclipse. If the
	 * config.txt exists and lists a particular SwirldMain class as the one to run,
	 * then it can run in Eclipse (with the green triangle icon).
	 * 
	 * @param args
	 *            these are not used
	 */
	public static void main(String[] args) {
		Browser.main(null);
	}

	// ///////////////////////////////////////////////////////////////////

	@Override
	public void preEvent() {
	}

	@Override
	public void init(Platform platform, int id) {
		this.platform = platform;
		this.selfId = id;
		this.console = platform.createConsole(true); // create the window, make it visible
		platform.setAbout("Hello Swirld v. 1.0\n"); // set the browser's "about" box
		platform.setSleepAfterSync(sleepPeriod);
	}

	public static final int PORT = 9111;

	public void startServer() {
		ServerSocket serverSocket = null;
		try {

			// Wait for someone to connect a socket to us
			console.out.println("Listening on port " + PORT + "...");
			serverSocket = new ServerSocket(PORT);

			while (true) {
				// start a new thread for each accepted socket
				Socket socket = serverSocket.accept();
				console.out.println("Server socket accepted connection...");

				new Thread() {
					@Override
					public void run() {
						serviceClientConnection(socket);
					}
				}.start();
				
			}

		} catch (IOException e) {
			console.out.println("Error listening: " + e);
		} finally {
			if (serverSocket != null) {
				try {
				serverSocket.close();
				} catch(Exception e) {
					// Ignore
				}
			}
		}
	}

	private void serviceClientConnection(Socket socket) {

		try {

			console.out.println("Servicing client on background thread...");

			// Write a message to them
			PrintWriter printWriter = new PrintWriter(socket.getOutputStream());
			HelloSwirldDemoState state = (HelloSwirldDemoState) platform.getState();
			//printWriter.write("HTTP/1.1 200 OK\r\nCache-Control: no-cache, private\r\nTransfer-Encoding: compress\r\nDate: Mon, 24 Nov 2014 10:21:21 GMT\r\n\r\n");
			
			printWriter.write("HTTP/1.1 200 OK\r\n");
			printWriter.write("Date: Mon, 27 Jul 2009 12:28:53 GMT\r\n");
			printWriter.write("Server: Apache/2.2.14 (Win32)\r\n");
			printWriter.write("Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT\r\n");
			printWriter.write("Transfer-Encoding: identity\r\n");
			printWriter.write("Content-Type: text/plain\r\n");
			printWriter.write("Connection: Closed\r\n\r\n");
			
			for (String hashgraphMessage : state.getStrings()) {
				printWriter.write(hashgraphMessage + "\n");
				printWriter.flush();
				console.out.println("Wrote hashgraph message to socket: " + hashgraphMessage);
			}
			console.out.println("Finished writing to socket");
			// printWriter.write("Hello user!\n");
			// console.out.println("Wrote Hello user!\n");

			// Read the message from the socket
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String readLine;
			while ((readLine = bufferedReader.readLine()) != null) {
				console.out.println("Read from socket: " + readLine);
				// Put transaction with line that was written to our socket on the hashgraph
				byte[] transaction = readLine.getBytes(StandardCharsets.UTF_8);
				platform.createTransaction(transaction, null);
				console.out.println("Wrote to hashgraph: " + readLine);
			}
			console.out.println("Finished reading from socket");
			// socket.close();

			// Close socket
			bufferedReader.close();
			printWriter.close();
			socket.close();

		} catch (IOException e) {
			console.out.println("Error listening: " + e);
		}
	}

	@Override
	public void run() {

		console.out.println("Lance was here");

		startServer();

		String myName = platform.getState().getAddressBookCopy().getAddress(selfId).getSelfName();

		console.out.println("Hello Swirld from " + myName);

		// create a transaction. For this example app,
		// we will define each transactions to simply
		// be a string in UTF-8 encoding.
		byte[] transaction = myName.getBytes(StandardCharsets.UTF_8);

		// Send the transaction to the Platform, which will then
		// forward it to the State object.
		// The Platform will also send the transaction to
		// all the other members of the community during syncs with them.
		// The community as a whole will decide the order of the transactions
		platform.createTransaction(transaction, null);
		String lastReceived = "";

		while (true) {
			HelloSwirldDemoState state = (HelloSwirldDemoState) platform.getState();
			String received = state.getReceived();

			if (!lastReceived.equals(received)) {
				lastReceived = received;
				console.out.println("Received: " + received); // print all received transactions
			}
			try {
				Thread.sleep(sleepPeriod);
			} catch (Exception e) {
			}
		}
	}

	@Override
	public SwirldState newState() {
		return new HelloSwirldDemoState();
	}
}