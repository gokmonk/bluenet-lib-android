package nl.dobots.bluenet.ble.base;

import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IAlertCallback;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.BleAlertState;
import nl.dobots.bluenet.ble.base.structs.BleConfiguration;
import nl.dobots.bluenet.ble.base.structs.BleMeshMessage;
import nl.dobots.bluenet.ble.base.structs.BleTrackedDevice;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshData;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshHubData;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshScanData;
import nl.dobots.bluenet.ble.cfg.BleTypes;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.core.BleCoreTypes;
import nl.dobots.bluenet.ble.extended.callbacks.IStringCallback;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

public class BleBase extends BleCore {

	private static final String TAG = BleBase.class.getCanonicalName();

	// handler used for delayed execution, e.g. a to get the configuration we need to write first
	// to the select configuration characteristic, then wait for a moment for the device to process
	// the request before reading from the get configution characteristic
	private Handler _timeoutHandler = new Handler();

	/**
	 * Start an endless scan, without defining any UUIDs to filter for. the scan will continue
	 * until stopEndlessScan is called. The function will convert a received device from JSON into
	 * a BleDevice object, the advertisement package will be parsed and included into the object.
	 *
	 * @param callback the callback to be notified if devices are detected
	 * @return true if the scan was started, false otherwise
	 */
	public boolean startEndlessScan(final IDataCallback callback) {
		return this.startEndlessScan(new String[]{}, callback);
	}

	/**
	 * Start an endless scan, without defining any UUIDs to filter for. the scan will continue
	 * until stopEndlessScan is called. The function will parse the advertisement package
	 * and include it into the json object received from the android os.
	 *
	 * Additionally, a list of UUIDs can be specified to filter. As a result, only devices with
	 * the given UUIDs will be returned.
	 *
	 * @param callback the callback to be notified if devices are detected
	 * @param uuids a list of UUIDs to filter for
	 * @return true if the scan was started, false otherwise
	 */
	public boolean startEndlessScan(String[] uuids,  final IDataCallback callback) {
		// wrap the status callback to do some pre-processing of the scan result data
		return super.startEndlessScan(uuids, new IDataCallback() {

			@Override
			public void onError(int error) {
				callback.onError(error);
			}

			@Override
			public void onData(final JSONObject json) {
				byte[] advertisement = BleCore.getBytes(json, BleCoreTypes.PROPERTY_ADVERTISEMENT);

//				if (Build.VERSION.SDK_INT >= 21) {
//					ScanRecord scanRecord = ScanRecord.parseFromBytes(advertisement);
//				}

				parseAdvertisement(advertisement, 0xFF, new IByteArrayCallback() {
					@Override
					public void onSuccess(byte[] result) {
						int companyID = BleUtils.byteArrayToShort(result, 0);
						if (companyID == BluenetConfig.APPLE_COMPANY_ID) {
							parseIBeaconData(json, result);
						}
						if (companyID == BluenetConfig.DOBOTS_COMPANY_ID) {
							parseDoBotsData(json, result);
						}
					}

					@Override
					public void onError(int error) {
						BleLog.LOGd(TAG, "json: " + json.toString());
					}
				});

				callback.onData(json);
			}
		});
	}

