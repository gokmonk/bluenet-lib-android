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
 * Created on 17-11-15
 *
 * @author Bart van Vliet
 */
public class AngleMath {
//	public static float degreesToNormalizedHeading(float heading, float base) {
//		float diff = heading - base;
//		if (diff < 0) {
//			diff += 360;
//		}
//		else if (diff > 360) {
//			diff -= 360;
//		}
//		return limitTheta(clockwiseToCounterClockwise(diff));
//	}

	/** Calculates the average angle between two angles */
	public static float meanAngle(float angle1, float angle2) {
		float diff = mapAngle(angle2 - angle1);
//		float sum = angle1 + angle1 + diff;
//		float average = mapAngle(sum/2);
//		return average;
		return mapAngle(angle1 + diff/2);
	}

	/** Maps the angle to range [-pi, pi]
	 */
	public static float mapAngle(float angle) {
		while (angle > Math.PI) {
			return angle - 2*(float)Math.PI;
		}
		while (angle < -Math.PI) {
			return angle + 2*(float)Math.PI;
		}
		return angle;
	}

	public static float[] polarToCartesian(float radius, float angle) {
		float[] pos = new float[2];
		pos[0] = radius*(float)Math.cos(angle);
		pos[1] = radius*(float)Math.sin(angle);
		return pos;
	}
}
