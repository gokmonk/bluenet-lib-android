package nl.dobots.bluenet.ble.base.structs;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleUtils;

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
 * Created on 22-9-15
 *
 * @author Dominik Egger
 */
public class BleAlertState {

	private int alertNum;

	private boolean temperatureLowActive;
	private boolean temperatureHighActive;

	public BleAlertState(int state, int num) {
		alertNum = num;
		temperatureLowActive = BleUtils.isBitSet(state, BluenetConfig.ALERT_TEMP_LOW_POS);
		temperatureHighActive = BleUtils.isBitSet(state, BluenetConfig.ALERT_TEMP_HIGH_POS);
	}

	@Override
	public String toString() {
		return String.format("Temp Low: %b, Temp High: %b", temperatureLowActive, temperatureHighActive);
	}

	public boolean isTemperatureLowActive() {
		return temperatureLowActive;
	}

	public boolean isTemperatureHighActive() {
		return temperatureHighActive;
	}
}
