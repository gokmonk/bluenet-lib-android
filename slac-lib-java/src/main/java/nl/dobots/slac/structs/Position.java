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
public class Position {
	public float x;
	public float y;
	public float z;

	public Position() {
		new Position(0, 0, 0);
	}

	public Position(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Copy constructor */
	public Position(Position position) {
		x = position.x;
		y = position.y;
		z = position.z;
	}

	public void add(Position position) {
		x += position.x;
		y += position.y;
		z += position.z;
	}

	public void subtract(Position position) {
		x -= position.x;
		y -= position.y;
		z -= position.z;
	}

	public void multiply(float val) {
		x *= val;
		y *= val;
		z *= val;
	}
}
