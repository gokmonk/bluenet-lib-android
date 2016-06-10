package nl.dobots.bluenet.ble.extended.structs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;

import nl.dobots.bluenet.ble.cfg.BleTypes;

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
public class BleDevice {

	private static final String TAG = BleDevice.class.getCanonicalName();

	// to be checked, obtained from http://data.altbeacon.org/android-distance.json
	// device specific constants, these ones are for a Nexus 4/5
	private static final double coeff1 = 0.42093;
	private static final double coeff2 = 6.9476;
	private static final double coeff3 = 0.54992;

	// todo: should we support a device being more than one type?
	enum DeviceType {
		unknown,
		crownstone,
		guidestone,
		ibeacon,
		fridge
	}

	private static long expirationTime = 5000;

	private String _address;
	private String _name;
	private int _rssi;
	private DeviceType _type;

	private Integer _averageRssi;
	private ArrayList<RssiMeasurement> _rssiHistory = new ArrayList<>();

	private Double _distance;

	private int _major;
	private int _minor;
	private UUID _proximityUuid;
	private int _calibratedRssi;

	public BleDevice(String address, String name, int rssi) {
		this._address = address;
		this._name = name;
		this._rssi = rssi;
		this._type = DeviceType.unknown;

		updateRssiValue((new Date()).getTime(), rssi);
//		_rssiHistory.add(new RssiMeasurement(rssi, (new Date()).getTime()));
	}

	private BleDevice(String address, String name, int rssi, DeviceType type, int major, int minor, UUID proximityUuid, int calibratedRssi) {
		_address = address;
		_name = name;
		_rssi = rssi;
		_type = type;
		_major = major;
		_minor = minor;
		_proximityUuid = proximityUuid;
		_calibratedRssi = calibratedRssi;

		updateRssiValue((new Date()).getTime(), rssi);
//		_rssiHistory.add(new RssiMeasurement(rssi, (new Date()).getTime()));
	}

	public BleDevice(JSONObject json) throws JSONException {
		this._address = json.getString(BleTypes.PROPERTY_ADDRESS);
		this._name = json.getString(BleTypes.PROPERTY_NAME);
		this._rssi = json.getInt(BleTypes.PROPERTY_RSSI);
		this._type = determineDeviceType(json);
		if (isIBeacon()) {
			this._major = json.getInt(BleTypes.PROPERTY_MAJOR);
			this._minor = json.getInt(BleTypes.PROPERTY_MINOR);
			this._proximityUuid = (UUID) json.get(BleTypes.PROPERTY_PROXIMITY_UUID);
			this._calibratedRssi = json.getInt(BleTypes.PROPERTY_CALIBRATED_RSSI);
		}

		updateRssiValue((new Date()).getTime(), this._rssi);
//		_rssiHistory.add(new RssiMeasurement(this._rssi, (new Date()).getTime()));
	}

	public BleDevice clone() {
		return new BleDevice(this._address, this._name, this._rssi, this._type,
				this._major, this._minor, this._proximityUuid, this._calibratedRssi);
	}

	public boolean isIBeacon() {
		return _type == DeviceType.ibeacon || _type == DeviceType.guidestone;
	}

	public boolean isGuidestone() {
		return _type == DeviceType.guidestone;
	}

	public boolean isCrownstone() {
		return _type == DeviceType.crownstone;
	}

	public boolean isFridge() {
		return _type == DeviceType.fridge;
	}

