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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code BufferPool}
 * 
 * Created on Nov 15, 2011 at 9:47:22 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public final class BufferPool {

	/**
	 * The default capacity is 8KB
	 */
	public static final int DEFAULT_CAPACITY = 8 * 1024;
	private static final int MAX_ITEMS = 256;
	private ConcurrentLinkedQueue<ByteBuffer> queue;
	private int capacity;
	private AtomicInteger counter = new AtomicInteger(0);

	/**
	 * Create a new instance of {@code BufferPool} with capacity equal to
	 * <i>DEFAULT_CAPACITY</i>
	 */
	private BufferPool() {
		this(DEFAULT_CAPACITY);
	}

	/**
	 * Create a new instance of {@code BufferPool} with the specified capacity
	 * 
	 * @param capacity
	 *            the capacity of the pool
	 */
	private BufferPool(int capacity) {
		this.capacity = capacity;
		this.queue = new ConcurrentLinkedQueue<ByteBuffer>();
	}

	/**
	 * 
	 * @param capacity
	 *            the pool capacity
	 * @return a new {@code BufferPool} instance
	 */
	public static BufferPool create(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("The capacity may not be null or negative");
		}

		return new BufferPool(capacity);
	}

	/**
	 * Create a new instance of {@code BufferPool} with the
	 * <i>DEFAULT_CAPACITY</>
	 * 
	 * @return a new instance of {@code BufferPool}
	 */
	public static BufferPool create() {
		return new BufferPool();
	}

	/**
	 * Peek a {@code ByteBuffer} from the {@code BufferPool}. This method return
	 * an element from the list if there is at least one available. If the
	 * maximum number of elements were created and the pool is empty the current
	 * thread blocks until at least one element becomes available. If the buffer
	 * is empty and the maximum number of elements is not reached yet, a new
	 * element is created.
	 * 
	 * @return a {@code ByteBuffer} instance with default capacity
	 * @throws Exception
	 */
	public ByteBuffer peek() throws Exception {
		if (queue.isEmpty() && counter.get() < MAX_ITEMS) {
			ByteBuffer newBuffer = ByteBuffer.allocate(capacity);
			this.queue.offer(newBuffer);
			counter.incrementAndGet();
		}

		synchronized (queue) {
			while (queue.isEmpty()) {
				queue.wait();
			}
			return queue.poll();
		}
	}

	/**
	 * 
	 * @param buffer
	 * @throws NullPointerException
	 *             if the <i>buffer<i> is null.
	 */
	public void restitute(ByteBuffer buffer) {
		if (buffer == null) {
			throw new NullPointerException();
		}

		synchronized (this.queue) {
			buffer.clear();
			this.queue.offer(buffer);
			this.queue.notifyAll();
		}
	}

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		final BufferPool pool = create();

		Thread threads[] = new Thread[100];

		final AtomicInteger counter = new AtomicInteger(0);

		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(new Runnable() {

				@Override
				public void run() {
					long id = Thread.currentThread().getId();
					int max = 10000;
					try {
						while ((max--) > 0) {
							ByteBuffer buffer = pool.peek();
							System.out.println("[Tread-" + id + "] -> peek buffer, max = " + max);
							Thread.sleep(2);
							pool.restitute(buffer);
							System.out.println("[Tread-" + id + "] -> restitute buffer");
						}
					} catch (Exception exp) {
						exp.printStackTrace();
					}
					counter.incrementAndGet();
					System.out.println("[Thread-" + id + "] terminated");
				}
			});
		}

		for (int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].join();
		}
		System.out.println("Counter : " + counter.get());
	}
}
