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
 * Created on 16-11-15
 *
 * @author Bart van Vliet
 */
public class KalmanFilter1D {
	private float _processNoise;         // R
	private float _measurementNoise;     // Q
	private float _stateTransitionModel; // A
	private float _controlInputModel;    // B
	private float _observationModel;     // C
	private float _covariance;           // cov
	private float _state;                // x
	public boolean _initialized;

//	public KalmanFilter1D() {
//		new KalmanFilter1D(1, 1, 1, 1, 1);
//	}

	public KalmanFilter1D(float processNoise, float measurementNoise, float stateTransitionModel, float controlInputModel, float observationModel) {
		_processNoise = processNoise;
		_measurementNoise = measurementNoise;
		_stateTransitionModel = stateTransitionModel;
		_controlInputModel = controlInputModel;
		_observationModel = observationModel;
		_initialized = false;
	}

	public float filter(float measurement) {
		return filter(measurement, 0);
	}

	public float filter(float measurement, float control) {
		if (!_initialized) {
			_state = measurement / _observationModel;
			_covariance = _measurementNoise / _observationModel / _observationModel;
			_initialized = true;
		}
		else {
			// Compute prediction
			float statePrediction = _stateTransitionModel * _state + _controlInputModel * control;
			float covariancePrediction = _stateTransitionModel * _covariance * _stateTransitionModel + _processNoise;

			// Kalman gain
			float kalmanGain = covariancePrediction * _observationModel * 1/(_observationModel*covariancePrediction*_observationModel + _measurementNoise);

			// Correction
			_state = statePrediction + kalmanGain * (measurement - _observationModel*statePrediction);
			_covariance = covariancePrediction - (kalmanGain * _observationModel * covariancePrediction);
		}
		return _state;
	}

	public float getState() {
		return _state;
	}


}
