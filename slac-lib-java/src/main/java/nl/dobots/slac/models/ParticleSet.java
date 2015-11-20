package nl.dobots.slac.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.dobots.slac.Config;
import nl.dobots.slac.structs.Covariance;
import nl.dobots.slac.structs.Landmark;
import nl.dobots.slac.structs.Orientation;
import nl.dobots.slac.structs.Position;
import nl.dobots.slac.util.Sampling;

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
public class ParticleSet {
	private int _numParticles;
	private float _effectiveParticleThreshold;
	private List<Particle> _particleList;
	private Set<String> _initializedLandmarks;
	private LandmarkInitSet _landmarkInitSet;
	private float[] _weights;
	private float[] _normalizedWeights;
	private boolean _weightArrayIsSynched = false;

	public ParticleSet(int numParticles, float effectiveParticleThreshold) {
		_numParticles = numParticles;
		_effectiveParticleThreshold = effectiveParticleThreshold;

		_particleList = new ArrayList<>(_numParticles);
		for (int i=0; i<_numParticles; i++) {
			_particleList.add(new Particle());
		}

		_initializedLandmarks = new HashSet<>();
		_landmarkInitSet = new LandmarkInitSet(
				Config.INIT_NUM_PARTICLES,
				Config.INIT_STANDARD_DEVIATION_RANGE,
				Config.INIT_EFFECTIVE_PARTICLE_THRESHOLD,
				Config.INIT_NUM_RANDOM_PARTICLES,
				Config.INIT_MAX_VARIANCE
		);

		_weights = new float[_numParticles];
		_normalizedWeights = new float[_numParticles];
	}

	/**
	 * Given a control, let each particle sample a new user position
	 * @deprecated
	 */
	public void samplePose(float radius, float angle) {
		for (Particle particle : _particleList) {
			particle.samplePose(radius, angle);
		}
	}

	/**
	 * Given a control, let each particle sample a new user position
	 * @param movement        Movement since last position.
	 * @param newOrientation  New orientation of the user.
	 */
	public void samplePose(Position movement, Orientation newOrientation) {
		for (Particle particle : _particleList) {
			particle.samplePose(movement, newOrientation);
		}
	}

	/**
	 * Let each particle process a proximity observation
	 * @param uid       ID of the landmark.
	 * @param name      Name of the landmark.
	 * @param distance  Measured distance between landmark and user.
	 * @param moved     True when the landmark has moved.
	 */
	public void processProximityObservation(String uid, String name, float distance, boolean moved) {
		if (moved) {
			// If the landmark has moved we remove it from all particles
			// TODO
		}

		if (!_initializedLandmarks.contains(uid)) {
			Position estimatedUserPos = userPositionEstimate();
			_landmarkInitSet.processProximityMeasurement(uid, estimatedUserPos, distance);

			// If the landmarks position variance is now low enough,
			// then add it to the particles and remove from landmarkInitSet.
			Position landmarkPositionEstimate = new Position();
			Covariance landmarkPositionVariance = new Covariance();
			if (_landmarkInitSet.getPositionEstimate(uid, landmarkPositionEstimate, landmarkPositionVariance)) {
				for (Particle particle : _particleList) {
					particle.addLandmark(uid, new Landmark(landmarkPositionEstimate, landmarkPositionVariance, name));
				}
				_landmarkInitSet.remove(uid);
				_initializedLandmarks.add(uid);
			}
		}
		else {
			for (Particle particle : _particleList) {
				particle.processProximityObservation(uid, distance);
			}
			_weightArrayIsSynched = false;
		}
	}

	/**
	 * Resample the internal particle list using their weights.
	 * Uses low variance sampling.
	 */
	public void resample() {
		syncWeightArray();
		if (Sampling.numberOfEffectiveParticles(_normalizedWeights) < _effectiveParticleThreshold) {
			List<Particle> newParticles = new ArrayList<>(_numParticles);
			int[] indices = Sampling.lowVarianceSampling(_normalizedWeights, _numParticles);
			for (int i=0; i<_numParticles; i++) {
				newParticles.add(new Particle(_particleList.get(indices[i])));
			}
			_particleList = newParticles;
			_weightArrayIsSynched = false;
		}
	}

	/** Get a weighted average of all landmark estimates */
	public Map<String, Landmark> getLandmarkEstimate() {
		syncWeightArray();
		Map<String, Landmark> landmarks = new HashMap<>();

		// Loop through all particles to get an estimate of the landmarks
		for (int i=0; i<_numParticles; i++) {
			Particle particle = _particleList.get(i);
			for (String key : particle.getLandmarks().keySet()) {
				Landmark landmark = particle.getLandmarks().get(key);
				if (!landmarks.containsKey(key)) {
					Landmark newLandmark = new Landmark();
					newLandmark.position.x = _normalizedWeights[i] * landmark.position.x;
					newLandmark.position.y = _normalizedWeights[i] * landmark.position.y;
					newLandmark.position.z = _normalizedWeights[i] * landmark.position.z;
					newLandmark.name = landmark.name;
					landmarks.put(key, newLandmark);
				}
				else {
					landmarks.get(key).position.x += _normalizedWeights[i] * landmark.position.x;
					landmarks.get(key).position.y += _normalizedWeights[i] * landmark.position.y;
					landmarks.get(key).position.z += _normalizedWeights[i] * landmark.position.z;
				}
			}
		}
		return landmarks;
	}

	/** Get the best estimate of the current user position */
	private Position userPositionEstimate() {
		syncWeightArray();

		Position pos = new Position(0,0,0);
		for (int i=0; i<_numParticles; i++) {
//			Position userPos = new Position(_particleList.get(i).getUser().getPosition());
//			userPos.multiply(weights[i]);
//			pos.add(userPos);
			pos.x += _normalizedWeights[i] * _particleList.get(i).getUser().getPosition().x;
			pos.y += _normalizedWeights[i] * _particleList.get(i).getUser().getPosition().y;
			pos.z += _normalizedWeights[i] * _particleList.get(i).getUser().getPosition().z;
		}
		return pos;
	}

	private void syncWeightArray() {
		if (_weightArrayIsSynched) {
			return;
		}
		for (int i=0; i<_numParticles; i++) {
			_weights[i] = _particleList.get(i).getWeight();
		}
		_normalizedWeights = _weights.clone();
		Sampling.normalize(_normalizedWeights);
		_weightArrayIsSynched = true;
	}
}
