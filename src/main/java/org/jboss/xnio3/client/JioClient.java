/**
 * JBoss, Home of Professional Open Source. Copyright 2011, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.jboss.xnio3.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Random;

/**
 * {@code JioClient}
 * 
 * Created on Nov 11, 2011 at 3:38:26 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class JioClient extends Thread {

	/**
	 * 
	 */
	public static final int READ_BUFFER_SIZE = 16 * 1024;
	/**
	 * 
	 */
	public static final String CRLF = "\r\n";
	/**
	 * 
	 */
	public static final int MAX = 1000;
	/**
	 * 
	 */
	public static final int N_THREADS = 100;
	/**
	 * Default wait delay 1000ms
	 */
	public static final int DEFAULT_DELAY = 1000;
	private long max_time = Long.MIN_VALUE;
	private long min_time = Long.MAX_VALUE;
	private double avg_time = 0;
	private String hostname;
	private int port;
	private int max;
	private int delay;
	private Socket channel;
	private String sessionId;
	private DataOutputStream dos;
	private InputStream is;

	/**
	 * Create a new instance of {@code JioClient}
	 * 
	 * @param hostname
	 * @param port
	 * @param d_max
	 * @param delay
	 */
	public JioClient(String hostname, int port, int d_max, int delay) {
		this.hostname = hostname;
		this.port = port;
		this.max = d_max;
		this.delay = delay;
	}

	/**
	 * Create a new instance of {@code JioClient}
	 * 
	 * @param hostname
	 * @param port
	 * @param delay
	 */
	public JioClient(String hostname, int port, int delay) {
		this(hostname, port, 55 * 1000 / delay, delay);
	}

	@Override
	public void run() {
		try {
			long total_running_time = System.currentTimeMillis();
			// Initialize the communication between client and server
			init();
			// wait for 2 seconds until all threads are ready
			sleep(2 * DEFAULT_DELAY);
			runit();
			total_running_time = System.currentTimeMillis() - total_running_time;
			System.out.println("[Thread-" + getId() + "] Total running time: " + total_running_time
					+ " ms");
		} catch (Exception exp) {
			System.err.println("Exception: " + exp.getMessage());
			exp.printStackTrace();
		} finally {
			System.out.println("[Thread-" + getId() + "] terminated -> "
					+ System.currentTimeMillis());
			try {
				this.channel.close();
			} catch (IOException ioex) {
				System.err.println("Exception: " + ioex.getMessage());
				ioex.printStackTrace();
			}
		}
	}

	/**
	 * 
	 * @throws Exception
	 */
	protected void connect() throws Exception {
		// Open connection with server
		System.out.println("Connecting to server on " + this.hostname + ":" + this.port);
		this.channel = new Socket(this.hostname, this.port);
		this.dos = new DataOutputStream(this.channel.getOutputStream());
		this.is = this.channel.getInputStream();
		System.out.println("Connection to server established ...");
	}

	/**
	 * 
	 * @throws Exception
	 */
	protected void init() throws Exception {
		// Connect to the server
		this.connect();
		System.out.println("Initializing communication...");
		write("POST /session-" + getId() + CRLF);
		BufferedReader in = new BufferedReader(new InputStreamReader(this.channel.getInputStream()));
		String response = in.readLine();
		String tab[] = response.split("\\s+");
		this.sessionId = tab[1];
		System.out.println("Communication intialized -> Session ID:" + this.sessionId);
	}

	/**
	 * 
	 * @throws Exception
	 */
	public void runit() throws Exception {
		Random random = new Random();
		// Wait a delay to ensure that all threads are ready
		sleep(DEFAULT_DELAY + random.nextInt(500));
		long time = 0;
		String response = null;
		int counter = 0;
		int min_count = 10 * 1000 / delay;
		int max_count = 50 * 1000 / delay;
		while ((this.max--) > 0) {
			sleep(this.delay);
			time = System.currentTimeMillis();
			write("GET /data/file.txt" + CRLF);
			response = read();
			time = System.currentTimeMillis() - time;
			// System.out.println("[Thread-" + getId() +
			// "] Received from server -> " + response);

			if (counter >= min_count && counter <= max_count) {
				// update the average response time
				avg_time += time;
				// update the maximum response time
				if (time > max_time) {
					max_time = time;
				}
				// update the minimum response time
				if (time < min_time) {
					min_time = time;
				}
			}
			counter++;
		}
		avg_time /= (max_count - min_count + 1);
		// For each thread print out the maximum, minimum and average response
		// times
		System.out.println(max_time + " \t " + min_time + " \t " + avg_time);
	}

	/**
	 * 
	 * @param data
	 * @throws Exception
	 */
	public void write(String data) throws Exception {
		this.dos.write(data.getBytes());
		this.dos.flush();
	}

	/**
	 * 
	 * @return data received from server
	 * @throws Exception
	 */
	public String read() throws Exception {
		byte bytes[] = new byte[READ_BUFFER_SIZE];
		int nBytes = -1;
		String tmp = null;
		int length = CRLF.getBytes().length;
		while ((nBytes = this.is.read(bytes)) != -1) {
			tmp = new String(bytes, nBytes - length, length);
			if (tmp.equals(CRLF)) {
				System.out.println("\n**** CRLF attemped ****");
				break;
			}
		}
		return "Hello world!";
	}

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		if (args.length < 2) {
			System.err.println("Usage: java " + JioClient.class.getName()
					+ " hostname port [n] [delay]");
			System.err.println("\thostname: The server IP/hostname.");
			System.err.println("\tport: The server port number.");
			System.err.println("\tn: The number of threads. (default is 100)");
			System.err.println("\tdelay: The delay between requests. (default is 1000ms)");
			System.exit(1);
		}

		String hostname = args[0];
		int port = Integer.parseInt(args[1]);
		int n = 100, delay = DEFAULT_DELAY;
		if (args.length > 2) {
			try {
				n = Integer.parseInt(args[2]);
				if (n < 1) {
					throw new IllegalArgumentException(
							"Number of threads may not be less than zero");
				}

				if (args.length > 3) {
					delay = Integer.parseInt(args[3]);
					if (delay < 1) {
						throw new IllegalArgumentException("Negative number: delay");
					}
				}
			} catch (Exception exp) {
				System.err.println("Error: " + exp.getMessage());
				System.exit(1);
			}
		}

		System.out.println("\nRunning test with parameters:");
		System.out.println("\tHostname: " + hostname);
		System.out.println("\tPort: " + port);
		System.out.println("\tn: " + n);
		System.out.println("\tdelay: " + delay);

		JioClient clients[] = new JioClient[n];

		for (int i = 0; i < clients.length; i++) {
			clients[i] = new JioClient(hostname, port, delay);
		}

		for (int i = 0; i < clients.length; i++) {
			clients[i].start();
		}

		for (int i = 0; i < clients.length; i++) {
			clients[i].join();
		}
	}
}
