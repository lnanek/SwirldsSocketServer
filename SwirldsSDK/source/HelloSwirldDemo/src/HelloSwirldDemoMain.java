
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
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

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

	private class HelloWorld extends AbstractHandler {
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {

			String requestBody = request.getReader().lines().collect(Collectors.joining());
			console.out.println("Finished reading from socket");
			
			requestBody = requestBody.replaceAll("\\n", "");
			requestBody = requestBody.replaceAll("\\r", "");
			byte[] transaction = requestBody.getBytes(StandardCharsets.UTF_8);
			platform.createTransaction(transaction, null);
			console.out.println("Wrote to hashgraph: " + requestBody);

			response.setContentType("text/plain;charset=utf-8");
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			HelloSwirldDemoState state = (HelloSwirldDemoState) platform.getState();
			console.out.println("Writing hashgraph to HTTP response...");
			for (String hashgraphMessage : state.getStrings()) {
				response.getWriter().println(hashgraphMessage);
				response.getWriter().flush();
				console.out.println("Wrote hashgraph message to socket: " + hashgraphMessage);
			}
			console.out.println("Writing hashgraph to HTTP response...");
		}
	}

	@Override
	public void run() {

		console.out.println("Lance was here");

		try {
			Server server = new Server(PORT);
			server.setHandler(new HelloWorld());
			server.start();
			server.join();
		} catch (Exception e) {
			console.out.println("Error running jetty: " + e);
		}

		// startServer();

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