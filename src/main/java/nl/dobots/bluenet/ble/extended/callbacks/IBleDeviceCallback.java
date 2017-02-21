package nl.dobots.bluenet.ble.extended.callbacks;

import nl.dobots.bluenet.ble.base.callbacks.IBaseCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;

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
 * Created on 15-7-15
 *
 * @author Dominik Egger
 */
public interface IBleDeviceCallback extends IStatusCallback {

	void onDeviceScanned(BleDevice device);

}
