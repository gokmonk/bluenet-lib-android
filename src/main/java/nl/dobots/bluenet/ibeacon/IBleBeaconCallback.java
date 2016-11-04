package nl.dobots.bluenet.ibeacon;

import nl.dobots.bluenet.ble.base.callbacks.IBaseCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;

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
 * Created on 27-10-16
 *
 * @author Bart van Vliet
 */
public interface IBleBeaconCallback extends IBaseCallback {

	void onBeaconScanned(BleDevice device);



}
