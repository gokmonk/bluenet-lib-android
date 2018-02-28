package nl.dobots.bluenet.ble.base.structs;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;
import nl.dobots.bluenet.ble.base.utils.PartialTime;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

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

	public static final int TYPE_UNKNOWN =   0;
	public static final int TYPE_V1 =        1;
	public static final int TYPE_STATE =     2;
	public static final int TYPE_ERROR =     3;
	public static final int TYPE_EXT_STATE = 4;
	public static final int TYPE_EXT_ERROR = 5;
	public static final int TYPE_SETUP =     6;


	// use BleLog.getInstance().setLogLevelPerTag(CrownstoneServiceData.class.getCanonicalName(), <NEW_LOG_LEVEL>)
	// to change the log level
	private static final int LOG_LEVEL = Log.WARN;

	private static final String TAG = CrownstoneServiceData.class.getCanonicalName();

	public CrownstoneServiceData() {
		super();
	}

	public CrownstoneServiceData(String json) throws JSONException {
		super(json);
	}

	public CrownstoneServiceData(JSONObject obj) throws JSONException {
		this(obj.toString());
	}

	public boolean parseBytes(byte[] bytes, boolean encrypted, byte[] key) {
		// Includes the service UUID (first 2 bytes)
		getLogger().LOGv(TAG, "serviceData: " + BleUtils.bytesToString(bytes));
		if (bytes.length < 3) {
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		try {
			setServiceUuid(BleUtils.toUint16(bb.getShort()));

			int opCode = BleUtils.toUint8(bb.get());
			setOpCode(opCode);
			setType(TYPE_UNKNOWN);

			switch (opCode) {
				case 1:
					return parseDataV1(bytes, bb.position(), encrypted, key);
				case 3:
					return parseDataV3(bytes, bb.position(), encrypted, key);
				case 4:
					return parseDataV4(bytes, bb.position(), encrypted, key);
				default:
					setOpCode(0);
					return false;
			}
		} catch (BufferUnderflowException e) {
			getLogger().LOGe(TAG, "failed to parse");
//			e.printStackTrace();
			return false;
		}
	}


	private boolean parseDataV1(byte[] bytes, int offset, boolean encrypted, byte[] key) {
		if (bytes.length - offset < 16) {
			return false;
		}

		setType(TYPE_V1);

		// First parse without decrypting
		ByteBuffer bb = ByteBuffer.wrap(bytes, offset, bytes.length-offset);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// First try to parse without decrypting (in case encryption is disable or Crownstone is in setup mode)
		if (!parseDecryptedDataV1(bb)) {
			return false;
		}
		// Check if data is setup mode data, if so we're done
		if (isSetupPacket()) {
			return true;
		}
		if (encrypted) {
			// Decrypt
			byte[] decryptedBytes = BleBaseEncryption.decryptEcb(bytes, offset, key);
			if (decryptedBytes == null) {
				return false;
			}
			// Parse again after decrypting data
			ByteBuffer decryptedBB = ByteBuffer.wrap(decryptedBytes);
			decryptedBB.order(ByteOrder.LITTLE_ENDIAN);
			if (!parseDecryptedDataV1(decryptedBB)) {
				return false;
			}
		}
		return true;
	}

	private boolean parseDecryptedDataV1(ByteBuffer bb) {
		int crownstoneId = BleUtils.toUint16(bb.getShort());
		setSwitchState(BleUtils.toUint8(bb.get()));
		byte flagBitmask = bb.get();
		setFlagNewData(     (flagBitmask & (1L << 0)) != 0);
		setFlagExternalData((flagBitmask & (1L << 1)) != 0);
		setFlagError(       (flagBitmask & (1L << 2)) != 0);
		setFlagSetup(       (flagBitmask & (1L << 7)) != 0);
		setTemperature(bb.get());
		setPowerUsageReal(bb.getInt() / 1000.0);
//		setPowerUsageApparent(getPowerUsageReal()); // Assume power factor of 1.0
		setAccumulatedEnergy(bb.getInt());
		byte[] randomBytes = new byte[3];
		bb.get(randomBytes);
		setChangingBytes(randomBytes);

		if (getFlagExternalData()) {
			setCrownstoneId(-1);
			setCrownstoneExternalId(crownstoneId);
		}
		else {
			setCrownstoneId(crownstoneId);
			setCrownstoneExternalId(-1);
		}
		return true;
	}


	private boolean parseDataV3(byte[] bytes, int offset, boolean encrypted, byte[] key) {
		if (bytes.length - offset < 16) {
			return false;
		}

		// First decrypt if encryption is enabled.
		ByteBuffer bb;
		if (encrypted) {
			byte[] decryptedBytes = BleBaseEncryption.decryptEcb(bytes, offset, key);
			if (decryptedBytes == null) {
				return false;
			}
			bb = ByteBuffer.wrap(decryptedBytes);
//			getLogger().LOGv(TAG, "decrypted: " + BleUtils.bytesToString(decryptedBytes));
		}
		else {
			bb = ByteBuffer.wrap(bytes, offset, bytes.length-offset);
		}
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// Parse the (decrypted) data.
		int type = BleUtils.toUint8(bb.get());
		switch (type) {
			case 0: { // state
				return parseStatePacket(bb, false);
			}
			case 1: { // error
				return parseErrorPacket(bb, false);
			}
			case 2: { // ext state
				return parseStatePacket(bb, true);
			}
			case 3: { // ext error
				return parseErrorPacket(bb, true);
			}
			default:
				// Use this as default, else service data of another sphere gets no service data at all,
				// which can result in getting old service data being copied into it.
				return parseStatePacket(bb, false);
//				return false;
		}
	}

	private boolean parseDataV4(byte[] bytes, int offset, boolean encrypted, byte[] key) {
		// Never encrypted
		if (bytes.length - offset < 16) {
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(bytes, offset, bytes.length-offset);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		int type = BleUtils.toUint8(bb.get());
		switch (type) {
			case 0: {
				setFlagSetup(true);
				setCrownstoneId(BleUtils.toUint8(bb.get()));
				setCrownstoneExternalId(-1);
				parseFlagsBitmask(bb.get());
				setTemperature(bb.get());
				double powerFactor = bb.get() / 127.0;
				setPowerFactor(powerFactor);
				double powerUsageReal = bb.getShort() / 8.0;
				setPowerUsageReal(powerUsageReal);
				setPowerUsageApparent(powerRealToApparent(powerUsageReal, powerFactor));
				parseErrorBitmask(BleUtils.toUint32(bb.getInt()));
				byte[] counter = new byte[1];
				bb.get(counter);
				setChangingBytes(counter);
				// TODO: 4 more bytes left.
				return true;
			}
			default:
				return false;
		}
	}

	private boolean parseStatePacket(ByteBuffer bb, boolean external) {
		if (external) {
			setFlagExternalData(true);
			setCrownstoneId(-1);
			setCrownstoneExternalId(BleUtils.toUint8(bb.get()));
			setType(TYPE_EXT_STATE);
		}
		else {
			setCrownstoneId(BleUtils.toUint8(bb.get()));
			setCrownstoneExternalId(-1);
			setType(TYPE_STATE);
		}
		setSwitchState(BleUtils.toUint8(bb.get()));
		parseFlagsBitmask(bb.get());
		setTemperature(bb.get());
		double powerFactor = bb.get() / 127.0;
		setPowerFactor(powerFactor);
		double powerUsageReal = bb.getShort() / 8.0;
		setPowerUsageReal(powerUsageReal);
		setPowerUsageApparent(powerRealToApparent(powerUsageReal, powerFactor));
		double energyUsed = bb.getInt() * 64.0;
		setAccumulatedEnergy(energyUsed);
		parsePartialTimestamp(bb);
		int validation = BleUtils.toUint16(bb.getShort());
		if (validation != 0xFACE) {
			getLogger().LOGv(TAG, "validation mismatch: " + validation);
		}
		reconstructTimestamp();
		return true;
	}

	private boolean parseErrorPacket(ByteBuffer bb, boolean external) {
		if (external) {
			setFlagExternalData(true);
			setCrownstoneId(-1);
			setCrownstoneExternalId(BleUtils.toUint8(bb.get()));
			setType(TYPE_EXT_ERROR);
		}
		else {
			setCrownstoneId(BleUtils.toUint8(bb.get()));
			setCrownstoneExternalId(-1);
			setType(TYPE_ERROR);
		}
		parseErrorBitmask(BleUtils.toUint32(bb.getInt()));
		setErrorTimestamp(BleUtils.toUint32(bb.getInt()));
		parseFlagsBitmask(bb.get());
		setTemperature(bb.get());
		parsePartialTimestamp(bb);
		double powerUsageReal = bb.getShort() / 8.0;
		setPowerUsageReal(powerUsageReal);
//		setPowerUsageApparent(powerUsageReal); // Assume power factor of 1.0
		reconstructTimestamp();
		return true;
	}


	private void parseFlagsBitmask(byte flagBitmask) {
		setFlagDimmingAvailable((flagBitmask & (1L << 0)) != 0);
		setFlagDimmingAllowed(  (flagBitmask & (1L << 1)) != 0);
		setFlagError(           (flagBitmask & (1L << 2)) != 0);
		setFlagSwitchLocked(    (flagBitmask & (1L << 3)) != 0);
		setFlagTimeSet(         (flagBitmask & (1L << 4)) != 0);
	}

	private void parseErrorBitmask(long bitmask) {
		setErrorOverCurrent(      (bitmask & (1L << BluenetConfig.STATE_ERROR_POS_OVERCURRENT)) != 0);
		setErrorOverCurrentDimmer((bitmask & (1L << BluenetConfig.STATE_ERROR_POS_OVERCURRENT_DIMMER)) != 0);
		setErrorChipTemperature(  (bitmask & (1L << BluenetConfig.STATE_ERROR_POS_TEMP_CHIP)) != 0);
		setErrorDimmerTemperature((bitmask & (1L << BluenetConfig.STATE_ERROR_POS_TEMP_DIMMER)) != 0);
		setErrorDimmerFailureOn(  (bitmask & (1L << BluenetConfig.STATE_ERROR_POS_DIMMER_ON_FAILURE)) != 0);
		setErrorDimmerFailureOff( (bitmask & (1L << BluenetConfig.STATE_ERROR_POS_DIMMER_OFF_FAILURE)) != 0);
		setErrorBitMaskString(Long.toBinaryString(bitmask));
	}

	private void parsePartialTimestamp(ByteBuffer bb) {
		byte[] partialTimestamp = new byte[2];
		bb.get(partialTimestamp);
		setPartialTimestamp(BleUtils.toUint16(BleUtils.byteArrayToShort(partialTimestamp)));
		setChangingBytes(partialTimestamp);
	}

	private void reconstructTimestamp() {
		if (getFlagTimeSet()) {
			PartialTime partialTime = new PartialTime();
			long timestamp = partialTime.reconstructTimestamp(getPartialTimestamp());
			setReconstructedTimestamp(timestamp);
		}
	}

	private double powerRealToApparent(double realPower, double powerFactor) {
		if (powerFactor == 0.0) {
			powerFactor = 0.01;
		}
		return realPower / powerFactor;
	}



	private boolean isSetupPacket() {
		switch (getOpCode()) {
			case 0:
				return false;
			case 1: {
				getLogger().LOGv(TAG, "setupbit=" + getFlagSetup() + " id=" + getCrownstoneId() + " switch=" + getSwitchState() + " power=" + getPowerUsageReal() + " energy=" + getAccumulatedEnergy());
				return (getFlagSetup() && getCrownstoneId() == 0 && getSwitchState() == 0 && getPowerUsageReal() == 0 && getAccumulatedEnergy() == 0);
			}
			default:
				return getFlagSetup();
		}
	}

	public boolean isSetupMode() { return isSetupPacket(); }

	/** Copy data from old service data to current service data. Basically merging the two.
	 *
	 * This is handy for when service data is interleaved, with different kind of data in the different packets.
	 * Basically merging the two.
	 *
	 * @param old previous service data.
	 */
	public void copyFromOld(CrownstoneServiceData old) {
		if (old == null) {
			return;
		}
		if (getFlagExternalData() || old.getFlagExternalData()) {
			return;
		}
		int type = getType();
		int oldType = old.getType();
		getLogger().LOGv(TAG, "current:" + this.toString());
		getLogger().LOGv(TAG, "old:" + old.toString());
		if (type == TYPE_STATE && getFlagError()) {
			setErrorOverCurrent(old.getErrorOverCurrent());
			setErrorOverCurrentDimmer(old.getErrorOverCurrentDimmer());
			setErrorChipTemperature(old.getErrorChipTemperature());
			setErrorDimmerTemperature(old.getErrorDimmerTemperature());
			setErrorDimmerFailureOn(old.getErrorDimmerFailureOn());
			setErrorDimmerFailureOff(old.getErrorDimmerFailureOff());
			setErrorTimestamp(old.getErrorTimestamp());
		}
		if (!hasSwitchState()) {
			setSwitchState(old.getSwitchState());
		}
		if (!hasPowerFactor()) {
			setPowerFactor(old.getPowerFactor());
		}
		if (!hasAccumulatedEnergy()) {
			setAccumulatedEnergy(old.getAccumulatedEnergy());
		}
		// TODO: merge more..
	}


	/** Check if service data is empty
	 *
	 * @return true when service data object is empty.
	 */
	public boolean isEmpty() {
		return length() == 0;
	}


	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\\
	//%%%%%%%%%%                    Getters and setters of fields                       %%%%%%%%%%\\
	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\\

	public int getType() {
		try {
			return getInt("serviceDataType");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no type found");
			return 0;
		}
	}

	private void setType(int type) {
		try {
			put("serviceDataType", type);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add type");
			e.printStackTrace();
		}
	}

	public int getOpCode() {
		try {
			return getInt("opCode");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no opCode found");
			return 0;
		}
	}

	private void setOpCode(int opCode) {
		try {
			put("opCode", opCode);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add opCode");
			e.printStackTrace();
		}
	}

	public int getServiceUuid() {
		try {
			return getInt("serviceUuid");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no service uuid found");
			return 0;
		}
	}

	private void setServiceUuid(int serviceUuid) {
		try {
			put("serviceUuid", serviceUuid);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add service uuid");
			e.printStackTrace();
		}
	}

	public int getCrownstoneId() {
		try {
			return getInt("crownstoneId");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no crownstone id found");
			return 0;
		}
	}

	private void setCrownstoneId(int crownstoneId) {
		try {
			put("crownstoneId", crownstoneId);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add crownstone id");
			e.printStackTrace();
		}
	}

	public int getCrownstoneExternalId() {
		try {
			return getInt("crownstoneExternalId");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no ext crownstone id found");
			return 0;
		}
	}

	private void setCrownstoneExternalId(int crownstoneStateId) {
		try {
			put("crownstoneExternalId", crownstoneStateId);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add ext crownstone id");
			e.printStackTrace();
		}
	}

	public boolean hasSwitchState() {
		return has("switchState");
	}

	public int getSwitchState() {
		try {
			return getInt("switchState");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no switch state found");
			return 0;
		}
	}

	private void setSwitchState(int switchState) {
		setRelayState(BleUtils.isBitSet(switchState, 7));
		setPwm(switchState & ~(1 << 7));
		try {
			put("switchState", switchState);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add switch state");
			e.printStackTrace();
		}
	}

	public boolean getRelayState() {
		try {
			return getBoolean("relayState");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no relay state found");
			return false;
		}
	}

	private void setRelayState(boolean relayState) {
		try {
			put("relayState", relayState);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add relay state");
			e.printStackTrace();
		}
	}

	public int getPwm() {
		try {
			return getInt("pwm");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no pwm found");
			return 0;
		}
	}

	private void setPwm(int pwm) {
		try {
			put("pwm", pwm);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add pwm");
			e.printStackTrace();
		}
	}



	public long getReconstructedTimestamp() {
		try {
			return getLong("reconstructedTimestamp");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no reconstructed timestamp found");
			return -1;
		}
	}

	private void setReconstructedTimestamp(long timestamp) {
		try {
			put("reconstructedTimestamp", timestamp);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add reconstructed timestamp");
			e.printStackTrace();
		}
	}





	@Deprecated
	public boolean getFlagNewData() {
		final String entry = "flagNewData";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	@Deprecated
	private void setFlagNewData(boolean val) {
		final String entry = "flagNewData";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}


	public boolean getFlagExternalData() {
		final String entry = "flagExternalData";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagExternalData(boolean val) {
		final String entry = "flagExternalData";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagError() {
		final String entry = "flagError";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagError(boolean val) {
		final String entry = "flagError";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagSetup() {
		final String entry = "flagSetup";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagSetup(boolean val) {
		final String entry = "flagSetup";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagDimmingAvailable() {
		final String entry = "flagDimmingAvailable";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagDimmingAvailable(boolean val) {
		final String entry = "flagDimmingAvailable";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagDimmingAllowed() {
		final String entry = "flagDimmingAllowed";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagDimmingAllowed(boolean val) {
		final String entry = "flagDimmingAllowed";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagSwitchLocked() {
		final String entry = "flagSwitchLocked";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagSwitchLocked(boolean val) {
		final String entry = "flagSwitchLocked";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getFlagTimeSet() {
		final String entry = "flagTimeSet";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setFlagTimeSet(boolean val) {
		final String entry = "flagTimeSet";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}



	public String getErrorBitMaskString() {
		final String entry = "errorBitMaskString";
		try {
			return getString(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no error bitmask string found");
			return "";
		}
	}
	private void setErrorBitMaskString(String val) {
		final String entry = "errorBitMaskString";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorOverCurrent() {
		final String entry = "errorOverCurrent";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorOverCurrent(boolean val) {
		final String entry = "errorOverCurrent";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorOverCurrentDimmer() {
		final String entry = "errorOverCurrentDimmer";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorOverCurrentDimmer(boolean val) {
		final String entry = "errorOverCurrentDimmer";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorChipTemperature() {
		final String entry = "errorChipTemperature";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorChipTemperature(boolean val) {
		final String entry = "errorChipTemperature";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorDimmerTemperature() {
		final String entry = "errorDimmerTemperature";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorDimmerTemperature(boolean val) {
		final String entry = "errorDimmerTemperature";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorDimmerFailureOn() {
		final String entry = "errorDimmerFailureOn";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorDimmerFailureOn(boolean val) {
		final String entry = "errorDimmerFailureOn";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	public boolean getErrorDimmerFailureOff() {
		final String entry = "errorDimmerFailureOff";
		try {
			return getBoolean(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return false;
		}
	}
	private void setErrorDimmerFailureOff(boolean val) {
		final String entry = "errorDimmerFailureOff";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}

	private long getErrorTimestamp() {
		final String entry = "errorTimestamp";
		try {
			return getLong(entry);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "not found " + entry);
			return 0;
		}
	}
	private void setErrorTimestamp(long val) {
		final String entry = "errorTimestamp";
		try {
			put(entry, val);
		} catch (JSONException e) {
			getLogger().LOGe(TAG, "failed to add " + entry);
			e.printStackTrace();
		}
	}



//	public boolean isNewData() { return isNewData(getEventBitmask()); }
//	public static boolean isNewData(byte eventBitmask) {
//		return ((eventBitmask & (1L << 0)) != 0);
//	}
//
//	public boolean isExternalData() { return isExternalData(getEventBitmask()); }
//	public static boolean isExternalData(byte eventBitmask) {
//		return ((eventBitmask & (1L << 1)) != 0);
//	}
//
//	public boolean isErrorBit() { return isErrorBit(getEventBitmask()); }
//	public static boolean isErrorBit(byte eventBitmask) {
//		return ((eventBitmask & (1L << 2)) != 0);
//	}

//	public boolean isSetupMode() { return isSetupMode(getEventBitmask()); }
//	public static boolean isSetupMode(byte eventBitmask) {
//		return ((eventBitmask & (1L << 7)) != 0);
//	}

//	public static boolean isSetupMode(byte eventBitmask) {
//		return isSetupPacket();
//	}
//	private static boolean isSetupMode(byte eventBitmask) {
//		return ((eventBitmask & (1L << 7)) != 0);
//	}

	public byte getTemperature() {
		try {
			return (byte)getInt("temperature");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no temperature found");
			return 0;
		}
	}

	private void setTemperature(byte temperature) {
		try {
			put("temperature", temperature);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add temperature");
			e.printStackTrace();
		}
	}

	public boolean hasPowerFactor() {
		return has("powerFactor");
	}

	public double getPowerFactor() {
		try {
			return getDouble("powerFactor");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no power factor found");
			return 1.0;
		}
	}

	private void setPowerFactor(double powerFactor) {
		if (powerFactor == 0.0) {
			powerFactor = 1.0;
		}
		try {
			put("powerFactor", powerFactor);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add power factor");
			e.printStackTrace();
		}
	}

//	public boolean hasPowerUsage() {
//		return has("powerUsage");
//	}

	public double getPowerUsageReal() {
		try {
			return getDouble("powerUsageReal");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no real power usage found");
			return 0;
		}
	}

	private void setPowerUsageReal(double powerUsage) {
		try {
			put("powerUsageReal", powerUsage);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add real power usage");
			e.printStackTrace();
		}
	}

	public double getPowerUsageApparent() {
		try {
			return getDouble("powerUsageApparent");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no apparent power usage found");
			return 0;
		}
	}

	private void setPowerUsageApparent(double powerUsage) {
		try {
			put("powerUsageApparent", powerUsage);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add apparent power usage");
			e.printStackTrace();
		}
	}

	public boolean hasAccumulatedEnergy() {
		return has("accumulatedEnergy");
	}

	public double getAccumulatedEnergy() {
		try {
			return getDouble("accumulatedEnergy");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no accumulated energy found");
			return 0;
		}
	}

	private void setAccumulatedEnergy(double accumulatedEnergy) {
		try {
			put("accumulatedEnergy", accumulatedEnergy);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add accumulated energy");
			e.printStackTrace();
		}
	}

	public int getPartialTimestamp() {
		try {
			return getInt("partialTimestamp");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no partial timestamp found");
			return 0;
		}
	}

	private void setPartialTimestamp(int partialTimestamp) {
		try {
			put("partialTimestamp", partialTimestamp);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add partial timestamp");
			e.printStackTrace();
		}
	}

	public String getChangingBytes() {
		try {
			return getString("changingBytes");
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "no changing bytes found");
			return null;
		}
	}
	private void setChangingBytes(byte[] bytes) {
		try {
			put("changingBytes", BleUtils.bytesToEncodedString(bytes));
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add changing bytes");
			e.printStackTrace();
		}
	}
	private void setChangingBytes(String encodedString) {
		try {
			put("changingBytes", encodedString);
		} catch (JSONException e) {
			getLogger().LOGv(TAG, "failed to add changing bytes");
			e.printStackTrace();
		}
	}



	private BleLog getLogger() {
		BleLog logger = BleLog.getInstance();
		// update the log level to the default of this class if it hasn't been set already
		if (logger.getLogLevel(TAG) == null) {
			logger.setLogLevelPerTag(TAG, LOG_LEVEL);
		}
		return logger;
	}
}