	private void parseDoBotsData(JSONObject json, byte[] manufacData) {
		ByteBuffer bb = ByteBuffer.wrap(manufacData);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		int companyId = bb.getShort();

		if (companyId == BluenetConfig.DOBOTS_COMPANY_ID) {
			try {
				int deviceType = bb.get();
				switch (deviceType) {
					case BluenetConfig.DEVICE_CROWNSTONE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE, true);
						break;
					}
					case BluenetConfig.DEVICE_DOBEACON: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_DOBEACON, true);
						break;
					}
					case BluenetConfig.DEVICE_FRIDGE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_FRIDGE, true);
						break;
					}
				}
			} catch (Exception e) {
				BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE, true);
				BleLog.LOGd(TAG, "old advertisement package: %s", json);
			}
		}
	}

	/**
	 * Helper function to parse iBeacon data from a byte array into a JSON object
	 * @param scanResult the json object in which the data should be included
	 * @param manufacData the byte array containing the ibeacon data
	 */
	private void parseIBeaconData(JSONObject scanResult, byte[] manufacData) {

		ByteBuffer bb = ByteBuffer.wrap(manufacData);

		bb.order(ByteOrder.LITTLE_ENDIAN);
		int companyId = bb.getShort();

		bb.order(ByteOrder.BIG_ENDIAN);
		int advertisementId = bb.getShort();

		if (companyId == BluenetConfig.APPLE_COMPANY_ID && advertisementId == BluenetConfig.IBEACON_ADVERTISEMENT_ID) {
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_IS_IBEACON, true);
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_PROXIMITY_UUID, new UUID(bb.getLong(), bb.getLong()));
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_MAJOR, bb.getShort());
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_MINOR, bb.getShort());
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_CALIBRATED_RSSI, bb.get());
		} else {
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_IS_IBEACON, false);
		}

	}

	/**
	 * Discover the available services and characteristics of the device. Parse the received
	 * JSON object and call the callbacks onDiscovery function with service UUID and characteristic
	 * UUID for each discovered characteristic. Once the discovery completes, the onSuccess is
	 * called or the onError if an error occurs
	 *
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * closeDevice with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
 	 * @param address the MAC address of the device
	 * @param callback the callback used to report discovered services and characteristics
	 */
	public void discoverServices(String address, final IDiscoveryCallback callback) {
		super.discoverServices(address, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				try {
					JSONArray services = json.getJSONArray(BleCoreTypes.PROPERTY_SERVICES_LIST);
					for (int i = 0; i < services.length(); i++) {
						JSONObject service = services.getJSONObject(i);
						String serviceUuid = service.getString(BleCoreTypes.PROPERTY_SERVICE_UUID);
						JSONArray characteristics = service.getJSONArray(BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST);
						for (int j = 0; j < characteristics.length(); j++) {
							JSONObject charac = characteristics.getJSONObject(j);
							String characteristicUuid = charac.getString(BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID);
							BleLog.LOGd(TAG, "found service %s with characteristic %s", serviceUuid, characteristicUuid);
							callback.onDiscovery(serviceUuid, characteristicUuid);
						}
					}
					callback.onSuccess();
				} catch (JSONException e) {
					BleLog.LOGe(TAG, "failed to parse discovery json");
					callback.onError(BleErrors.ERROR_JSON_PARSING);
					e.printStackTrace();
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}



	// Crownstone specific characteristic operations

	/**
	 * Read the temperature characteristic on the device and return the temperature as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read Temperature at service %s and characteristic %s", BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_TEMPERATURE_UUID);
		read(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_TEMPERATURE_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read temperature characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleLog.LOGd(TAG, "temperature: %d", bytes[0]);
				callback.onSuccess(bytes[0]);
			}
		});
	}

	/**
	 * Write the given value to the PWM characteristic on the device
	 * @param address the MAC address of the device
	 * @param value the pwm value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePWM(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to pwm characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to pwm characteristic");
						callback.onError(error);
					}
				}
		);
	}

	/**
	 * Read the pwm characteristic on the device and return the current pwm value as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPWM(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read pwm at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read pwm characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				int result = BleUtils.signedToUnsignedByte(bytes[0]);
				BleLog.LOGd(TAG, "pwm: %d", result);
				callback.onSuccess(result);
			}
		});
	}

	/**
	 * Write to the device scan characteristic to start or stop a scan for BLE devices. After
	 * starting a scan, it will run indefinite until this function is called again to stop it.
	 * @param address the MAC address of the device
	 * @param scan true to start the scan, false to stop
	 * @param callback the callback which will be informed about success or failure
	 */
	public void scanDevices(String address, boolean scan, final IStatusCallback callback) {
		int value = scan ? 1 : 0;
		BleLog.LOGd(TAG, "writeScanDevices: write %d at service %s and characteristic %s", value, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_DEVICE_SCAN_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_DEVICE_SCAN_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to writeScanDevices characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to writeScanDevices characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the device list characteristic to get the list of scanned BLE devices from the device.
	 * Need to call scanDevices first to start and to stop the scan.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the list on success, or an error otherwise
	 */
	public void listScannedDevices(String address, final IByteArrayCallback callback) {
		BleLog.LOGd(TAG, "read device list at service %s and characteristic %s", BluenetConfig.CHAR_DEVICE_LIST_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID);
		read(address, BluenetConfig.CHAR_DEVICE_LIST_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read device list characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleLog.LOGd(TAG, "device list: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	/**
	 * Read the current consumption characteristic to get the current consumption value from the device.
	 * Need to have called at least once sampleCurrent before a valid current consumption can be
	 * read.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readCurrentConsumption(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read current consumption at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_CONSUMPTION_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_CONSUMPTION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read current consumption characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				// todo: check if current consumption is only 1 byte
				int value = BleUtils.signedToUnsignedByte(bytes[0]);
				BleLog.LOGd(TAG, "current consumption: %d", value);
				callback.onSuccess(value);
			}
		});
	}

	/**
	 * Write to the sample current characteristic to start the current sampling. The value parameter
	 * determines what result is expected. This is necessary to get a valid result for the calls to
	 * readCurrentConsumption and readCurrentCurve
	 * @param address the MAC address of the device
	 * @param value determines the type of sampling, can be either SAMPLE_CURRENT_CONSUMPTION or
	 *              SAMPLE_CURRENT_CURVE
	 * @param callback the callback which will be informed about success or failure
	 */
	public void sampleCurrent(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "sample current: write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_SAMPLE_CURRENT_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_SAMPLE_CURRENT_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to sample current characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to sample current characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the current curve characteristic to get the current curve from the device. Need to have
	 * called at least once sampleCurrent before a valid current curve can be read.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the curve on success, or an error otherwise
	 */
	public void readCurrentCurve(String address, final IByteArrayCallback callback) {
		BleLog.LOGd(TAG, "read current curve at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_CURVE_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_CURVE_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read current curve characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleLog.LOGd(TAG, "current curve: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
			}
		});
	}

	/**
	 * Write the floor to the configuration
	 * @param address the MAC address of the device
	 * @param value the new floor value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setFloor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_FLOOR, 1, new byte[] {(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the floor from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getFloor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_FLOOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int floor = configuration.getPayload()[0];
					BleLog.LOGd(TAG, "floor: %d", floor);
					callback.onSuccess(floor);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the wifi value to the configuration
	 * @param address the MAC address of the device
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String address, String value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_WIFI, value.length(), value.getBytes());
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the ip from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(String address, final IStringCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_WIFI, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					BleLog.LOGe(TAG, "empty name received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload());
					BleLog.LOGd(TAG, "device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 * @param address the MAC address of the device
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_TX_POWER, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the tx power from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_TX_POWER, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int txPower = configuration.getPayload()[0];
					BleLog.LOGd(TAG, "tx power: %d", txPower);
					callback.onSuccess(txPower);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	// constant used to convert the advertisement interval from ms to the unit expected by the
	// characteristic (increments of 0.625 ms)
	private static final double ADVERTISEMENT_INCREMENT = 0.625;

	/**
	 * Write the advertisement interval (in ms) to the configuration
	 * @param address the MAC address of the device
	 * @param value advertisement interval (in ms)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(String address, int value, final IStatusCallback callback) {
		// convert ms to value used by the crownstone (which is in increments of 0.625 ms)
		int advertisementInterval = (int)Math.floor(value / ADVERTISEMENT_INCREMENT);
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_ADV_INTERVAL, 2, BleUtils.shortToByteArray(advertisementInterval));
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the advertisement interval from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_ADV_INTERVAL, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int advertisementInterval = (int) (BleUtils.byteArrayToShort(configuration.getPayload()) * ADVERTISEMENT_INCREMENT);
					BleLog.LOGd(TAG, "advertisement interval: %d", advertisementInterval);
					callback.onSuccess(advertisementInterval);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon major to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_IBEACON_MAJOR, 2, BleUtils.shortToByteArray(value));
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the beacon major from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_IBEACON_MAJOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int major = BleUtils.byteArrayToShort(configuration.getPayload());
					BleLog.LOGd(TAG, "major: %d", major);
					callback.onSuccess(major);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon minor to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_IBEACON_MINOR, 2, BleUtils.shortToByteArray(value));
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the beacon minor from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_IBEACON_MINOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int minor = BleUtils.byteArrayToShort(configuration.getPayload());
					BleLog.LOGd(TAG, "major: %d", minor);
					callback.onSuccess(minor);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon calibrated rssi (rssi at 1m) to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_IBEACON_RSSI, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the beacon calibrated rssi (rssi at 1m) from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_IBEACON_RSSI, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int calibratedRssi = configuration.getPayload()[0];
					BleLog.LOGd(TAG, "rssi at 1 m: %d", calibratedRssi);
					callback.onSuccess(calibratedRssi);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon proximity UUID to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String address, String value, final IStatusCallback callback) {
		byte[] bytes = BleUtils.uuidToBytes(value);
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_IBEACON_PROXIMITY_UUID, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the beacon proximity UUID from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_IBEACON_PROXIMITY_UUID, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 16) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String proximityUuid = BleUtils.bytesToUuid(configuration.getPayload());
					BleLog.LOGd(TAG, "proximity UUID: %s", proximityUuid);
					callback.onSuccess(proximityUuid);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the device name to the configuration
	 * @param address the MAC address of the device
	 * @param value new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_NAME, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the device name from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getDeviceName(String address, final IStringCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_NAME, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					BleLog.LOGe(TAG, "empty name received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload());
					BleLog.LOGd(TAG, "device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the device type to the configuration
	 * @param address the MAC address of the device
	 * @param value new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceType(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_DEVICE_TYPE, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the device type from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getDeviceType(String address, final IStringCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_DEVICE_TYPE, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					BleLog.LOGe(TAG, "empty device type received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String deviceType = new String(configuration.getPayload());
					BleLog.LOGd(TAG, "device type: %s", deviceType);
					callback.onSuccess(deviceType);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the room to the configuration
	 * @param address the MAC address of the device
	 * @param value new room
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setRoom(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_ROOM, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the room from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getRoom(String address, final IStringCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_ROOM, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					BleLog.LOGe(TAG, "empty room received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String room = new String(configuration.getPayload());
					BleLog.LOGd(TAG, "room: %s", room);
					callback.onSuccess(room);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the minimum environment temperature to the configuration
	 * @param address the MAC address of the device
	 * @param value new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_MIN_ENV_TEMP, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the minimum environment temperature from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_MIN_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getPayload()[0];
					BleLog.LOGd(TAG, "min environment temperature: %d", temperature);
					callback.onSuccess(temperature);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the maximum environment temperature to the configuration
	 * @param address the MAC address of the device
	 * @param value new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BluenetConfig.CONFIG_TYPE_MAX_ENV_TEMP, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the maximum environment temperature from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		getConfiguration(address, BluenetConfig.CONFIG_TYPE_MAX_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					BleLog.LOGe(TAG, "Wrong length parameter: %s", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getPayload()[0];
					BleLog.LOGd(TAG, "max environment temperature: %d", temperature);
					callback.onSuccess(temperature);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Wrapper function which first calls select configuration, and on success calls the get configuration
	 *
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum BleConfiguration Types in BluenetConfig.
	 * @param callback callback function to be called with the read configuration object
	 */
	public void getConfiguration(final String address, int configurationType, final IConfigurationCallback callback) {
		selectConfiguration(address, configurationType, new IStatusCallback() {
			@Override
			public void onSuccess() {
				// todo: do we need a timeout here?
				_timeoutHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						readConfiguration(address, callback);
					}
				}, 50);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the configuration value to the set configuration characteristic. the configuration is a
	 * BleConfiguration object which starts with a byte for the configuration type, then 1 byte
	 * reserved for byte alignment, 2 bytes for the length of the payload data, and the payload
	 * data
	 * @param address the address of the device
	 * @param configuration configuration to be written to the set configuration characteristic
	 * @param callback callback function to be called on success or error
	 */
	public void writeConfiguration(String address, BleConfiguration configuration, final IStatusCallback callback) {
		byte[] bytes = configuration.toArray();
		BleLog.LOGd(TAG, "configuration: write %s at service %s and characteristic %s", Arrays.toString(bytes), BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_SET_CONFIGURATION_UUID);
		write(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_SET_CONFIGURATION_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to configuration characteristic");
						// we need to give the crownstone some time to handle the config write,
						// because it needs to access persistent memory in order to store the new
						// config value
						_timeoutHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								callback.onSuccess();
							}
						}, 200);
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to configuration characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write to the select configuration characteristic to select a configuration that we want to
	 * read afterwards. Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum BleConfiguration Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void selectConfiguration(String address, int configurationType, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "select configuration: write %d at service %s and characteristic %s", configurationType, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_SELECT_CONFIGURATION_UUID);
		write(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_SELECT_CONFIGURATION_UUID, new byte[]{(byte) configurationType},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to select configuration characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to select configuration characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the get configuration characteristic to get the configuration value which was
	 * previously selected. Need to call selectConfiguration first to select a configuration value
	 * to be read.
	 * Note: Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * Consider using @getConfiguration instead
	 * @param address the address of the device
	 * @param callback callback function to be called with the read configuration object
	 */
	public void readConfiguration(String address, final IConfigurationCallback callback) {
		BleLog.LOGd(TAG, "read configuration at service %s and characteristic %s", BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_GET_CONFIGURATION_UUID);
		read(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_GET_CONFIGURATION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read configuration characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleConfiguration configuration = new BleConfiguration(bytes);
				BleLog.LOGd(TAG, "read configuration: %d", configuration.toString());
				callback.onSuccess(configuration);
			}
		});
	}

	/**
	 * Write the given mesh message to the mesh characteristic. the mesh message will be
	 * forwarded by the device into the mesh network
	 * @param address the address of the device
	 * @param message the mesh message to be sent into the mesh
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(String address, BleMeshMessage message, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "mesh message: write %s at service %s and characteristic %s", message.toString(), BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_MESH_UUID);
		write(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_MESH_UUID, message.toArray(),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to mesh message characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to mesh message characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write the given value to the current limit characteristic
	 * @param address the address of the device
	 * @param value new current limit
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeCurrentLimit(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "current limit: write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_LIMIT_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_LIMIT_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to current limit characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to current limit characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the current limit characteristic to get the current limit
	 * @param address the address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readCurrentLimit(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read current limit at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_LIMIT_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_CURRENT_LIMIT_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read current limit characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				int currentLimit = BleUtils.signedToUnsignedByte(bytes[0]);
				BleLog.LOGd(TAG, "current limit: %d", currentLimit);
				callback.onSuccess(currentLimit);
			}
		});
	}

	/**
	 * Read the list tracked devices characteristic to get the list of tracked devices
	 * @param address the address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		BleLog.LOGd(TAG, "read tracked devices at service %s and characteristic %s", BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_LIST_TRACKED_DEVICES_UUID);
		read(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_LIST_TRACKED_DEVICES_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read tracked devices characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleLog.LOGd(TAG, "tracked devices: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	/**
	 * Write the given device to the add tracked device characteristic to add it as a device
	 * to the list of tracked devices
	 * @param address the address of the device
	 * @param device device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(String address, BleTrackedDevice device, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "add tracked device: write %d at service %s and characteristic %s", device.toString(), BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_ADD_TRACKED_DEVICE_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_ADD_TRACKED_DEVICE_UUID, device.toArray(),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to add tracked device characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to add tracked device characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write the reset value to the reset characteristic
	 * @param address the address of the device
	 * @param value reset value, can be either RESET_DEFAULT or RESET_BOOTLOADER
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeReset(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "reset: write %d at service %s and characteristic %s", value, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_RESET_UUID);
		write(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_RESET_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to reset characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to reset characteristic");
						callback.onError(error);
					}
				});
	}

	public void readAlert(String address, final IAlertCallback callback) {
		BleLog.LOGd(TAG, "read Alert at service %s and characteristic %s", BluenetConfig.ALERT_SERVICE_UUID, BluenetConfig.CHAR_NEW_ALERT_UUID);
		read(address, BluenetConfig.ALERT_SERVICE_UUID, BluenetConfig.CHAR_NEW_ALERT_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read Alert characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				try {
					int alertValue = BleUtils.signedToUnsignedByte(bytes[0]);
					int num = BleUtils.signedToUnsignedByte(bytes[1]);
					BleLog.LOGd(TAG, "Alert: %d, num: %d", alertValue, num);
					callback.onSuccess(new BleAlertState(alertValue, num));
				} catch (Exception e) {
					callback.onError(BleErrors.ERROR_RETURN_VALUE_PARSING);
				}
			}
		});
	}

	// only used to reset alerts (set value to 0)
	public void writeAlert(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "Alert: write %d at service %s and characteristic %s", value, BluenetConfig.ALERT_SERVICE_UUID, BluenetConfig.CHAR_NEW_ALERT_UUID);
		write(address, BluenetConfig.ALERT_SERVICE_UUID, BluenetConfig.CHAR_NEW_ALERT_UUID, BleUtils.shortToByteArray(value),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to Alert characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to Alert characteristic");
						callback.onError(error);
					}
				});
	}

	public void readMeshData(String address, final IMeshDataCallback callback) {
		BleLog.LOGd(TAG, "Reading mesh data...");
		read(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						byte[] bytes = BleCore.getValue(json);
						BleMeshData meshData = new BleMeshData(bytes);
						BleMeshHubData meshHubData = BleMeshDataFactory.fromBytes(meshData.getData());
						callback.onData(meshHubData);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	ByteBuffer notificationBuffer = ByteBuffer.allocate(100);

	public void subscribeMeshData(final String address, final IMeshDataCallback callback) {
		BleLog.LOGd(TAG, "subscribing to mesh data...");
		subscribe(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
			new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					if (BleCore.getStatus(json) == BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY) {

						final byte[] notificationBytes = BleCore.getValue(json);

						BleMeshHubData meshData;
						BleMeshData meshDataPart = new BleMeshData(notificationBytes);
						switch (meshDataPart.getOpCode()) {
							case 0x0: {
//								meshData = BleMeshDataFactory.fromBytes(notificationBytes);
//								meshData = BleMeshDataFactory.fromBytes(meshDataPart.getData());
								break;
							}
							case 0x21: {
								notificationBuffer.put(meshDataPart.getData());
								break;
							}
							case 0x22: {
								notificationBuffer.put(meshDataPart.getData());
								meshData = BleMeshDataFactory.fromBytes(notificationBuffer.array());
								callback.onData((BleMeshHubData) meshData);
								notificationBuffer.clear();
								break;
							}
						}


						// unfortunately notifications only report up to 23 bytes, so we can't use
						// the value provided in the notification directly. however, we can now
						// read the characteristic to get the full content

//						readMeshData(address, callback);
//						read(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
//							new IDataCallback() {
//								@Override
//								public void onData(JSONObject json) {
//									byte[] bytes = BleCore.getValue(json);
//
//									BleMeshData meshData = BleMeshDataFactory.fromBytes(bytes);
//
//									for (int i = 0; i < notificationBytes.length; ++i) {
//										if (notificationBytes[i] != bytes[i]) {
//											BleLog.LOGe(TAG, "did not receive same mesh message as in notifaction");
//											final BleMeshData notificationMeshData = BleMeshDataFactory.fromBytes(notificationBytes);
//											BleLog.LOGe(TAG, "notification was from: %s", ((BleMeshScanData)notificationMeshData).getSourceAddress());
//											BleLog.LOGe(TAG, "read is from: %s", ((BleMeshScanData)meshData).getSourceAddress());
//											break;
//										}
//									}
//
//									callback.onData(meshData);
//								}
//
//								@Override
//								public void onError(int error) {
//									callback.onError(error);
//								}
//							});




//						byte[] bytes = BleCore.getValue(json);
//						BleMeshData meshData = BleMeshDataFactory.fromBytes(bytes);
//						callback.onData(meshData);
					}
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
		});
	}

	public void unsubscribeMeshData(final String address, final IMeshDataCallback callback) {
		BleLog.LOGd(TAG, "subscribing to mesh data...");
		unsubscribe(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}



/*
	public void readTemplate(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read xxx at service %s and characteristic %s", BluenetConfig.yyy, BluenetConfig.zzz);
		read(address, BluenetConfig.yyy, BluenetConfig.zzz, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read xxx characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				BleLog.LOGd(TAG, "xxx: %d", uuu);
				callback.onSuccess(uuu);
			}
		});
	}


	public void writeTemplate(String address, int value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "xxx: write %d at service %s and characteristic %s", uuu, BluenetConfig.yyy, BluenetConfig.zzz);
		write(address, BluenetConfig.yyy, BluenetConfig.zzz, new byte[]{uuu},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to xxx characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to xxx characteristic");
						callback.onError(error);
					}
				});
	}
*/

}