	private DeviceType determineDeviceType(JSONObject json) throws JSONException {
		if (json.has(BleTypes.PROPERTY_IS_CROWNSTONE)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_CROWNSTONE)) {
				return DeviceType.crownstone;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_GUIDESTONE)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_GUIDESTONE)) {
				return DeviceType.guidestone;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_FRIDGE)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_FRIDGE)) {
				return DeviceType.fridge;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_IBEACON)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_IBEACON)) {
				return DeviceType.ibeacon;
			}
		}
		return DeviceType.unknown;
	}

	public String getAddress() {
		return _address;
	}

	public void setAddress(String address) {
		this._address = address;
	}

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		this._name = name;
	}

	public int getRssi() {
		return _rssi;
	}

	public void setRssi(int rssi) {
//		this._rssi = rssi;
		updateRssiValue(new Date().getTime(), rssi);
	}

	public int getMajor() {
		return _major;
	}

	public void setMajor(int major) {
		_major = major;
	}

	public int getMinor() {
		return _minor;
	}

	public void setMinor(int minor) {
		_minor = minor;
	}

	public UUID getProximityUuid() {
		return _proximityUuid;
	}

	public void setProximityUuid(UUID proximityUuid) {
		_proximityUuid = proximityUuid;
	}

	public int getCalibratedRssi() {
		return _calibratedRssi;
	}

	public void setCalibratedRssi(int calibratedRssi) {
		_calibratedRssi = calibratedRssi;
	}

	public synchronized void updateRssiValue(long timestamp, int rssi) {
		if (rssi != 127) {
			this._rssi = rssi;
			// todo: maybe also add a capacity limit, in case we only scan and never check
			//   for rssi or distance values, we would only add and never remove any values
			//   and the history will only grow
			_rssiHistory.add(new RssiMeasurement(rssi, timestamp));
		}

		_averageRssi = null;
	}

	private synchronized boolean refreshHistory() {
		Date now = new Date();
		ArrayList<RssiMeasurement> newHistory = new ArrayList<>();

		boolean hasChange = false;
		for (RssiMeasurement measurement : _rssiHistory) {
			if (measurement.timestamp + expirationTime > now.getTime()) {
				newHistory.add(measurement);
			} else {
//				if (!hasChange) {
//					Log.d(TAG, "dropping old rssi measurements");
//				}
				hasChange = true;
			}
		}

		this._rssiHistory = newHistory;
		return hasChange;
	}

	public int getOccurrences() {
		refreshHistory();
		return _rssiHistory.size();
	}

	Comparator<RssiMeasurement> timeSorter = new Comparator<RssiMeasurement>() {
		@Override
		public int compare(RssiMeasurement lhs, RssiMeasurement rhs) {
			return lhs.timestamp.compareTo(rhs.timestamp);
		}
	};

	Comparator<RssiMeasurement> rssiSorter = new Comparator<RssiMeasurement>() {
		@Override
		public int compare(RssiMeasurement lhs, RssiMeasurement rhs) {
			return lhs.rssi.compareTo(rhs.rssi);
		}
	};

	private synchronized ArrayList<RssiMeasurement> getHistoryClone() {
		return (ArrayList<RssiMeasurement>) _rssiHistory.clone();
	}

	public ArrayList<RssiMeasurement> getTimeSortedHistory() {
		refreshHistory();
		ArrayList<RssiMeasurement> clone = getHistoryClone();
		Collections.sort(clone, timeSorter);
		return clone;
	}

	public ArrayList<RssiMeasurement> getRssiSortedHistory() {
		refreshHistory();
		ArrayList<RssiMeasurement> clone = getHistoryClone();
		Collections.sort(clone, rssiSorter);
		return clone;
	}

	/**
	 * set expiration time of rssi measurments. globaly used for every beacon
	 * @param expirationTime
	 */
	public static void setExpirationTime(long expirationTime) {
		BleDevice.expirationTime = expirationTime;
	}

	private synchronized void calculateAverageRssi() {
		Collections.sort(this._rssiHistory, rssiSorter);

		int size = _rssiHistory.size();
		int startIndex = 0;
		int endIndex = size - 1;
		if (size > 2) {
			startIndex = size / 10 + 1;
			endIndex = size - size / 10 - 2;
		}

		double sum = 0;

		for (int i = startIndex; i <= endIndex; i++) {
			sum += _rssiHistory.get(i).rssi;
		}

		_averageRssi = (int)(sum / (endIndex - startIndex + 1));
		_distance = null;
	}

	public synchronized int getAverageRssi() {
		// avoid recalculating if no new measurement was received in between
		// is set to null in updateRssiValue
		if (_averageRssi == null) {
			refresh();
		}
		return _averageRssi;
	}

	/**
	 * Holds rssi value with timestamp for history
	 */
	private class RssiMeasurement {
		Integer rssi;
		Long timestamp;

		private RssiMeasurement() {}

		public RssiMeasurement(int rssi, long timestamp) {
			this.rssi = rssi;
			this.timestamp = timestamp;
		}
	}

	/**
	 * returns -1 in case of error
	 * @return
	 */
	private void calculateDistance() {
		if (!isIBeacon() || _averageRssi == 0 || _calibratedRssi == 0) {
			_distance = -1.0;
		} else {
			double ratio = _averageRssi * 1.0 / _calibratedRssi;
			if (ratio < 1) {
				_distance = Math.pow(ratio, 10);
			} else {
				_distance = coeff1 * Math.pow(ratio, coeff2) + coeff3;
			}
		}
	}

	/**
	 * only use if isIBeacon returns true. Otherwise, this function will always return -1
	 * returns -1 in case of error
	 * @return
	 */
	public synchronized double getDistance() {
		// avoid recalculating if no new measurement was received in between
		// is set to null in calculateAverageRssi
		if (_distance == null || _averageRssi == null) {
			refresh();
		}
		return _distance;
	}

	public void refresh() {
//		if (refreshHistory()) {
		refreshHistory();

		calculateAverageRssi();
		calculateDistance();
//		}

	}

}
