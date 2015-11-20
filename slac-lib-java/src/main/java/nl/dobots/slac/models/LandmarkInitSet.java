package nl.dobots.slac.models;

import java.util.HashMap;
import java.util.Map;

import nl.dobots.slac.structs.Covariance;
import nl.dobots.slac.structs.Position;

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
public class LandmarkInitSet {
	private int _numParticles;
	private float _standardDeviationProximity; // SD of proximity measurements
	private float _effectiveParticleThreshold; // Threshold of effective particles for resampling
	private int _numRandomParticles;           // Number of random particles to use each update
	private float _maxVariance;                // The maximum variance before a landmark estimate is returned
	private Map<String, LandmarkParticleSet> _landmarkParticleSetMap;


	public LandmarkInitSet(int numParticles,
						   float standardDeviationProximity,
						   float effectiveParticleThreshold,
						   int numRandomParticles,
						   float maxVariance) {
		_numParticles = numParticles;
		_standardDeviationProximity = standardDeviationProximity;
		_effectiveParticleThreshold = effectiveParticleThreshold;
		_numRandomParticles = numRandomParticles;
		_maxVariance = maxVariance;
		_landmarkParticleSetMap = new HashMap<>();
	}

	public LandmarkInitSet(int numParticles, float standardDeviationProximity, int numRandomParticles, float maxVariance) {
		new LandmarkInitSet(numParticles,
				standardDeviationProximity,
				numParticles / (float)1.5,
				numRandomParticles,
				maxVariance);
	}

	/**
	 * Integrate a new measurement
	 * @param uid          ID of the landmark.
	 * @param userPosition Estimate of the user position.
	 * @param distance     Measured distance between landmark and user.
	 */
	public void processProximityMeasurement(String uid, Position userPosition, float distance) {
		if (!_landmarkParticleSetMap.containsKey(uid)) {
			LandmarkParticleSet particleSet = new LandmarkParticleSet(
					_numParticles,
					_standardDeviationProximity,
					_effectiveParticleThreshold,
					_numRandomParticles,
					_maxVariance);
			_landmarkParticleSetMap.put(uid, particleSet);
		}
		_landmarkParticleSetMap.get(uid).processProximityMeasurement(userPosition, distance);
	}

	public void remove(String uid) {
		_landmarkParticleSetMap.remove(uid);
	}

	/**
	 * Get best position estimate for a landmark
	 * @param position   Will be set to the position estimate
	 * @param covariance Will be set to the covariance of the estimate
	 * @return true if the estimate is valid
	 */
	public boolean getPositionEstimate(String uid, Position position, Covariance covariance) {
		return _landmarkParticleSetMap.get(uid).getPositionEstimate(position, covariance);
	}

}
