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
 * Created on 18-11-15
 *
 * @author Bart van Vliet
 */
public class LandmarkParticle {
	public Position position;
	public float weight = 1f;

	public LandmarkParticle(Position position, float weight) {
		this.position = position;
		this.weight = weight;
	}

	/** Copy constructor */
	public LandmarkParticle(LandmarkParticle particle) {
		position = new Position(particle.position);
		weight = particle.weight;
	}
}
