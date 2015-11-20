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
		}
	}

	/**
	 * Resample the internal particle list using their weights.
	 * Uses low variance sampling.
	 */
	public void resample() {
		// TODO: keep up the weights, don't make a copy every time
		float[] weights = new float[_numParticles];
		for (int i=0; i<_numParticles; i++) {
			weights[i] = _particleList.get(i).getWeight();
		}
		if (Sampling.numberOfEffectiveParticles(weights) < _effectiveParticleThreshold) {
			List<Particle> newParticles = new ArrayList<>(_numParticles);
			int[] indices = Sampling.lowVarianceSampling(weights, _numParticles);
			for (int i=0; i<_numParticles; i++) {
				newParticles.add(new Particle(_particleList.get(i)));
			}
			_particleList = newParticles;
		}
	}

	/** Get a weighted average of all landmark estimates */
	public Map<String, Landmark> getLandmarkEstimate() {
		// TODO: keep up the weights, don't make a copy every time
		float[] weights = new float[_numParticles];
		for (int i=0; i<_numParticles; i++) {
			weights[i] = _particleList.get(i).getWeight();
		}
		Sampling.normalize(weights);

		Map<String, Landmark> landmarks = new HashMap<>();

		// Loop through all particles to get an estimate of the landmarks
		for (int i=0; i<_numParticles; i++) {
			Particle particle = _particleList.get(i);
			for (String key : particle.getLandmarks().keySet()) {
				Landmark landmark = particle.getLandmarks().get(key);
				if (!landmarks.containsKey(key)) {
					Landmark newLandmark = new Landmark();
					newLandmark.position.x = weights[i] * landmark.position.x;
					newLandmark.position.y = weights[i] * landmark.position.y;
					newLandmark.position.z = weights[i] * landmark.position.z;
					newLandmark.name = landmark.name;
					landmarks.put(key, newLandmark);
				}
				else {
					landmarks.get(key).position.x += weights[i] * landmark.position.x;
					landmarks.get(key).position.y += weights[i] * landmark.position.y;
					landmarks.get(key).position.z += weights[i] * landmark.position.z;
				}
			}
		}
		return landmarks;
	}

	/** Get the best estimate of the current user position */
	private Position userPositionEstimate() {
		// TODO: keep up the weights, don't make a copy every time
		float[] weights = new float[_numParticles];
		for (int i=0; i<_numParticles; i++) {
			weights[i] = _particleList.get(i).getWeight();
		}
		Sampling.normalize(weights);
		Position pos = new Position(0,0,0);
		for (int i=0; i<_numParticles; i++) {
			pos.x += weights[i] * _particleList.get(i).getUser().getPosition().x;
			pos.y += weights[i] * _particleList.get(i).getUser().getPosition().y;
			pos.z += weights[i] * _particleList.get(i).getUser().getPosition().z;
		}
		return pos;
	}
}
