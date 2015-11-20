package nl.dobots.slac.util;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 18-11-15
 *
 * @author Bart van Vliet
 */

// TODO: use apache.commons.math3.stat
public class StatisticsMath {

	/** pdf for a normal distribution */
	public static double pdfn(double x, double mean, double standardDeviation) {
		return (1 / (standardDeviation * Math.sqrt(2 * Math.PI))) * Math.exp(-(Math.pow(x - mean, 2)) / (2 * standardDeviation * standardDeviation));
	}

	public static double variance(float[] data) {
		double sum = 0;
		double squaredSum = 0;
		int size = data.length;

		for (int i=0; i<size; i++) {
			sum += data[i];
			squaredSum += data[i]*data[i];
		}
		return (squaredSum - (sum*sum)/size) / size;
	}
}
