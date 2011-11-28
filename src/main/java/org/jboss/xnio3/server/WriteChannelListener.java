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

import org.xnio.ChannelListener;
import org.xnio.channels.StreamChannel;

/**
 * {@code WriteChannelListener}
 * 
 * Created on Nov 22, 2011 at 4:47:23 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class WriteChannelListener implements ChannelListener<StreamChannel> {

	private int offset = 0;
	private long written = 0;
	private ByteBuffer buffers[];
	private long total = 0;
	private String sessionId;

	/**
	 * Create a new instance of {@code WriteChannelListener}
	 */
	public WriteChannelListener() {
		super();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xnio.ChannelListener#handleEvent(java.nio.channels.Channel)
	 */
	@Override
	public void handleEvent(StreamChannel channel) {

		if (this.total > 0) {
			if (this.written < this.total) {
				this.offset = (int) (this.written / XnioUtils.WRITE_BUFFER_SIZE);
				try {
					// Update the value of written bytes
					this.written += channel.write(buffers, offset, buffers.length - offset);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (this.written == this.total) {
				reset();
				channel.suspendWrites();
			}
		}
	}

	/**
	 * Initialize the Channel listener fields
	 * 
	 * @param buffers
	 * @param total
	 * @param written
	 */
	public void init(ByteBuffer[] buffers, long total, long written) {
		this.buffers = buffers;
		this.written = written;
		this.total = total;
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

	/**
	 * Getter for offset
	 * 
	 * @return the offset
	 */
	public int getOffset() {
		return this.offset;
	}

	/**
	 * Setter for the offset
	 * 
	 * @param offset
	 *            the offset to set
	 */
	public void setOffset(int offset) {
		this.offset = offset;
	}

	/**
	 * Getter for total
	 * 
	 * @return the total
	 */
	public long getTotal() {
		return this.total;
	}

	/**
	 * Setter for the total
	 * 
	 * @param total
	 *            the total to set
	 */
	public void setTotal(long total) {
		this.total = total;
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
