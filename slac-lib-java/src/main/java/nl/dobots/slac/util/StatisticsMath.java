package nl.dobots.slac.util;

import java.util.List;

import nl.dobots.slac.structs.Covariance;
import nl.dobots.slac.structs.Landmark;
import nl.dobots.slac.structs.LandmarkParticle;

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

	// TODO: make this function more generic
	public static Covariance variance(List<LandmarkParticle> particles) {
		double[] sum = {0,0,0};
		double[] squaredSum = {0,0,0};
		int size = particles.size();

		for (LandmarkParticle particle : particles) {
			sum[0] += particle.position.x;
			sum[1] += particle.position.y;
			sum[2] += particle.position.z;
			squaredSum[0] += particle.position.x * particle.position.x;
			squaredSum[1] += particle.position.y * particle.position.y;
			squaredSum[2] += particle.position.z * particle.position.z;
		}
		Covariance covariance = new Covariance();
		covariance.x.x = (float)((squaredSum[0] - sum[0]*sum[0]/size) / size);
		covariance.y.y = (float)((squaredSum[0] - sum[0]*sum[0]/size) / size);
		covariance.z.z = (float)((squaredSum[0] - sum[0]*sum[0]/size) / size);
		return covariance;
	}
}
