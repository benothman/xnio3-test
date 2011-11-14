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
package org.jboss.xnio3.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jboss.logging.Logger;
import org.xnio.channels.StreamChannel;

/**
 * {@code Xnio3ClientManager}
 * 
 * Created on Nov 10, 2011 at 4:25:31 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Xnio3ClientManager implements Runnable {

	private StreamChannel channel;
	private static final Logger logger = Logger.getLogger(Xnio3ClientManager.class.getName());
	private String sessionId;

	/**
	 * Create a new instance of {@code Xnio3ClientManager}
	 * 
	 * @param channel
	 */
	public Xnio3ClientManager(StreamChannel channel) {
		this.channel = channel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		ByteBuffer bb = ByteBuffer.allocate(1024);
		String response = null;
		try {
			// Initialization of the communication
			bb.clear();
			int nBytes = channel.read(bb);
			bb.flip();
			byte bytes[] = new byte[nBytes];
			bb.get(bytes);
			System.out.println("[" + this.sessionId + "] " + new String(bytes).trim());
			response = "jSessionId: " + this.sessionId + "\n";
			// write initialization response to client
			this.write(bb, response);
			bb.clear();
			do {
				nBytes = channel.read(bb);
				if (nBytes > 0) {
					bb.flip();
					bytes = new byte[nBytes];
					bb.get(bytes);
					response = "[" + this.sessionId + "] Pong from server\n";
					System.out.println("[" + this.sessionId + "] " + new String(bytes).trim());
					// write response to client
					this.write(bb, response);
					bb.clear();
				}
			} while (channel.isOpen());

		} catch (Exception exp) {
			logger.error("ERROR from client side");
		} finally {
			try {
				this.close();
			} catch (IOException ex) {
				logger.error("ERROR from server side", ex);
			}
		}
		logger.infov("Client Manager shutdown");
	}

	/**
	 * 
	 * @param bb
	 * @param response
	 * @throws IOException
	 */
	protected int write(ByteBuffer bb, String response) throws IOException {
		bb.clear();
		bb.put(response.getBytes());
		bb.flip();
		return channel.write(bb);
	}

	/**
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		logger.infov("Closing remote connection");
		this.channel.close();
	}

	/**
	 * Getter for sessionId
	 * 
	 * @return the sessionId
	 */
	public String getSessionId() {
		return this.sessionId;
	}

	/**
	 * Setter for the sessionId
	 * 
	 * @param sessionId
	 *            the sessionId to set
	 */
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

}
