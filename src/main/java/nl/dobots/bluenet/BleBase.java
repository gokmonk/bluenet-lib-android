package nl.dobots.bluenet;

import android.bluetooth.le.ScanRecord;
import android.os.Build;
import android.os.Handler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import nl.dobots.bluenet.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.callbacks.IDataCallback;
import nl.dobots.bluenet.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.callbacks.IIntegerCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.callbacks.IStringCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.core.BleCore;
import nl.dobots.bluenet.core.BleCoreTypes;
import nl.dobots.bluenet.extended.BleExtTypes;
import nl.dobots.bluenet.structs.BleConfiguration;
import nl.dobots.bluenet.structs.BleMeshMessage;
import nl.dobots.bluenet.structs.BleTrackedDevice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BleBase extends BleCore {

	private Handler _timeoutHandler = new Handler();

	public boolean startEndlessScan(final IDataCallback callback) {
		return this.startEndlessScan(new String[]{}, callback);
	}

	public boolean startEndlessScan(String[] uuids,  final IDataCallback callback) {
		// wrap the status callback to do some pre-processing of the scan result data
		return super.startEndlessScan(uuids, new IDataCallback() {

			@Override
			public void onError(int error) {
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] advertisement = BleUtils.getBytes(json, BleCoreTypes.PROPERTY_ADVERTISEMENT);

//				if (Build.VERSION.SDK_INT >= 21) {
//					ScanRecord scanRecord = ScanRecord.parseFromBytes(advertisement);
//				}

				byte[] manufacData = parseAdvertisement(advertisement, 0xFF);
				if (manufacData != null) {
					int companyID = BleUtils.byteArrayToShort(manufacData, 0);
					if (companyID == BleTypes.APPLE_COMPANY_ID) {
						parseIBeaconData(json, manufacData);
					}
					if (companyID == BleTypes.DOBOTS_COMPANY_ID) {
						BleUtils.addProperty(json, BleBaseTypes.PROPERTY_IS_CROWNSTONE, true);
					}
				} else {
					LOGd("json: " + json.toString());
				}
				callback.onData(json);

			}
		});
	}

	private void parseIBeaconData(JSONObject scanResult, byte[] manufacData) {

		ByteBuffer bb = ByteBuffer.wrap(manufacData);

		bb.order(ByteOrder.LITTLE_ENDIAN);
		int companyId = bb.getShort();

		bb.order(ByteOrder.BIG_ENDIAN);
		int advertisementId = bb.getShort();

		if (companyId == BleTypes.APPLE_COMPANY_ID && advertisementId == BleTypes.IBEACON_ADVERTISEMENT_ID) {
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_IS_IBEACON, true);
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_PROXIMITY_UUID, new UUID(bb.getLong(), bb.getLong()));
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_MAJOR, bb.getShort());
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_MINOR, bb.getShort());
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_CALIBRATED_RSSI, bb.get());
		} else {
			BleUtils.addProperty(scanResult, BleBaseTypes.PROPERTY_IS_IBEACON, false);
		}

	}

	public void discoverServices(String address, final IDiscoveryCallback callback) {
		// todo: CONTINUE here
		super.discoverServices(address, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				try {
					JSONArray services = json.getJSONArray(BleExtTypes.PROPERTY_SERVICES_LIST);
					for (int i = 0; i < services.length(); i++) {
						JSONObject service = services.getJSONObject(i);
						String serviceUuid = service.getString(BleExtTypes.PROPERTY_SERVICE_UUID);
						JSONArray characteristics = service.getJSONArray(BleExtTypes.PROPERTY_CHARACTERISTICS_LIST);
						for (int j = 0; j < characteristics.length(); j++) {
							JSONObject charac = characteristics.getJSONObject(j);
							String characteristicUuid = charac.getString(BleExtTypes.PROPERTY_CHARACTERISTIC_UUID);
							LOGd("found service %s with characteristic %s", serviceUuid, characteristicUuid);
							callback.onDiscovery(serviceUuid, characteristicUuid);
						}
					}
					callback.onSuccess();
				} catch (JSONException e) {
					LOGe("failed to parse discovery json");
					callback.onError(BleExtTypes.ERROR_JSON_PARSING);
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

	public void readTemperature(String address, final IIntegerCallback callback) {
		LOGd("read Temperature at service %s and characteristic %s", BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_TEMPERATURE_UUID);
		read(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_TEMPERATURE_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				LOGe("Failed to read temperature characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				LOGd("temperature: %d", bytes[0]);
				callback.onSuccess(bytes[0]);
			}
		});
	}

	public void writePWM(String address, int value, final IStatusCallback callback) {
		LOGd("write %d at service %s and characteristic %s", value, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_PWM_UUID);
		write(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_PWM_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to pwm characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to pwm characteristic");
						callback.onError(error);
					}

//          @Override
//          public void onData(JSONObject json) {
//              // not interested
//
//              // theoretically, could check if the value that was actually written is the value we
//              // wanted to write. but do we need to?
//          }
				});
	}

	public void readPWM(String address, final IIntegerCallback callback) {
		LOGd("read pwm at service %s and characteristic %s", BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_PWM_UUID);
		read(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_PWM_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				LOGe("Failed to read pwm characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				int result = BleUtils.signedToUnsignedByte(bytes[0]);
				LOGd("pwm: %d", result);
				callback.onSuccess(result);
			}
		});
	}

	public void scanDevices(String address, boolean scan, final IStatusCallback callback) {
		int value = scan ? 1 : 0;
		LOGd("writeScanDevices: write %d at service %s and characteristic %s", value, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_DEVICE_SCAN_UUID);
		write(address, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_DEVICE_SCAN_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to writeScanDevices characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to writeScanDevices characteristic");
						callback.onError(error);
					}
				});
	}

	public void listScannedDevices(String address, final IByteArrayCallback callback) {
		LOGd("read device list at service %s and characteristic %s", BleTypes.CHAR_DEVICE_LIST_UUID, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID);
		read(address, BleTypes.CHAR_DEVICE_LIST_UUID, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read device list characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				LOGd("device list: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	public void readCurrentConsumption(String address, final IIntegerCallback callback) {
		LOGd("read current consumption at service %s and characteristic %s", BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_CONSUMPTION_UUID);
		read(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_CONSUMPTION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read current consumption characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				// todo: check if current consumption is only 1 byte
				int value = BleUtils.signedToUnsignedByte(bytes[0]);
				LOGd("current consumption: %d", value);
				callback.onSuccess(value);
			}
		});
	}

	public void sampleCurrent(String address, int value, final IStatusCallback callback) {
		LOGd("sample current: write %d at service %s and characteristic %s", value, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_SAMPLE_CURRENT_UUID);
		write(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_SAMPLE_CURRENT_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to sample current characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to sample current characteristic");
						callback.onError(error);
					}
				});
	}

	public void readCurrentCurve(String address, final IByteArrayCallback callback) {
		LOGd("read current curve at service %s and characteristic %s", BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_CURVE_UUID);
		read(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_CURVE_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read current curve characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				LOGd("current curve: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
			}
		});
	}

	public void setFloor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_FLOOR, 1, new byte[] {(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	public void getFloor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_FLOOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int floor = configuration.getPayload()[0];
					LOGd("floor: %d", floor);
					callback.onSuccess(floor);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setWifi(String address, String value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_WIFI, value.length(), value.getBytes());
		writeConfiguration(address, configuration, callback);
	}

	public void getIp(String address, final IStringCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_WIFI, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					LOGe("empty name received!");
					onError(BleCoreTypes.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload());
					LOGd("device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setTxPower(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_TX_POWER, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	public void getTxPower(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_TX_POWER, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int txPower = configuration.getPayload()[0];
					LOGd("tx power: %d", txPower);
					callback.onSuccess(txPower);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private static final double ADVERTISEMENT_INCREMENT = 0.625;

	/**
	 * Set the advertisement interval (in ms)
	 * @param address
	 * @param value advertisement interval (in ms)
	 * @param callback
	 */
	public void setAdvertisementInterval(String address, int value, final IStatusCallback callback) {
		// convert ms to value used by the crownstone (which is in increments of 0.625 ms)
		int advertisementInterval = (int)Math.floor(value / ADVERTISEMENT_INCREMENT);
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_ADV_INTERVAL, 2, BleUtils.shortToByteArray(advertisementInterval));
		writeConfiguration(address, configuration, callback);
	}

	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_ADV_INTERVAL, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int advertisementInterval = (int) (BleUtils.byteArrayToShort(configuration.getPayload()) * ADVERTISEMENT_INCREMENT);
					LOGd("advertisement interval: %d", advertisementInterval);
					callback.onSuccess(advertisementInterval);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setBeaconMajor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_IBEACON_MAJOR, 2, BleUtils.shortToByteArray(value));
		writeConfiguration(address, configuration, callback);
	}

	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_IBEACON_MAJOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int major = BleUtils.byteArrayToShort(configuration.getPayload());
					LOGd("major: %d", major);
					callback.onSuccess(major);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setBeaconMinor(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_IBEACON_MINOR, 2, BleUtils.shortToByteArray(value));
		writeConfiguration(address, configuration, callback);
	}

	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_IBEACON_MINOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 2) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int minor = BleUtils.byteArrayToShort(configuration.getPayload());
					LOGd("major: %d", minor);
					callback.onSuccess(minor);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setBeaconCalibratedRssi(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_IBEACON_RSSI, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_IBEACON_RSSI, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int calibratedRssi = configuration.getPayload()[0];
					LOGd("rssi at 1 m: %d", calibratedRssi);
					callback.onSuccess(calibratedRssi);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setBeaconProximityUuid(String address, String value, final IStatusCallback callback) {
		byte[] bytes = BleUtils.uuidToBytes(value);
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_IBEACON_PROXIMITY_UUID, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_IBEACON_PROXIMITY_UUID, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 16) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String proximityUuid = BleUtils.bytesToUuid(configuration.getPayload());
					LOGd("proximity UUID: %s", proximityUuid);
					callback.onSuccess(proximityUuid);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setDeviceName(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_NAME, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	public void getDeviceName(String address, final IStringCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_NAME, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					LOGe("empty name received!");
					onError(BleCoreTypes.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload());
					LOGd("device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setDeviceType(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_DEVICE_TYPE, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	public void getDeviceType(String address, final IStringCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_DEVICE_TYPE, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					LOGe("empty device type received!");
					onError(BleCoreTypes.ERROR_EMPTY_VALUE);
				} else {
					String deviceType = new String(configuration.getPayload());
					LOGd("device type: %s", deviceType);
					callback.onSuccess(deviceType);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setRoom(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes();
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_ROOM, bytes.length, bytes);
		writeConfiguration(address, configuration, callback);
	}

	public void getRoom(String address, final IStringCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_ROOM, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() == 0) {
					LOGe("empty room received!");
					onError(BleCoreTypes.ERROR_EMPTY_VALUE);
				} else {
					String room = new String(configuration.getPayload());
					LOGd("room: %s", room);
					callback.onSuccess(room);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setMinEnvTemp(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_MIN_ENV_TEMP, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_MIN_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getPayload()[0];
					LOGd("min environment temperature: %d", temperature);
					callback.onSuccess(temperature);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setMaxEnvTemp(String address, int value, final IStatusCallback callback) {
		BleConfiguration configuration = new BleConfiguration(BleTypes.CONFIG_TYPE_MAX_ENV_TEMP, 1, new byte[]{(byte)value});
		writeConfiguration(address, configuration, callback);
	}

	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		getConfiguration(address, BleTypes.CONFIG_TYPE_MAX_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(BleConfiguration configuration) {
				if (configuration.getLength() != 1) {
					LOGe("Wrong length parameter: %s", configuration.getLength());
					onError(BleCoreTypes.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getPayload()[0];
					LOGd("max environment temperature: %d", temperature);
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
	 * @param configurationType the configuration type, see enum BleConfiguration Types in BleTypes.
	 * @param callback callback functions to be called on success or error
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

	public void writeConfiguration(String address, BleConfiguration configuration, final IStatusCallback callback) {
		byte[] bytes = configuration.toArray();
		LOGd("configuration: write %s at service %s and characteristic %s", Arrays.toString(bytes), BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_SET_CONFIGURATION_UUID);
		write(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_SET_CONFIGURATION_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to configuration characteristic");
						// todo: do we need a timeout here?
//                      _timeoutHandler.postDelayed(new Runnable() {
//                          @Override
//                          public void run() {
						callback.onSuccess();
//                          }
//                      }, 500);
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to configuration characteristic");
						callback.onError(error);
					}
				});
	}

	public void selectConfiguration(String address, int configurationType, final IStatusCallback callback) {
		LOGd("select configuration: write %d at service %s and characteristic %s", configurationType, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_SELECT_CONFIGURATION_UUID);
		write(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_SELECT_CONFIGURATION_UUID, new byte[]{(byte) configurationType},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to select configuration characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to select configuration characteristic");
						callback.onError(error);
					}
				});
	}

	public void readConfiguration(String address, final IConfigurationCallback callback) {
		LOGd("read configuration at service %s and characteristic %s", BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_GET_CONFIGURATION_UUID);
		read(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_GET_CONFIGURATION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read configuration characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				BleConfiguration configuration = new BleConfiguration(bytes);
				LOGd("read configuration: %d", configuration.toString());
				callback.onSuccess(configuration);
			}
		});
	}

	public void writeMeshMessage(String address, BleMeshMessage message, final IStatusCallback callback) {
		LOGd("mesh message: write %s at service %s and characteristic %s", message.toString(), BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_MESH_UUID);
		write(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_MESH_UUID, message.toArray(),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to mesh message characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to mesh message characteristic");
						callback.onError(error);
					}
				});
	}

	public void writeCurrentLimit(String address, int value, final IStatusCallback callback) {
		LOGd("current limit: write %d at service %s and characteristic %s", value, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_LIMIT_UUID);
		write(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_LIMIT_UUID, new byte[]{(byte)value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to current limit characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to current limit characteristic");
						callback.onError(error);
					}
				});
	}

	public void readCurrentLimit(String address, final IIntegerCallback callback) {
		LOGd("read current limit at service %s and characteristic %s", BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_LIMIT_UUID);
		read(address, BleTypes.POWER_SERVICE_UUID, BleTypes.CHAR_CURRENT_LIMIT_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read current limit characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				int currentLimit = BleUtils.signedToUnsignedByte(bytes[0]);
				LOGd("current limit: %d", currentLimit);
				callback.onSuccess(currentLimit);
			}
		});
	}

	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		LOGd("read tracked devices at service %s and characteristic %s", BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_LIST_TRACKED_DEVICES_UUID);
		read(address, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_LIST_TRACKED_DEVICES_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read tracked devices characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				LOGd("tracked devices: %s", Arrays.toString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	public void addTrackedDevice(String address, BleTrackedDevice device, final IStatusCallback callback) {
		LOGd("add tracked device: write %d at service %s and characteristic %s", device.toString(), BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_ADD_TRACKED_DEVICE_UUID);
		write(address, BleTypes.INDOOR_LOCALIZATION_SERVICE_UUID, BleTypes.CHAR_ADD_TRACKED_DEVICE_UUID, device.toArray(),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to add tracked device characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to add tracked device characteristic");
						callback.onError(error);
					}
				});
	}

	public void writeReset(String address, int value, final IStatusCallback callback) {
		LOGd("reset: write %d at service %s and characteristic %s", value, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_RESET_UUID);
		write(address, BleTypes.GENERAL_SERVICE_UUID, BleTypes.CHAR_RESET_UUID, new byte[]{(byte)value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to reset characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to reset characteristic");
						callback.onError(error);
					}
				});
	}







/*
	public void readTemplate(String address, final IIntegerCallback callback) {
		LOGd("read xxx at service %s and characteristic %s", BleTypes.yyy, BleTypes.zzz);
		read(address, BleTypes.yyy, BleTypes.zzz, new IDataCallback() {
			@Override
			public void onError(int error) {
				LOGe("Failed to read xxx characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleUtils.getValue(json);
				LOGd("xxx: %d", uuu);
				callback.onSuccess(uuu);
			}
		});
	}


	public void writeTemplate(String address, int value, final IStatusCallback callback) {
		LOGd("xxx: write %d at service %s and characteristic %s", uuu, BleTypes.yyy, BleTypes.zzz);
		write(address, BleTypes.yyy, BleTypes.zzz, new byte[]{uuu},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						LOGd("Successfully written to xxx characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						LOGe("Failed to write to xxx characteristic");
						callback.onError(error);
					}
				});
	}
*/

}
