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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.UUID;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamChannel;

/**
 * {@code Xnio3Server}
 * 
 * Created on Nov 10, 2011 at 3:41:02 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Xnio3Server {

	protected static final int SERVER_PORT = 8080;
	private static final Logger logger = Logger.getLogger(Xnio3Server.class.getName());

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		int port = SERVER_PORT;
		if (args.length > 0) {
			try {
				port = Integer.valueOf(args[0]);
			} catch (NumberFormatException e) {
				logger.error(e.getMessage(), e);
			}
		}

		logger.infov("Starting NIO2 Synchronous Sever on port {0} ...", port);
		// Get the Xnio instance
		final Xnio xnio = Xnio.getInstance("nio", Xnio3Server.class.getClassLoader());
		// Create the OptionMap for the worker
		OptionMap optionMap = OptionMap.create(Options.WORKER_WRITE_THREADS, 200,
				Options.WORKER_READ_THREADS, 200);
		// Create the worker
		final XnioWorker worker = xnio.createWorker(optionMap);
		final SocketAddress address = new InetSocketAddress(port);
		final ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener = ChannelListeners
				.openListenerAdapter(new AcceptChannelListenerImpl());

		worker.setOption(Options.WORKER_TASK_MAX_THREADS, 400);
		
		final AcceptingChannel<? extends ConnectedStreamChannel> server = worker
				.createStreamServer(address, acceptListener,
						OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));
		server.resumeAccepts();
	}

	/**
	 * 
	 * @return
	 */
	public static String generateSessionId() {
		UUID uuid = UUID.randomUUID();
		return uuid.toString();
	}

	/**
	 * 
	 * @param channel
	 * @param sessionId
	 * @throws IOException
	 * @throws Exception
	 */
	protected static void initSession(StreamChannel channel, String sessionId) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(512);
		buffer.clear();
		int nBytes = channel.read(buffer);
		buffer.flip();
		byte bytes[] = new byte[nBytes];
		buffer.get(bytes);
		System.out.println("[" + sessionId + "] " + new String(bytes).trim());
		String response = "jSessionId: " + sessionId + "\n";
		// write initialization response to client
		buffer.clear();
		buffer.put(response.getBytes());
		buffer.flip();
		channel.write(buffer);
	}

	/**
	 * {@code ChannelListenerImpl}
	 * 
	 * Created on Nov 10, 2011 at 4:03:10 PM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	protected static class AcceptChannelListenerImpl implements ChannelListener<Channel> {

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel)
		 */
		public void handleEvent(Channel channel) {
			logger.info("New connection accepted");
			final StreamChannel streamChannel = (StreamChannel) channel;
			String sessionId = generateSessionId();
			try {
				initSession(streamChannel, sessionId);
				logger.info("Session intialization finish successfully");
			} catch (IOException e) {
				e.printStackTrace();
				logger.error("ERROR: Session initialization failed", e);
				return;
			}

			ReadChannelListener readListener = new ReadChannelListener();
			readListener.sessionId = sessionId;
			CloseChannelListener closeListener = new CloseChannelListener();
			closeListener.sessionId = sessionId;

			streamChannel.getReadSetter().set(readListener);
			streamChannel.getCloseSetter().set(closeListener);
			streamChannel.resumeReads();
		}
	}

	/**
	 * {@code CloseChannelListener}
	 * 
	 * Created on Nov 11, 2011 at 1:58:40 PM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	protected static class CloseChannelListener implements ChannelListener<StreamChannel> {

		private String sessionId;

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel)
		 */
		public void handleEvent(StreamChannel channel) {
			try {
				logger.info("Closing remote connection for session: [" + sessionId + "]");
				channel.suspendReads();
				channel.suspendWrites();
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * {@code ReadChannelListener}
	 * 
	 * Created on Nov 11, 2011 at 1:58:45 PM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	protected static class ReadChannelListener implements ChannelListener<StreamChannel> {

		private ByteBuffer byteBuffer = ByteBuffer.allocate(512);
		private String sessionId;

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel )
		 */
		public void handleEvent(StreamChannel channel) {
			try {
				int nBytes = channel.read(byteBuffer);
				if (nBytes < 0) {
					// means that the connection was closed remotely
					channel.close();
					return;
				}

				if (nBytes > 0) {
					this.byteBuffer.flip();
					byte bytes[] = new byte[nBytes];
					this.byteBuffer.get(bytes);
					this.byteBuffer.clear();
					System.out.println("[" + this.sessionId + "] " + new String(bytes).trim());
					String response = "[" + this.sessionId + "] Pong from server\n";
					this.byteBuffer.put(response.getBytes());
					byteBuffer.flip();
					channel.write(byteBuffer);
					this.byteBuffer.clear();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}
