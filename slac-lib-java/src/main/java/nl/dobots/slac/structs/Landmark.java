package nl.dobots.slac.structs;

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
public class Landmark {
	public Position position;
	public Covariance covariance;
	public String name;

	public Landmark() {
		position = new Position();
		covariance = new Covariance();
	}

	public Landmark(Position position, Covariance covariance) {
		this.position = position;
		this.covariance = covariance;
//		this.name = null;
	}

	public Landmark(Position position, Covariance covariance, String name) {
		this.position = position;
		this.covariance = covariance;
		this.name = name;
	}

	/** Copy constructor */
	public Landmark(Landmark landmark) {
		position = new Position(landmark.position);
		covariance = new Covariance(landmark.covariance);
		name = landmark.name; // Strings are immutable..
	}
}
