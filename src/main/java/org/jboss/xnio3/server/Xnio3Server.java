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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
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
	private static final int BUFFER_SIZE = 8 * 1024;
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
		String response = "jSessionId: " + sessionId + CRLF;
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
			WriteChannelListener writeListener = new WriteChannelListener();

			streamChannel.getReadSetter().set(readListener);
			streamChannel.getWriteSetter().set(writeListener);
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

			ByteBuffer byteBuffer = ByteBuffer.allocate(512);
			try {
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
					writeResponse(channel);

					/*
					 * String response = "[" + this.sessionId +
					 * "] Pong from server\n";
					 * byteBuffer.put(response.getBytes()); byteBuffer.flip();
					 * // Wait until the channel becomes writable again
					 * channel.awaitWritable(); channel.write(byteBuffer);
					 */
				}
			} catch (Exception e) {
				logger.error("Exception: " + e.getMessage(), e);
				e.printStackTrace();
			}
		}

		/**
		 * 
		 * @param channel
		 * @throws Exception
		 */
		void writeResponse(StreamChannel channel) throws Exception {
			File file = new File("data" + File.separatorChar + "file.txt");

			/*
			 * Path path =
			 * FileSystems.getDefault().getPath(file.getAbsolutePath());
			 * SeekableByteChannel sbc = null; ByteBuffer writeBuffer =
			 * ByteBuffer.allocate(BUFFER_SIZE); try { sbc =
			 * Files.newByteChannel(path, StandardOpenOption.READ); // Read from
			 * file and write to the asynchronous socket channel while
			 * (sbc.read(writeBuffer) > 0) { write(channel, writeBuffer); } //
			 * write the CRLF characters writeBuffer.put(CRLF.getBytes());
			 * write(channel, writeBuffer); } catch (Exception exp) {
			 * logger.error("Exception: " + exp.getMessage(), exp);
			 * exp.printStackTrace(); } finally { if (sbc != null) {
			 * sbc.close(); } }
			 */

			RandomAccessFile raf = new RandomAccessFile(file, "r");
			FileChannel fileChannel = raf.getChannel();

			try {
				long fileLength = fileChannel.size() + CRLF.getBytes().length;
				double tmp = (double) fileLength / BUFFER_SIZE;
				int length = (int) Math.ceil(tmp);
				ByteBuffer buffers[] = new ByteBuffer[length];

				for (int i = 0; i < buffers.length - 1; i++) {
					buffers[i] = ByteBuffer.allocate(BUFFER_SIZE);
				}

				int temp = (int) (fileLength % BUFFER_SIZE);
				buffers[buffers.length - 1] = ByteBuffer.allocate(temp);
				// Read the whole file in one pass
				fileChannel.read(buffers);

				buffers[buffers.length - 1].put(CRLF.getBytes());
				// Write the file content to the channel
				write(channel, buffers, fileLength);
			} catch (Exception exp) {
				logger.error("Exception: " + exp.getMessage(), exp);
				exp.printStackTrace();
			} finally {
				fileChannel.close();
				raf.close();
			}
		}

		/**
		 * 
		 * @param channel
		 * @param buffers
		 * @throws Exception
		 */
		protected void write(final StreamChannel channel, final ByteBuffer[] buffers, long total)
				throws Exception {

			for (int i = 0; i < buffers.length; i++) {
				buffers[i].flip();
			}
			WriteChannelListener writeListener = (WriteChannelListener)channel.getWriteSetter();
			writeListener.reset();
			writeListener.total = total;
			writeListener.buffers = buffers;
			long written = channel.write(buffers);
			writeListener.addWritten(written);
			System.out.println("Number of bytes written : " + written);
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

	/**
	 * {@code WriteChannelListener}
	 * 
	 * Created on Nov 21, 2011 at 4:01:22 PM
	 * 
	 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
	 */
	protected static class WriteChannelListener implements ChannelListener<StreamChannel> {

		private int offset = 0;
		private long written = 0;
		private ByteBuffer buffers[];
		private long total = 0;
		
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel)
		 */
		@Override
		public void handleEvent(StreamChannel channel) {
			if(this.written < this.total) {
				this.offset = (int)(this.written / BUFFER_SIZE);				
				try {
					long nBytes = channel.write(buffers, offset, buffers.length - offset);
					this.written += nBytes;
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
		}

		/**
		 * Reset the write handler counters
		 */
		public void reset() {
			this.total = 0;
			this.offset = 0;
			this.written = 0;
			this.buffers = null;
		}

		/**
		 * 
		 * @param nBytes
		 */
		public void addWritten(long nBytes) {
			this.written += nBytes;
		}
		
		/**
		 * Getter for buffers
		 * 
		 * @return the buffers
		 */
		public ByteBuffer[] getBuffers() {
			return this.buffers;
		}

		/**
		 * Setter for the buffers
		 * 
		 * @param buffers
		 *            the buffers to set
		 */
		public void setBuffers(ByteBuffer[] buffers) {
			this.buffers = buffers;
		}
	}
}
