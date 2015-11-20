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
 * Created on 19-11-15
 *
 * @author Bart van Vliet
 */
public class Covariance {
//	// covariance matrix:
//	// [[varXX, varXY, varXZ],
//	//  [varYX, varYY, varYZ],
//	//  [varZX, varZY, varZZ]]
//	public float[][] covariance;
//
//	public Covariance() {
//		covariance = new float[3][3];
//	}
//
//	public Covariance(float[][] covariance) {
//		this.covariance = covariance;
//	}
//
//	/** Copy constructor */
//	public Covariance(Covariance covariance) {
//		this.covariance = new float[3][];
//		for (int i=0; i<3; i++) {
//			this.covariance[i] = Arrays.copyOf(covariance.covariance[i], 3);
//		}
//	}



	public Position x;
	public Position y;
	public Position z;

	public Covariance() {
		x = new Position();
		y = new Position();
		z = new Position();
	}

	public Covariance(Position x, Position y, Position z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Copy constructor */
	public Covariance(Covariance covariance) {
		x = new Position(covariance.x);
		y = new Position(covariance.y);
		z = new Position(covariance.z);
	}
}
