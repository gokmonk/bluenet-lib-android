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
 * Created on 17-11-15
 *
 * @author Bart van Vliet
 */
public class Sampling {
	/** Normalize a set of weights */
	public static void normalize(float[] weights) {
		float sum = 0;
		for (int i=0; i<weights.length; i++) {
			sum += weights[i];
		}
		for (int i=0; i<weights.length; i++) {
			weights[i] /= sum;
		}
	}

	/**
	 * Samples a new set using a low variance sampler from a array of weights
	 * @return Array with selected indices of weights array
	 */
	public static int[] lowVarianceSampling(float[] normalizedWeights, int numSamples) {
		int size = normalizedWeights.length;
		float rand = (float)Math.random() / size;

		float c = normalizedWeights[0];
		int i = 0;
		int[] indices = new int[numSamples];

		for (int j=0; j<numSamples; j++) {
			float U = rand + j/size;
			while (U > c) {
				c += normalizedWeights[++i];
			}
			indices[j] = i;
		}
		return indices;
	}

	/** Sample using roulette wheel sampler from a array of weights
	 *	@return Array with selected indices of weights array
	 */
	public static int[] rouletteWheelSampling(float[] normalizedWeights, int numSamples) {
		float[] cumSumWeights = normalizedWeights.clone();
		cumulativeSum(cumSumWeights);
		int[] indices = new int[numSamples];

		for (int i=0; i<numSamples; i++) {
			float rand = (float)Math.random();

			for (int j=0; j<cumSumWeights.length; j++) {
				if (cumSumWeights[j] >= rand) {
					indices[i] = j;
					break;
				}
			}
		}
		return indices;
	}

	/** Convert an array of weights to an cumulative sum array */
	public static void cumulativeSum(float[] weights) {
		for (int i=1; i<weights.length; i++) {
			weights[i] += weights[i-1];
		}
	}

	/**
	 * Calculate the effective number of particles
	 * see https://en.wikipedia.org/wiki/Particle_filter#Sequential_importance_sampling_.28SIS.29
	 */
	public static float numberOfEffectiveParticles(float[] normalizedWeights) {
		float sum=0;
		for (int i=0; i<normalizedWeights.length; i++) {
			sum += normalizedWeights[i] * normalizedWeights[i];
		}
		return 1/sum;
	}
}
