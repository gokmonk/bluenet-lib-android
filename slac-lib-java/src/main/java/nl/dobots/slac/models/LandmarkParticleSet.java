package nl.dobots.slac.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nl.dobots.slac.structs.Covariance;
import nl.dobots.slac.structs.LandmarkParticle;
import nl.dobots.slac.structs.Position;
import nl.dobots.slac.util.AngleMath;
import nl.dobots.slac.util.Sampling;
import nl.dobots.slac.util.StatisticsMath;

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
public class LandmarkParticleSet {
	private int _numParticles;
	private float _standardDeviationProximity; // SD of proximity measurements
	private float _effectiveParticleThreshold; // Threshold of effective particles for resampling
	private int _numRandomParticles;           // Number of random particles to use each update
	private float _maxVariance;                // The maximum variance before a landmark estimate is returned
	private int _numMeasurements;
	private List<LandmarkParticle> _particleList;
	private float[] _weights;
	private float[] _normalizedWeights;
	private boolean _weightArrayIsSynched = false;


	public LandmarkParticleSet(int numParticles, float standardDeviationRange, float effectiveParticleThreshold, int numRandomParticles, float maxVariance) {
		_numParticles = numParticles;
		_standardDeviationProximity = standardDeviationRange;
		_effectiveParticleThreshold = effectiveParticleThreshold;
		_numRandomParticles = numRandomParticles;
		_maxVariance = maxVariance;

		_numMeasurements = 0;
		_weights = new float[_numParticles];
		_normalizedWeights = new float[_numParticles];
	}

	/**
	 * Integrate a new measurement in the particle set
	 * @param userPosition Estimate of the user position.
	 * @param distance     Measured distance between landmark and user.
	 */
	public void processProximityMeasurement(Position userPosition, float distance) {
		if (_numMeasurements == 0) {
			// Initialize the particle set by adding random particles around the user
			_particleList = createRandomParticles(_numParticles, userPosition, distance);
			_weightArrayIsSynched = false;
		}
		else {
			updateWeights(userPosition, distance);

			syncWeightArray();

			// Determine whether resampling is effective now
			// Is based on the normalised weights
			if (Sampling.numberOfEffectiveParticles(_normalizedWeights) < _effectiveParticleThreshold) {

				// Use low variance resampling to generate a set of new particles
				// Returns a list of N - randomParticles particles
				List<LandmarkParticle> particles = resample(_numParticles - _numRandomParticles);

				// Add new uniformly distributed particles tot the set
				// Random particles are distributed around the current position
				particles.addAll(createRandomParticles(_numRandomParticles, userPosition, distance));
				_particleList = particles;
				_weightArrayIsSynched = false;
			}
		}

		_numMeasurements++;
	}

	/**
	 * Resample the particle set and return a given number of new particles
	 */
	private List<LandmarkParticle> resample(int numSamples) {
		syncWeightArray();

		// These particles don't move by themselves, since the landmarks don't move.
		// So add some randomness to the particles' position.
		Random rng = new Random();
		List<LandmarkParticle> particles = new ArrayList<>(numSamples);
		int[] indices = Sampling.lowVarianceSampling(_normalizedWeights, numSamples);
		for (int i=0; i<numSamples; i++) {
			LandmarkParticle particle = _particleList.get(indices[i]);
			LandmarkParticle newParticle = new LandmarkParticle(particle);
			newParticle.position.x += _standardDeviationProximity /4 * (float)rng.nextGaussian();
			newParticle.position.y += _standardDeviationProximity /4 * (float)rng.nextGaussian();
			newParticle.weight = 1;
			particles.add(newParticle);
		}
		_weightArrayIsSynched = false;
		return particles;
	}

