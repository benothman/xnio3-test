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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * {@code LogParser}
 * 
 * Created on Nov 1, 2011 at 10:30:28 AM
 * 
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class LogParser {

	/**
	 * Create a new instance of {@code LogParser}
	 */
	public LogParser() {
		super();
	}

	/**
	 * 
	 * @param filename
	 * @throws Exception
	 */
	public static void parse(String filename) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
		String line = null;
		// drop the header line
		line = br.readLine();
		double avg_min = Double.MAX_VALUE, avg_max = Double.MIN_VALUE, avg_avg = 0, tmp;
		String tab[];
		int counter = 0;
		while ((line = br.readLine()) != null) {
			counter++;
			tab = line.split("\\s+");
			tmp = Double.parseDouble(tab[2]);
			avg_avg += tmp;
			if (tmp > avg_max) {
				avg_max = tmp;
			}
			if (tmp < avg_min) {
				avg_min = tmp;
			}
		}
		br.close();
		avg_avg /= counter;
		FileWriter fw = new FileWriter(filename, true);
		fw.write("---------- STATS ----------\n");

		fw.write("AVG MAX: " + avg_max + " ms\n");
		fw.write("AVG MIN: " + avg_min + " ms\n");
		fw.write("AVG AVG: " + avg_avg + " ms\n");
		fw.flush();
		fw.close();
	}

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: java " + LogParser.class.getName() + " filename");
			System.exit(1);
		}

		parse(args[0]);
	}
}