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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.logging.Logger;
import org.xnio.ChannelListener;
import org.xnio.channels.StreamChannel;

/**
 * {@code ReadChannelListener}
 * 
 * Created on Nov 22, 2011 at 4:44:01 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class ReadChannelListener implements ChannelListener<StreamChannel> {

	private static final Logger logger = Logger.getLogger(ChannelListener.class.getName());
	private String sessionId;
	private ByteBuffer readBuffer;
	private ByteBuffer writeBuffers[];
	private ByteBuffer writeBuffer;
	private long fileLength;

	/**
	 * Create a new instance of {@code ReadChannelListener}
	 */
	public ReadChannelListener() {
		this.readBuffer = ByteBuffer.allocate(512);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel )
	 */
	public void handleEvent(StreamChannel channel) {
		try {
			int nBytes = channel.read(readBuffer);
			if (nBytes < 0) {
				// means that the connection was closed remotely
				channel.close();
				return;
			}

			if (nBytes > 0) {
				readBuffer.flip();
				byte bytes[] = new byte[nBytes];
				readBuffer.get(bytes);
				readBuffer.clear();
				writeResponse(channel);
			}
		} catch (Exception e) {
			logger.error("Exception: " + e.getMessage(), e);
			// e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param channel
	 * @throws Exception
	 */
	void writeResponse(StreamChannel channel) throws Exception {
		try {
			if (this.writeBuffers == null) {
				initWriteBuffers();
				// initWriteBuffer();
			}

			// Write the file content to the channel
			write(channel, writeBuffers, fileLength);
			// write(channel, writeBuffer);
		} catch (Exception exp) {
			logger.error("Exception: " + exp.getMessage(), exp);
			// exp.printStackTrace();
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

		/*
		 * // Flip all buffers XnioUtils.flipAll(buffers); WriteChannelListener
		 * writeListener = (WriteChannelListener)
		 * ((ChannelListener.SimpleSetter) channel .getWriteSetter()).get();
		 * writeListener.reset(); long written = channel.write(buffers); //
		 * Initialize the listener fields writeListener.init(buffers, total,
		 * written); channel.resumeWrites();
		 */

		for (ByteBuffer byteBuffer : buffers) {
			write(channel, byteBuffer);
		}
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
	}

	/**
	 * Read the file from HD and initialize the write byte buffers array.
	 * 
	 * @throws IOException
	 */
	private void initWriteBuffers() throws IOException {

		File file = new File("data" + File.separatorChar + "file.txt");
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		FileChannel fileChannel = raf.getChannel();

		fileLength = fileChannel.size() + XnioUtils.CRLF.getBytes().length;
		double tmp = (double) fileLength / XnioUtils.WRITE_BUFFER_SIZE;
		int length = (int) Math.ceil(tmp);
		writeBuffers = new ByteBuffer[length];

		for (int i = 0; i < writeBuffers.length - 1; i++) {
			writeBuffers[i] = ByteBuffer.allocate(XnioUtils.WRITE_BUFFER_SIZE);
		}

		int temp = (int) (fileLength % XnioUtils.WRITE_BUFFER_SIZE);
		writeBuffers[writeBuffers.length - 1] = ByteBuffer.allocate(temp);
		// Read the whole file in one pass
		fileChannel.read(writeBuffers);
		// Close the file channel
		raf.close();
		// Put the <i>CRLF</i> chars at the end of the last byte buffer to mark
		// the end of data
		writeBuffers[writeBuffers.length - 1].put(XnioUtils.CRLF.getBytes());
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
