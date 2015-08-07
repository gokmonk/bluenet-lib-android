package nl.dobots.bluenet.extended.structs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import nl.dobots.bluenet.extended.BleExtTypes;

/**
 * Created by dominik on 15-7-15.
 */
public class BleDevice {

	private static final String TAG = BleDevice.class.getCanonicalName();

	// to be checked, obtained from http://data.altbeacon.org/android-distance.json
	private static final double coeff1 = 0.42093;
	private static final double coeff2 = 6.9476;
	private static final double coeff3 = 0.54992;

	enum DeviceType {
		unknown,
		crownstone,
		dobeacon,
		ibeacon
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

	private Semaphore _historySemaphore = new Semaphore(1, true);
	private Semaphore _rssiSemaphore = new Semaphore(1, true);

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
		this._address = json.getString(BleExtTypes.PROPERTY_ADDRESS);
		this._name = json.getString(BleExtTypes.PROPERTY_NAME);
		this._rssi = json.getInt(BleExtTypes.PROPERTY_RSSI);
		this._type = determineDeviceType(json);
		if (isIBeacon()) {
			this._major = json.getInt(BleExtTypes.PROPERTY_MAJOR);
			this._minor = json.getInt(BleExtTypes.PROPERTY_MINOR);
			this._proximityUuid = (UUID) json.get(BleExtTypes.PROPERTY_PROXIMITY_UUID);
			this._calibratedRssi = json.getInt(BleExtTypes.PROPERTY_CALIBRATED_RSSI);
		}

		updateRssiValue((new Date()).getTime(), this._rssi);
//		_rssiHistory.add(new RssiMeasurement(this._rssi, (new Date()).getTime()));
	}

	public BleDevice clone() {
		return new BleDevice(this._address, this._name, this._rssi, this._type,
				this._major, this._minor, this._proximityUuid, this._calibratedRssi);
	}

	public boolean isIBeacon() {
		return _type == DeviceType.ibeacon || _type == DeviceType.dobeacon;
	}

	public boolean isCrownstone() {
		return _type == DeviceType.crownstone;
	}

	private DeviceType determineDeviceType(JSONObject json) throws JSONException {
		if (json.has(BleExtTypes.PROPERTY_IS_CROWNSTONE)) {
			if (json.getBoolean(BleExtTypes.PROPERTY_IS_CROWNSTONE)) {
				return DeviceType.crownstone;
			}
		}
		if (json.has(BleExtTypes.PROPERTY_IS_IBEACON)) {
			if (json.getBoolean(BleExtTypes.PROPERTY_IS_IBEACON)) {
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
		this._rssi = rssi;
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

	public void updateRssiValue(long timestamp, int rssi) {
		try {
			_historySemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		}
		if (rssi != 127) {
			this._rssi = rssi;
			_rssiHistory.add(new RssiMeasurement(rssi, (new Date()).getTime()));
		}
		_historySemaphore.release();

		try {
			_rssiSemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		}
		_averageRssi = null;
		_rssiSemaphore.release();
	}

	private boolean refreshHistory() {
		try {
			_historySemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
		Date now = new Date();
		ArrayList<RssiMeasurement> newHistory = new ArrayList<>();

		boolean hasChange = false;
		for (RssiMeasurement measurement : _rssiHistory) {
			if (measurement.timestamp + expirationTime > now.getTime()) {
				newHistory.add(measurement);
			} else {
				hasChange = true;
			}
		}

		this._rssiHistory = newHistory;
		_historySemaphore.release();
		return hasChange;
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

	private ArrayList<RssiMeasurement> getHistoryClone() {
		try {
			_historySemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		ArrayList<RssiMeasurement> result = (ArrayList<RssiMeasurement>) _rssiHistory.clone();
		_historySemaphore.release();
		return result;
	}

	private ArrayList<RssiMeasurement> getTimeSortedHistory() {
		refreshHistory();
		ArrayList<RssiMeasurement> clone = getHistoryClone();
		Collections.sort(clone, timeSorter);
		return clone;
	}

	private ArrayList<RssiMeasurement> getRssiSortedHistory() {
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

	private void calculateAverageRssi() {
//		if (!refreshHistory() && _averageRssi != null) return;
		try {
			_historySemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		}

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
		_historySemaphore.release();

		_averageRssi = (int)(sum / (endIndex - startIndex + 1));
		_distance = null;
	}

	public int getAverageRssi() {
		// avoid recalculating if no new measurement was received in between
		// is set to null in updateRssiValue
//		if (_averageRssi == null) {
//			calculateAverageRssi();
//		}
		try {
			_rssiSemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return 0;
		}
		if (_averageRssi == null) {
			refresh();
		}
		int averageRssi = _averageRssi;
		_rssiSemaphore.release();
		return averageRssi;
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
	public double getDistance() {
//		getAverageRssi();
//		// avoid recalculating if no new measurement was received in between
//		// is set to null in calculateAverageRssi
//		if (_distance == null) {
//			calculateDistance();
//		}

		try {
			_rssiSemaphore.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return -1;
		}
		if (_distance == null || _averageRssi == null) {
			refresh();
		}
		double distance = _distance;
		_rssiSemaphore.release();
		return distance;
	}

	public void refresh() {
//		if (refreshHistory()) {
		refreshHistory();

		calculateAverageRssi();
		calculateDistance();
//		}

	}

}
