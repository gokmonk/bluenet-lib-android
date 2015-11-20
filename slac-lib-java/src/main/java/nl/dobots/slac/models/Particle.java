package nl.dobots.slac.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import nl.dobots.slac.structs.Landmark;
import nl.dobots.slac.structs.Orientation;
import nl.dobots.slac.structs.Position;
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
 * Created on 17-11-15
 *
 * @author Bart van Vliet
 */
public class Particle {
	private float _weight;
	private User _user;
	private Map<String, Landmark> _landmarks;


	public Particle() {
		resetWeight();
		_user = new User(new Position(), new Orientation());
		_landmarks = new HashMap<>();
	}

	/** Copy constructor */
	public Particle(Particle particle) {
		_weight = particle._weight;
		_user = new User(particle._user);
		_landmarks = new HashMap<>();
		for (String key : particle._landmarks.keySet()) {
			_landmarks.put(key, new Landmark(particle._landmarks.get(key)));
		}
	}

	public void addLandmark(String uid, Landmark landmark) {
		_landmarks.put(uid, landmark);
	}

	public void remLandmark(String uid) {
		_landmarks.remove(uid);
	}

	/**
	 * Given a control, sample a new user position
	 * @deprecated
	 */
	public void samplePose(float radius, float angle) {
		_user.samplePose(radius, angle);
	}

	/**
	 * Given a control, sample a new user position
	 * @param movement        Movement since last position.
	 * @param newOrientation  New orientation of the user.
	 */
	public void samplePose(Position movement, Orientation newOrientation) {
		_user.samplePose(movement, newOrientation);
	}

	/**
	 * Update a landmark using the EKF update rule
	 * @param uid          ID of the landmark.
	 * @param distance     Measured distance between landmark and user.
	 */
	public void processProximityObservation(String uid, float distance) {
		Landmark landmark = _landmarks.get(uid);

		// Compute the distance between the predicted user position of this
		// particle and the predicted position of the landmark.
		float dx = _user.getPosition().x - landmark.position.x;
		float dy = _user.getPosition().y - landmark.position.y;

		// TODO: find better values for default covariance
		Random rng = new Random();
		float errorCovariance = 2 + (float)(0.1*rng.nextGaussian());
		float distancePrediction = (float)Math.max(0.001, Math.sqrt(dx * dx + dy * dy));

		// Compute innovation: difference between the observation and the predicted value
		float innovation = distance - distancePrediction;

		// Compute Jacobian
		float[] jacobian = {-dx/distancePrediction, -dy/distancePrediction};

		// Compute covariance of the innovation
		// covV = H * Cov_s * H^T + error
		float[] HxCov = {landmark.covariance.x.x * jacobian[0] + landmark.covariance.x.y * jacobian[1],
		                 landmark.covariance.y.x * jacobian[0] + landmark.covariance.y.y * jacobian[1]};

		float covV = HxCov[0]*jacobian[0] + HxCov[1]*jacobian[1] + errorCovariance;

		// Kalman gain
		float[] kalmanGain = { HxCov[0] / covV, HxCov[1] / covV };

		// Calculate the new position of the landmark
		landmark.position.x += kalmanGain[0] * innovation;
		landmark.position.y += kalmanGain[1] * innovation;

		// Calculate the new covariance
		// cov_t = cov_t-1 - K * covV * K^T
		landmark.covariance.x.x -= jacobian[0] * jacobian[0] * covV;
		landmark.covariance.x.y -= jacobian[0] * jacobian[1] * covV;
		landmark.covariance.y.x -= jacobian[1] * jacobian[0] * covV;
		landmark.covariance.y.y -= jacobian[1] * jacobian[1] * covV;

		//Update the weight of the particle
		//_weight = _weight - (innovation * (1.0 / covV) * innovation);
		_weight *= StatisticsMath.pdfn(distance, distancePrediction, covV);

	}

	public void resetWeight() {
		_weight = 1f;
	}

	public float getWeight() {
		return _weight;
	}

	public User getUser() {
		return _user;
	}

	public Map<String, Landmark> getLandmarks() {
		return _landmarks;
	}
}
