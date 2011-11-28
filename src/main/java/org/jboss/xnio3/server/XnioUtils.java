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

import java.nio.ByteBuffer;

/**
 * {@code XnioUtils}
 * 
 * Created on Nov 22, 2011 at 5:01:23 PM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public final class XnioUtils {

	/**
	 * 
	 */
	public static final int WRITE_BUFFER_SIZE = 16 * 1024;
	/**
	 * 
	 */
	public static final String CRLF = "\r\n";
	/**
	 * The default server port
	 */
	public static final int SERVER_PORT = 8080;

	/**
	 * Create a new instance of {@code XnioUtils}
	 */
	private XnioUtils() {
		super();
	}

	/**
	 * Flip all byte buffers
	 * 
	 * @param buffers
	 */
	public static void flipAll(ByteBuffer[] buffers) {
		for (ByteBuffer bb : buffers) {
			bb.flip();
		}
	}

	/**
	 * Flip the byte buffer
	 * 
	 * @param buffer
	 */
	public static void flip(ByteBuffer buffer) {
		buffer.flip();
	}
}
