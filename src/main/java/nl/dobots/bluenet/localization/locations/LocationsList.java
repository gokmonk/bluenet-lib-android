package nl.dobots.bluenet.localization.locations;

import java.util.ArrayList;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 7-8-15
 *
 * @author Dominik Egger
 */
public class LocationsList extends ArrayList<Location> {

	public Location findLocation(String deviceAddress) {

		for (Location location : this) {
			if (location.containsBeacon(deviceAddress)) {
				return location;
			}
		}

		return null;
	}

	public Location getLocation(String name) {
		for (Location location : this) {
			if (location.getName().equals(name)) {
				return location;
			}
		}

		return null;
	}

}