	/**
	 * Create a list of random particles around given center at a
	 * distance following a normal distribution with radius as mean.
	 */
	private List<LandmarkParticle> createRandomParticles(int numParticles, Position center, float meanRadius) {
		float deltaAngle = 2*(float)Math.PI / numParticles;
		List<LandmarkParticle> particles = new ArrayList<>(numParticles);
		Random rng = new Random();
		for (int i=0; i<numParticles; i++) {
			float angle = i*deltaAngle;
			float radius = meanRadius + _standardDeviationProximity * (float)rng.nextGaussian();
			float[] diff = AngleMath.polarToCartesian(radius, angle);
			Position pos = new Position(center);
			pos.x += diff[0];
			pos.y += diff[1];
			particles.add(new LandmarkParticle(pos, 1));
		}
		return particles;
	}

	/**
	 * Update weight of each particle
	 * @param userPosition Estimate of the user position.
	 * @param distance     Measured distance between landmark and user.
	 */
	private void updateWeights(Position userPosition, float distance) {
		for (LandmarkParticle particle : _particleList) {
			float dx = particle.position.x - userPosition.x;
			float dy = particle.position.y - userPosition.y;

			// Calculate distance estimate
			float distanceEstimate = (float)Math.sqrt(dx*dx + dy*dy);

			// What is the probability of r (distance) given est (distanceEstimate)? p(r|est)
			// Update the weight accordingly
			// p(r) = N(r|est,sd)
			float weight = (float)StatisticsMath.pdfn(distance, distanceEstimate, _standardDeviationProximity);
			particle.weight *= weight;
		}
		_weightArrayIsSynched = false;
	}

	/**
	 * Get the current estimate of this landmark's position
	 * @param estimate   Will be set to the position estimate
	 * @param covariance Will be set to the covariance of the estimate
	 * @return true if the estimate is valid
	 */
	public boolean getPositionEstimate(Position estimate, Covariance covariance) {
		// Fast check, never return before we have at least multiple measurements
		if (_numMeasurements < 10) { // TODO: magic nr
			return false;
		}
		covariance = getParticleVariance();
//		if (covariance.covariance[0][0] < _maxVariance && covariance.covariance[1][1] < _maxVariance && covariance.covariance[2][2] < _maxVariance) {
		if (covariance.x.x < _maxVariance && covariance.y.y < _maxVariance && covariance.z.z < _maxVariance) {
			estimate = getAveragePosition();
			return true;
		}
		return false;
	}

	/** Get the weighted average position of this particle set */
	private Position getAveragePosition() {
		syncWeightArray();

		Position pos = new Position();
		for (int i=0; i<_numParticles; i++) {
			pos.x += _normalizedWeights[i] * _particleList.get(i).position.x;
			pos.y += _normalizedWeights[i] * _particleList.get(i).position.y;
			pos.z += _normalizedWeights[i] * _particleList.get(i).position.z;
		}
		return pos;
	}

	/** Get the particle variance in x, y and z */
	private Covariance getParticleVariance() {
//		Covariance covariance = new Covariance();
//
//		// TO DO: make sure we don't need to make a copy every time
//		float[] values = new float[_numParticles];
//		for (int i=0; i<_numParticles; i++) {
//			values[i] = _particleList.get(i).position.x;
//		}
//		covariance.x.x = (float)StatisticsMath.variance(values);
//
//		for (int i=0; i<_numParticles; i++) {
//			values[i] = _particleList.get(i).position.y;
//		}
//		covariance.y.y = (float)StatisticsMath.variance(values);
//
//		for (int i=0; i<_numParticles; i++) {
//			values[i] = _particleList.get(i).position.z;
//		}
//		covariance.z.z = (float)StatisticsMath.variance(values);
//
//		return covariance;
		return StatisticsMath.variance(_particleList);
	}

	private void syncWeightArray() {
		if (_weightArrayIsSynched) {
			return;
		}
		for (int i=0; i<_numParticles; i++) {
			_weights[i] = _particleList.get(i).weight;
		}
		_normalizedWeights = _weights.clone();
		Sampling.normalize(_normalizedWeights);
		_weightArrayIsSynched = true;
	}
}
