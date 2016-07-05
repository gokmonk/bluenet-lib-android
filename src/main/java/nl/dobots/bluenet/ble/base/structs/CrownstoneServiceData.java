package nl.dobots.bluenet.ble.base.structs;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 9-6-16
 *
 * @author Dominik Egger
 */
public class CrownstoneServiceData extends JSONObject {

	private static final String TAG = CrownstoneServiceData.class.getCanonicalName();

	public CrownstoneServiceData(byte[] bytes) {
		super();

		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.getShort(); // skip first two bytes (service UUID)
		setCrownstoneId(bb.getShort());
		setCrownstoneStateId(bb.getShort());
		setSwitchState((bb.get() & 0xff));
		setEventBitmask(bb.get());
		setTemperature(bb.get());
		bb.get(); // skip reserved
//		bb.getShort(); // skip reserved
		setPowerUsage(bb.getInt());
		setAccumulatedEnergy(bb.getInt());
	}

	public CrownstoneServiceData(String json) throws JSONException {
		super(json);
	}

	public CrownstoneServiceData(JSONObject obj) throws JSONException {
		this(obj.toString());
	}

	public int getCrownstoneId() {
		try {
			return getInt("crownstoneId");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no crownstone id found");
			return 0;
		}
	}

	private void setCrownstoneId(int crownstoneId) {
		try {
			put("crownstoneId", crownstoneId);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add crownstone id");
			e.printStackTrace();
		}
	}

	public int getCrownstoneStateId() {
		try {
			return getInt("crownstoneStateId");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no crownstone state id found");
			return 0;
		}
	}

	private void setCrownstoneStateId(int crownstoneStateId) {
		try {
			put("crownstoneStateId", crownstoneStateId);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add crownstone state id");
			e.printStackTrace();
		}
	}

	public int getSwitchState() {
		try {
			return getInt("switchState");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no switch state found");
			return 0;
		}
	}

	private void setSwitchState(int switchState) {
		try {
			put("switchState", switchState);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add switch state");
			e.printStackTrace();
		}
	}

	public byte getEventBitmask() {
		try {
			return (byte)getInt("eventBitmask");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no event bitmask found");
			return 0;
		}
	}

	private void setEventBitmask(byte eventBitmask) {
		try {
			put("eventBitmask", eventBitmask);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add event bitmask");
			e.printStackTrace();
		}
	}

	public byte getTemperature() {
		try {
			return (byte)getInt("temperature");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no temperature found");
			return 0;
		}
	}

	private void setTemperature(byte temperature) {
		try {
			put("temperature", temperature);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add temperature");
			e.printStackTrace();
		}
	}

	public int getPowerUsage() {
		try {
			return getInt("powerUsage");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no power usage found");
			return 0;
		}
	}

	private void setPowerUsage(int powerUsage) {
		try {
			put("powerUsage", powerUsage);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add power usage");
			e.printStackTrace();
		}
	}

	public int getAccumulatedEnergy() {
		try {
			return getInt("accumulatedEnergy");
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "no accumulated energy found");
			return 0;
		}
	}

	private void setAccumulatedEnergy(int accumulatedEnergy) {
		try {
			put("accumulatedEnergy", accumulatedEnergy);
		} catch (JSONException e) {
			BleLog.LOGd(TAG, "failed to add accumulated energy");
			e.printStackTrace();
		}
	}
}
