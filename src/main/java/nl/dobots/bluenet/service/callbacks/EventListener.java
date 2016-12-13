package nl.dobots.bluenet.service.callbacks;

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
 * Created on 19-8-15
 *
 * @author Dominik Egger
 */
public interface EventListener {

	enum Event {
		BLUETOOTH_TURNED_OFF,
		BLUETOOTH_NOT_ENABLED,
		BLUETOOTH_INITIALIZED,
		BLUETOOTH_START_SCAN_ERROR,
		BLUETOOTH_STOP_SCAN_ERROR,
		BLE_PERMISSIONS_MISSING,
		BLE_PERMISSIONS_GRANTED
	}

	void onEvent(Event event);

}
