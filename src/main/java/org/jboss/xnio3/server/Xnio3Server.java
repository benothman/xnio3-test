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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

	private static final Logger logger = Logger.getLogger(Xnio3Server.class.getName());
	private static final BufferPool WRITE_BUFFER_POOL = BufferPool.create();
	private static final BufferPool READ_BUFFER_POOL = BufferPool.create(512);
	/**
	 * 
	 */
	public static final String CRLF = "\r\n";
	/**
	 * The default server port
	 */
	public static final int SERVER_PORT = 8080;

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

		logger.infov("Starting XNIO3 Server on port {0} ...", port);
		// Get the Xnio instance
		final Xnio xnio = Xnio.getInstance("nio", Xnio3Server.class.getClassLoader());

		int cores = Runtime.getRuntime().availableProcessors();
		logger.infof("Number of cores detected %s", cores);

		// Create the OptionMap for the worker
		OptionMap optionMap = OptionMap.create(Options.WORKER_WRITE_THREADS, cores,
				Options.WORKER_READ_THREADS, cores);
		// Create the worker
		final XnioWorker worker = xnio.createWorker(optionMap);
		final SocketAddress address = new InetSocketAddress(port);
		final ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptListener = ChannelListeners
				.openListenerAdapter(new AcceptChannelListenerImpl());
		// configure the number of worker task max threads
		worker.setOption(Options.WORKER_TASK_MAX_THREADS, 400);

		final AcceptingChannel<? extends ConnectedStreamChannel> server = worker
				.createStreamServer(address, acceptListener,
						OptionMap.create(Options.REUSE_ADDRESSES, Boolean.TRUE));
		server.resumeAccepts();
	}

	/**
	 * Generate a random and unique session ID.
	 * 
	 * @return a random and unique session ID
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
	 * {@code AcceptChannelListenerImpl}
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
			logger.info("Closing remote connection for session: [" + sessionId + "]");
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

		private String sessionId;

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel )
		 */
		public void handleEvent(StreamChannel channel) {
			try {
				// Peek a byte buffer from the READ_BUFFER_POOL
				ByteBuffer byteBuffer = READ_BUFFER_POOL.peek();
				int nBytes = channel.read(byteBuffer);
				if (nBytes < 0) {
					// means that the connection was closed remotely
					channel.close();
					return;
				}

				if (nBytes > 0) {
					byteBuffer.flip();
					byte bytes[] = new byte[nBytes];
					byteBuffer.get(bytes);
					byteBuffer.clear();
					System.out.println("[" + this.sessionId + "] " + new String(bytes).trim());
					// Restitute the read byte buffer
					READ_BUFFER_POOL.restitute(byteBuffer);
					writeResponse(channel);

					/*
					 * String response = "[" + this.sessionId +
					 * "] Pong from server\n";
					 * byteBuffer.put(response.getBytes()); byteBuffer.flip();
					 * // Wait until the channel becomes writable again
					 * channel.awaitWritable(); channel.write(byteBuffer);
					 */
				} else {
					READ_BUFFER_POOL.restitute(byteBuffer);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * 
		 * @param channel
		 * @throws Exception
		 */
		void writeResponse(StreamChannel channel) throws Exception {

			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(
					"file.txt")));

			ByteBuffer buffer = WRITE_BUFFER_POOL.peek();
			String line = null;
			int off = 0;
			int remain = 0;
			while ((line = in.readLine()) != null) {
				int length = line.length();

				if (buffer.remaining() >= length) {
					buffer.put(line.getBytes());
				} else {
					off = buffer.remaining();
					remain = length - off;
					buffer.put(line.getBytes(), 0, off);
				}
				// write data to the channel when the buffer is full
				if (!buffer.hasRemaining()) {
					write(channel, buffer);
					buffer.put(line.getBytes(), off, remain);
					remain = 0;
				}
			}
			in.close();
			// If still some data to write
			if (buffer.remaining() < buffer.capacity()) {
				write(channel, buffer);
			}
			// write the CRLF characters
			buffer.put(CRLF.getBytes());
			write(channel, buffer);
			WRITE_BUFFER_POOL.restitute(buffer);
		}

		/**
		 * 
		 * @param channel
		 * @param buffer
		 * @throws IOException
		 */
		void write(StreamChannel channel, ByteBuffer byteBuffer) throws IOException {
			byteBuffer.flip();
			// Wait until the channel becomes writable again
			channel.awaitWritable();
			channel.write(byteBuffer);
			byteBuffer.clear();
		}
	}
}
