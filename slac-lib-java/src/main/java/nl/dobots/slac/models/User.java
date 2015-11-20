package nl.dobots.slac.models;

import java.util.Random;

import nl.dobots.slac.Config;
import nl.dobots.slac.structs.Orientation;
import nl.dobots.slac.structs.Position;
import nl.dobots.slac.util.AngleMath;

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
public class User {
	private Position _position;
	private Orientation _orientation;

	public User(Position position, Orientation orientation) {
		_position = position;
		_orientation = orientation;
	}

	/** Copy constructor */
	public User(User user) {
		_position = new Position(user._position);
		_orientation = new Orientation(user._orientation);
	}

	/**
	 * Move a user to a new position
	 * @deprecated
	 */
	private void move(float radius, float angle) {
		float[] movement = AngleMath.polarToCartesian(radius, angle);
		_position.x += movement[0];
		_position.y += movement[1];
		_orientation.azimut = angle;

		// Add to trace
	}

	/**
	 * Move a user to a new pose
	 * @param movement        Movement since last position.
	 * @param newOrientation  New orientation of the user.
	 */
	private void move(Position movement, Orientation newOrientation) {
		_position.add(movement);
		_orientation = newOrientation;
	}

	/**
	 * Move the user to a specific position using a sampling function
	 * @deprecated
	 */
	public void samplePose(float radius, float angle) {
		Random rng = new Random();
		float sampleAngle = AngleMath.mapAngle(angle + (float)rng.nextGaussian() * Config.STANDARD_DEVIATION_HEADING);
		float sampleRadius = radius + (float)rng.nextGaussian() * Config.STANDARD_DEVIATION_STEP;
		move(sampleRadius, sampleAngle);
	}

	/**
	 * Move the user to a specific position using a sampling function
	 * @param movement        Movement since last position.
	 * @param newOrientation  New orientation of the user.
	 */
	public void samplePose(Position movement, Orientation newOrientation) {
		Random rng = new Random();
		// TODO: different standard deviation?
		movement.x += Config.STANDARD_DEVIATION_STEP * (float)rng.nextGaussian();
		movement.y += Config.STANDARD_DEVIATION_STEP * (float)rng.nextGaussian();
		newOrientation.azimut = AngleMath.mapAngle(newOrientation.azimut + Config.STANDARD_DEVIATION_HEADING * (float)rng.nextGaussian());
		move(movement, newOrientation);
	}

	public Position getPosition() {
		return _position;
	}

	public Orientation getOrientation() {
		return _orientation;
	}
}
