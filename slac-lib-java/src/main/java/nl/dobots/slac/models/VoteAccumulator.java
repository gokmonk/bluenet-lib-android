package nl.dobots.slac.models;

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
public class VoteAccumulator {
	int _dimension;
	float _precision;
	float _centerX;
	float _centerY;
	int _numMeasurements;
	int _size;
	int[][] _votes = null;

	public VoteAccumulator(int dimension, float precision, float startX, float startY) {
		_dimension = dimension;
		_precision = precision;
		_centerX = startX;
		_centerY = startY;
		_numMeasurements = 0;
		_size = Math.round(dimension / precision);

		_votes = new int[_size][_size];
		for (int i=0; i<_size; i++) {
			for (int j=0; j<_size; j++) {
				_votes[i][j] = 0;
			}
		}
	}


	void addMeasurement(float x, float y, float r) {
		_numMeasurements++;
		x = x-_centerX;
		y = y-_centerY;

		//TODO
	}



}

