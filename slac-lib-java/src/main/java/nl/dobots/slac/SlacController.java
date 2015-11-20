package nl.dobots.slac;

import nl.dobots.slac.models.ParticleSet;
import nl.dobots.slac.structs.Orientation;
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
 * Created on 19-11-15
 *
 * @author Bart van Vliet
 */
public class SlacController {
	private int _numPartices;
	private ParticleSet _particleSet;

	/** Run the full SLAM update */
	public void update(Position movement, Orientation orientation) {
//	public void update(float distanceTraveled, float angle) {


		// Sample a new pose for each particle in the set
		_particleSet.samplePose(movement, orientation);

		// Let each particle process the scans
		for ...{
			_particleSet.processProximityObservation();
		}

		_particleSet.resample();

	}
}
