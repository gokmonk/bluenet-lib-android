package nl.dobots.bluenet.ble.base;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IAlertCallback;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStateCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.ISubscribeCallback;
import nl.dobots.bluenet.ble.base.structs.AlertState;
import nl.dobots.bluenet.ble.base.structs.CommandMsg;
import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.base.structs.EncryptionSessionData;
import nl.dobots.bluenet.ble.base.structs.MeshMsg;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.base.structs.StateMsg;
import nl.dobots.bluenet.ble.base.structs.TrackedDeviceMsg;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshData;
import nl.dobots.bluenet.ble.base.structs.mesh.BleMeshHubData;
import nl.dobots.bluenet.ble.cfg.BleTypes;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.core.BleCoreTypes;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

public class BleBase extends BleCore {

	private static final String TAG = BleBase.class.getCanonicalName();

	// handler used for delayed execution, e.g. a to get the configuration we need to write first
	// to the select configuration characteristic, then wait for a moment for the device to process
	// the request before reading from the get configuration characteristic
//	private Handler _timeoutHandler = new Handler();
	private Handler _timeoutHandler;


	private BleBaseEncryption _encryption = new BleBaseEncryption();
	private boolean _encryptionEnabled = false;
	private EncryptionKeys _encryptionKeys = null;
	private EncryptionSessionData _encryptionSessionData = null;

	/** Hashmap of all subscribers, based on characeristic UUID */
	private HashMap<UUID, ArrayList<IDataCallback>> _subscribers = new HashMap<>();

	/**
	 * "global" subscribe callback will be given to all subscribe calls of the BleCore. The
	 * gatt notifications (or errors) will then be delegated based on the characteristic uuid to all
	 * subscribers of that uuid
	 */
	ISubscribeCallback notificationCallback = new ISubscribeCallback() {
		@Override
		public void onData(UUID uuidService, UUID uuidCharacteristic, JSONObject data) {
			for (IDataCallback callback : getSubscribers(uuidCharacteristic)) {
				callback.onData(data);
			}
		}

		@Override
		public void onError(UUID uuidService, UUID uuidCharacteristic, int error) {
			for (IDataCallback callback : getSubscribers(uuidCharacteristic)) {
				callback.onError(error);
			}
		}
	};

	public BleBase() {
		// create handler with its own thread
		HandlerThread handlerThread = new HandlerThread("BleBaseHandler");
		handlerThread.start();
		_timeoutHandler = new Handler(handlerThread.getLooper());
	}

	public boolean enableEncryption(boolean enable) {
		_encryptionEnabled = enable;
		return true;
	}

	public boolean isEncryptionEnabled() {
		return _encryptionEnabled;
	}

	public void setEncryptionKeys(EncryptionKeys encryptionKeys) {
		_encryptionKeys = encryptionKeys;
	}

	public void setEncryptionSessionData(EncryptionSessionData sessionData) {
		_encryptionSessionData = sessionData;
	}

	@Override
	public void connectDevice(String address, int timeout, IDataCallback callback) {
		_subscribers.clear();
		super.connectDevice(address, timeout, callback);
	}

	@Override
	public boolean disconnectDevice(String address, IDataCallback callback) {
		_subscribers.clear();
		return super.disconnectDevice(address, callback);
	}

	@Override
	public boolean closeDevice(String address, boolean clearCache, IStatusCallback callback) {
		_subscribers.clear();
		return super.closeDevice(address, clearCache, callback);
	}

	@Override
	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, BleBaseEncryption.ACCESS_LEVEL_HIGHEST_AVAILABLE, callback);
	}

	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, char accessLevel, IStatusCallback callback) {
		if (_encryptionEnabled && accessLevel != BleBaseEncryption.ACCESS_LEVEL_ENCRYPTION_DISABLED) {
			// Just use highest available key
			EncryptionKeys.KeyAccessLevelPair keyAccessLevelPair = _encryptionKeys.getHighestKey();
			byte[] encryptedBytes = BleBaseEncryption.encryptCtr(value, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, keyAccessLevelPair.key, keyAccessLevelPair.accessLevel);
			return super.write(address, serviceUuid, characteristicUuid, encryptedBytes, callback);
		}
		return super.write(address, serviceUuid, characteristicUuid, value, callback);
	}

	@Override
	public boolean read(String address, String serviceUuid, String characteristicUuid, IDataCallback callback) {
		return read(address, serviceUuid, characteristicUuid, true, callback);
	}

	public boolean read(String address, String serviceUuid, String characteristicUuid, boolean useEncryption, final IDataCallback callback) {
		if (_encryptionEnabled && useEncryption) {
			IDataCallback encryptedCallback = new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					byte[] encryptedBytes = getValue(json);
					byte[] decryptedBytes = BleBaseEncryption.decryptCtr(encryptedBytes, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, _encryptionKeys);
					if (decryptedBytes == null) {
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						return;
					}
					setValue(json, decryptedBytes);
					callback.onData(json);
				}
				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			};
			return super.read(address, serviceUuid, characteristicUuid, encryptedCallback);
		}
		return super.read(address, serviceUuid, characteristicUuid, callback);
	}

	/**
	 * Start an endless scan, without defining any UUIDs to filter for. the scan will continue
	 * until stopEndlessScan is called. The function will convert a received device from JSON into
	 * a BleDevice object, the advertisement package will be parsed and included into the object.
	 *
	 * @param callback the callback to be notified if devices are detected
	 * @return true if the scan was started, false otherwise
	 */
	public boolean startEndlessScan(final IBleDeviceCallback callback) {
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
	 * @param serviceUuids a list of UUIDs to filter for
	 * @return true if the scan was started, false otherwise
	 */
	public boolean startEndlessScan(String[] serviceUuids,  final IBleDeviceCallback callback) {
		// wrap the status callback to do some pre-processing of the scan result data
		return super.startEndlessScan(serviceUuids, new IDataCallback() {

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
//						BleLog.LOGd(TAG, "json: " + json.toString());
					}
				});

				parseAdvertisement(advertisement, 0x16, new IByteArrayCallback() {
					@Override
					public void onSuccess(byte[] result) {
						int serviceUUID = BleUtils.byteArrayToShort(result, 0);
						if (serviceUUID == BluenetConfig.CROWNSTONE_SERVICE_DATA_UUID ||
								serviceUUID == BluenetConfig.GUIDESTONE_SERVICE_DATA_UUID) {
							parseServiceData(json, result);
						}
					}

					@Override
					public void onError(int error) {
//						BleLog.LOGd(TAG, "json: " + json.toString());
					}
				});

				BleDevice device;
				try {
					device = new BleDevice(json);
				} catch (JSONException e) {
//					BleLog.LOGe(TAG, "Failed to parse json into device! Err: " + e.getMessage());
//					BleLog.LOGd(TAG, "json: " + json.toString());
					return;
				}
				callback.onDeviceScanned(device);
			}
		});
	}

	/**
	 * Helper function to parse manufacturing data of an advertisement/scan response packet. Use
	 * @parseAdvertisement first to retrieve the manufacturing data (type 0xFF), and pass it to
	 * this function together with the json object. it will populate the json object with the data
	 * parsed from the manufacData array.
	 *
	 * @param json the json object in which the data should be included
	 * @param manufacData the byte array containing the manufacturing data
	 */
	@Deprecated
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
					case BluenetConfig.DEVICE_GUIDESTONE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_GUIDESTONE, true);
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
	 * Helper function to parse the crownstone service data of an advertisement/scan response packet.
	 * Use @parseAdvertisement first to retrieve the service data (type 0x16), and pass it to
	 * this function together with the json object. it will populate the json object with the data
	 * parsed from the serviceData array.
	 *
	 * @param json the json object in which the data should be included
	 * @param serviceData the byte array containing the service data
	 */
	private void parseServiceData(JSONObject json, byte[] serviceData) {
		ByteBuffer bb = ByteBuffer.wrap(serviceData);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		int serviceUUID = bb.getShort();

		if (serviceUUID == BluenetConfig.CROWNSTONE_SERVICE_DATA_UUID) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE, true);
//			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData(bb.array(), _encryptionEnabled, _encryptionKeys.getGuestKey());
			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData();
			if (crownstoneServiceData.parseBytes(bb.array(), _encryptionEnabled, EncryptionKeys.getGuestKey(_encryptionKeys))) {
				BleCore.addProperty(json, BleTypes.PROPERTY_SERVICE_DATA, crownstoneServiceData);
			}
		} else if (serviceUUID == BluenetConfig.GUIDESTONE_SERVICE_DATA_UUID) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_GUIDESTONE, true);
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

		// ibeacon data is in big endian format
		bb.order(ByteOrder.BIG_ENDIAN);
		// advertisement id is actually two seperate bits, first bit is the iBeacon type (0x02),
		// the second is the iBeacon length (0x15), but they are fixed to these values, so we can
		// compare them together
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

	/**
	 * Helper function to return the list of subscribers for a given uuid.
	 * @param uuid uuid for which the list should be returned
	 * @return list of subscribers
	 */
	private ArrayList<IDataCallback> getSubscribers(UUID uuid) {
		ArrayList<IDataCallback> callbacks = _subscribers.get(uuid);
		if (callbacks == null) {
			callbacks = new ArrayList<>();
			_subscribers.put(uuid, callbacks);
		}
		return callbacks;
	}

	/**
	 * Subscribe to a characteristic. This can be called several times for different callbacks. a
	 * list of subscribers is kept with a subscriberId, so that different functions can subscribe
	 * to the same characteristic.
	 * the statusCallback will return in the onSuccess call the subscriber id, this id will be needed
	 * to unsubscribe afterwards.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribe(String address, String serviceUuid, String characteristicUuid,
						  final IIntegerCallback statusCallback, final IDataCallback callback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		final ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		if (subscribers.isEmpty()) {
			subscribers.add(callback);
			if (!super.subscribe(address, serviceUuid, characteristicUuid, new IStatusCallback() {
				@Override
				public void onError(int error) {
					statusCallback.onError(error);
				}

				@Override
				public void onSuccess() {
					statusCallback.onSuccess(subscribers.indexOf(callback));
				}
			}, notificationCallback)) {
				subscribers.remove(callback);
			}
		} else {
			subscribers.add(callback);

//			JSONObject json = new JSONObject();
//			setStatus(json, BleCoreTypes.CHARACTERISTIC_SUBSCRIBED);
//			callback.onData(json);
			statusCallback.onSuccess(subscribers.indexOf(callback));
		}

	}

	class MultiPartNotificationCallback implements IDataCallback {

		IDataCallback callback;

		MultiPartNotificationCallback(IDataCallback callback) {
			this.callback = callback;
		}

		ByteBuffer buffer = ByteBuffer.allocate(BluenetConfig.BLE_MAX_MULTIPART_NOTIFICATION_LENGTH);
		int messageNr = 0;

		long timeStart;

		@Override
		public void onData(JSONObject json) {
			final byte[] notificationBytes = BleCore.getValue(json);
			int nr = BleUtils.toUint8(notificationBytes[0]);

			if (nr == 0) {
				timeStart = SystemClock.elapsedRealtime();
				if (messageNr != 0) {
					BleLog.LOGw(TAG, "notification reset!");
					buffer.clear();
				} else {
					// this is the first
				}
			} else if (messageNr > nr) {
				BleLog.LOGe(TAG, "fatal error");
				callback.onError(0);
				return;
			}
			messageNr = nr;

			buffer.put(notificationBytes, 1, notificationBytes.length - 1);

			if (messageNr == 0xFF) {
				BleLog.LOGd(TAG, "received last part");
				BleLog.LOGv(TAG, "duration: %d", SystemClock.elapsedRealtime() - timeStart);
				JSONObject combinedJson = new JSONObject();
				byte[] result = new byte[buffer.position()];
				buffer.rewind();
				buffer.get(result);

				BleCore.setValue(combinedJson, result);
				callback.onData(combinedJson);

				messageNr = 0;
				buffer.clear();
			} else {
				BleLog.LOGd(TAG, "received part %d", messageNr + 1);
			}
		}

		@Override
		public void onError(int error) {

		}
	}

	/**
	 * Subscribe to a characteristic. This can be called several times for different callbacks. a
	 * list of subscribers is kept with a subscriberId, so that different functions can subscribe
	 * to the same characteristic.
	 * the statusCallback will return in the onSuccess call the subscriber id, this id will be needed
	 * to unsubscribe afterwards.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribeMultipart(String address, String serviceUuid, String characteristicUuid,
						  final IIntegerCallback statusCallback, final IDataCallback callback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		final ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		final MultiPartNotificationCallback notificationCB = new MultiPartNotificationCallback(callback);

		if (subscribers.isEmpty()) {
			subscribers.add(notificationCB);
			if (!super.subscribe(address, serviceUuid, characteristicUuid, new IStatusCallback() {
				@Override
				public void onError(int error) {
					statusCallback.onError(error);
				}

				@Override
				public void onSuccess() {
					statusCallback.onSuccess(subscribers.indexOf(notificationCB));
				}
			}, notificationCallback)) {
				subscribers.remove(notificationCB);
			}
		} else {
			subscribers.add(notificationCB);

//			JSONObject json = new JSONObject();
//			setStatus(json, BleCoreTypes.CHARACTERISTIC_SUBSCRIBED);
//			callback.onData(json);
			statusCallback.onSuccess(subscribers.indexOf(callback));
		}

	}

	/**
	 * Unsubscribe from the characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribe call.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param subscriberId id obtained from the subscribeState call
	 * @param statusCallback the callback which will be informed about success or failure.
	 */
	public void unsubscribe(String address, String serviceUuid, String characteristicUuid,
							int subscriberId, IStatusCallback statusCallback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		if (subscribers.remove(subscriberId) != null) {
			if (subscribers.isEmpty()) {
				super.unsubscribe(address, serviceUuid, characteristicUuid, statusCallback);
			} else {

//				JSONObject json = new JSONObject();
//				setStatus(json, BleCoreTypes.CHARACTERISTIC_UNSUBSCRIBED);
//				callback.onData(json);

				statusCallback.onSuccess();
			}
		} else {
			statusCallback.onError(BleErrors.WRONG_CALLBACK);
//			callback.onError(BleErrors.WRONG_CALLBACK);
		}
	}

	// todo: better name than single shot?
	/**
	 * Subscribe to a characteristic, get the notifications, then unsubscribe again (silently).
	 * internal use only
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	private void subscribeMultipartSingleShot(final String address, final String serviceUuid,
											  final String characteristicUuid,
											  final IDataCallback callback) {

		final int[] subscribeId = {0};

		subscribeMultipart(address, serviceUuid, characteristicUuid,
				new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscribeId[0] = result;
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				},
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						callback.onData(json);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										BleLog.LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										BleLog.LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
					}

					@Override
					public void onError(int error) {
						callback.onError(error);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										BleLog.LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										BleLog.LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
					}
				});
	}

	// todo: better name than single shot?
	private void subscribeSingleShot(final String address, final String serviceUuid, final String characteristicUuid,
									 final IDataCallback callback) {

		final int[] subscribeId = {0};

		subscribe(address, serviceUuid, characteristicUuid,
				new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscribeId[0] = result;
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				},
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						callback.onData(json);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										BleLog.LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										BleLog.LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
					}

					@Override
					public void onError(int error) {
						callback.onError(error);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										BleLog.LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										BleLog.LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
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
				int result = BleUtils.toUint8(bytes[0]);
				BleLog.LOGd(TAG, "pwm: %d", result);
				callback.onSuccess(result);
			}
		});
	}

	/**
	 * Write the given value to the Relay characteristic on the device
	 * @param address the MAC address of the device
	 * @param relayOn true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(String address, boolean relayOn, final IStatusCallback callback) {
		int value = relayOn ? BluenetConfig.RELAY_ON : BluenetConfig.RELAY_OFF;
		BleLog.LOGd(TAG, "write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to Relay characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to relay characteristic");
						callback.onError(error);
					}
				}
		);
	}

	/**
	 * Read the Relay characteristic on the device and return the current Relay value as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(String address, final IBooleanCallback callback) {

		BleLog.LOGd(TAG, "read Relay at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read Relay characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				boolean result = BleUtils.toUint8(bytes[0]) > 0;
				BleLog.LOGd(TAG, "Relay: %b", result);
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
		BleLog.LOGd(TAG, "writeScanDevices: write %d at service %s and characteristic %s", value, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_SCAN_CONTROL_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_SCAN_CONTROL_UUID, new byte[]{(byte) value},
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
		BleLog.LOGd(TAG, "read device list at service %s and characteristic %s", BluenetConfig.CHAR_SCANNED_DEVICES_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID);
		read(address, BluenetConfig.CHAR_SCANNED_DEVICES_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, new IDataCallback() {
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
	public void readPowerConsumption(String address, final IIntegerCallback callback) {
		BleLog.LOGd(TAG, "read current consumption at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_CONSUMPTION_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_CONSUMPTION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read current consumption characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				// todo: check if current consumption is only 1 byte
				int value = BleUtils.toUint8(bytes[0]);
				BleLog.LOGd(TAG, "current consumption: %d", value);
				callback.onSuccess(value);
			}
		});
	}

	/**
	 * Read the current curve characteristic to get the current curve from the device. Need to have
	 * called at least once sampleCurrent before a valid current curve can be read.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the curve on success, or an error otherwise
	 */
	public void readPowerSamples(final String address, final IPowerSamplesCallback callback) {
		BleLog.LOGd(TAG, "read power samples at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID);

		subscribeMultipartSingleShot(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						final byte[] notificationBytes = BleCore.getValue(json);
						try {
							PowerSamples powerSamples = new PowerSamples(notificationBytes);
							callback.onData(powerSamples);
						} catch (BufferUnderflowException e) {
							callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	public void subscribePowerSamples(final String address, final IIntegerCallback statusCallback, final IPowerSamplesCallback callback) {
		BleLog.LOGd(TAG, "subscribe to power samples at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID);

		subscribeMultipart(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID,
				statusCallback,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						final byte[] notificationBytes = BleCore.getValue(json);
						try {
							PowerSamples powerSamples = new PowerSamples(notificationBytes);
							callback.onData(powerSamples);
						} catch (BufferUnderflowException e) {
							callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	public void unsubscribePowerSamples(final String address, int subscriberId, final IStatusCallback statusCallback) {
		unsubscribe(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID,
				subscriberId, statusCallback);
	}

	/**
	 * Wrapper function which first calls select configuration, and on success calls the get configuration
	 *
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum ConfigurationMsg Types in BluenetConfig.
	 * @param callback callback function to be called with the read configuration object
	 */
	public void getConfiguration(final String address, final int configurationType, final IConfigurationCallback callback) {

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
//				readConfiguration(address, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the configuration value to the set configuration characteristic. the configuration is a
	 * ConfigurationMsg object which starts with a byte for the configuration type, then 1 byte
	 * reserved for byte alignment, 2 bytes for the length of the payload data, and the payload
	 * data
	 * @param address the address of the device
	 * @param configuration configuration to be written to the set configuration characteristic
	 * @param callback callback function to be called on success or error
	 */
	public void writeConfiguration(String address, ConfigurationMsg configuration, final IStatusCallback callback) {
		byte[] bytes = configuration.toArray();
		BleLog.LOGd(TAG, "configuration: write %s at service %s and characteristic %s", Arrays.toString(bytes), BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_CONTROL_UUID, bytes,
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
	 * Write to the configuration control characteristic to select a configuration that we want to
	 * read afterwards. Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum ConfigurationMsg Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	private void selectConfiguration(String address, int configurationType, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(configurationType, BluenetConfig.READ_VALUE, 0, new byte[]{});
		byte[] bytes = configuration.toArray();
		BleLog.LOGd(TAG, "select configuration: write %d at service %s and characteristic %s", configurationType, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_CONTROL_UUID, bytes,
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
	private void readConfiguration(String address, final IConfigurationCallback callback) {
		BleLog.LOGd(TAG, "read configuration at service %s and characteristic %s", BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_READ_UUID);
		read(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_READ_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read configuration characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				ConfigurationMsg configuration = new ConfigurationMsg(bytes);
				BleLog.LOGd(TAG, "read configuration: %s", configuration.toString());
				callback.onSuccess(configuration);
			}
		});
	}

	/**
	 * Enables state variable notifications on the device. every time the value changes, the device
	 * will write a StateMsg with opCode notification to the ConfigRead characteristic
	 *
	 * @param address the address of the device
	 * @param stateType the state variable for which notifications should be enabled, see @BleStateTypes
	 * @param callback the callback which will be informed about success or failure of the enable call
	 */
	public void enableStateNotification(final String address, final int stateType, final IStatusCallback callback) {
		StateMsg state = new StateMsg(stateType, BluenetConfig.NOTIFY_VALUE, 1, new byte[]{1});
		byte[] bytes = state.toArray();
		BleLog.LOGd(TAG, "notify state: write %d at service %s and characteristic %s", stateType, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to select state characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to select state characteristic");
						callback.onError(error);
					}
				});
	}

//	private void subscribeState(String address, final IStateCallback callback) {
//		subscribeState(address, callback, null);
//	}

	/**
	 * Subscribe to the StateRead characteristic to receive gatt notifications. the statusCallback's
	 * onSuccess function will be triggered on success with the subscriber id. this id is needed
	 * for unsubscribing afterwards.
	 *
	 * @param address the address of the device
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribeState(String address, final IIntegerCallback statusCallback, final IStateCallback callback) {

		subscribeMultipart(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				statusCallback,
				new IDataCallback() {

					@Override
					public void onError(int error) {
						callback.onError(error);
					}

					@Override
					public void onData(JSONObject json) {
						final byte[] bytes = BleCore.getValue(json);
						byte[] decryptedBytes;
						if (_encryptionEnabled) {
							decryptedBytes = _encryption.decryptCtr(bytes, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, _encryptionKeys);
						}
						else {
							decryptedBytes = bytes;
						}

						Log.d(TAG, BleUtils.bytesToString(decryptedBytes));
						StateMsg state = new StateMsg(decryptedBytes);
						BleLog.LOGd(TAG, "received state notification: %s", state.toString());
						callback.onSuccess(state);
					}
				}
		);
	}

	/**
	 * Unsubscribe from the StateRead characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribeState call.
	 *
	 * @param address the address of the device
	 * @param subscriberId id obtained from the subscribeState call
	 */
	public void unsubscribeState(String address, int subscriberId) {
		unsubscribe(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				subscriberId, new IStatusCallback() {
					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "unsubscribe state success");
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "unsubscribe state error: %d", error);
					}
				});
	}

	/**
	 * Unsubscribe from the StateRead characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribeState call.
	 *
	 * @param address the address of the device
	 * @param subscriberId id obtained from the subscribeState call
	 * @param callback the callback which will be informed about success or failure.
	 */
	public void unsubscribeState(String address, int subscriberId, final IStatusCallback callback) {
		unsubscribe(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				subscriberId, callback);
	}

	/**
	 * Helper function to get state notifications. it will first subscribe to the StateRead characteristic
	 * if that is successful, it will enable notifications for the state variable specified. After that,
	 * every time the state variable is updated on the device, the callback's onSuccess function is called.
	 *
	 * @param address the address of the device
	 * @param type the state variable for which notifications should be enabled, see @BleStateTypes
	 * @param statusCallback the callback which will be informed about success or failure of the enable/subscribe
	 *                       calls
	 * @param callback the callback which will be triggered for every notification / state variable update
	 */
	public void getStateNotifications(final String address, final int type, final IIntegerCallback statusCallback,
									  final IStateCallback callback) {

		final int[] subscriberId = new int[1];

		subscribeState(address, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscriberId[0] = result;
						enableStateNotification(address, type, new IStatusCallback() {

							@Override
							public void onSuccess() {
								BleLog.LOGd(TAG, "notify state success");
								statusCallback.onSuccess(subscriberId[0]);
							}

							@Override
							public void onError(int error) {
								unsubscribeState(address, subscriberId[0]);
							}
						});
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "notify state error: %d", error);
						callback.onError(error);
					}
				},
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						if (state.getOpCode() == BluenetConfig.NOTIFY_VALUE) {
							callback.onSuccess(state);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				}
		);
	}


	/**
	 * Wrapper function which first calls select state, and on success calls the get state
	 *
	 * @param address the address of the device
	 * @param stateType the state type, see enum StateMsg Types in BluenetConfig.
	 * @param callback callback function to be called with the read state object
	 */
	public void getState(final String address, final int stateType, final IStateCallback callback) {

		final int[] subscriberId = new int[1];

		subscribeState(address,
			new IIntegerCallback() {
				@Override
				public void onSuccess(int result) {
					subscriberId[0] = result;
					selectState(address, stateType, new IStatusCallback() {
						@Override
						public void onSuccess() {
							// yippi, wait for state to come in on notification
						}

						@Override
						public void onError(int error) {
							// select failed, unsubscribe again
							unsubscribeState(address, subscriberId[0]);
							callback.onError(error);
//							_timeoutHandler.postDelayed(new Runnable() {
//								@Override
//								public void run() {
//									unsubscribeState(address, subscriberId[0]);
//								}
//							}, 200);
						}
					});
				}

				@Override
				public void onError(int error) {
					// subscribe failed
					callback.onError(error);
				}
			},
			new IStateCallback() {
				@Override
				public void onSuccess(StateMsg state) {
					unsubscribeState(address, subscriberId[0]);
					callback.onSuccess(state);
//					_timeoutHandler.postDelayed(new Runnable() {
//						@Override
//						public void run() {
//							unsubscribeState(address, subscriberId[0]);
//						}
//					}, 200);
				}

				@Override
				public void onError(int error) {
					unsubscribeState(address, subscriberId[0]);
					callback.onError(error);
//					_timeoutHandler.postDelayed(new Runnable() {
//						@Override
//						public void run() {
//							unsubscribeState(address, subscriberId[0]);
//						}
//					}, 200);
				}
			}
		);

//		selectState(address, stateType, new IStatusCallback() {
//			@Override
//			public void onSuccess() {
//				// todo: do we need a timeout here?
////				_timeoutHandler.postDelayed(new Runnable() {
////					@Override
////					public void run() {
//						readState(address, callback);
////					}
////				}, 50);
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write to the state control characteristic to select a state variable that we want to
	 * read afterwards. Need to delay the call to readState to give the device some time
	 * to process the request.
	 * @param address the address of the device
	 * @param stateType the state type, see enum StateMsg Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void selectState(String address, int stateType, final IStatusCallback callback) {
		StateMsg state = new StateMsg(stateType, BluenetConfig.READ_VALUE, 0, new byte[]{});
		byte[] bytes = state.toArray();
		BleLog.LOGd(TAG, "select state: write %d at service %s and characteristic %s", stateType, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to select state characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to select state characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the get state characteristic to get the state value which was
	 * previously requested. Need to call selectState first to select a state variable
	 * to be read.
	 * Note: Need to delay the call to readState to give the device some time
	 * to process the request.
	 * Consider using @getState instead
	 * @param address the address of the device
	 * @param callback callback function to be called with the read state object
	 */
	public void readState(String address, final IStateCallback callback) {
		BleLog.LOGd(TAG, "read state at service %s and characteristic %s", BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID);
		read(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "Failed to read state characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				StateMsg state = new StateMsg(bytes);
				BleLog.LOGd(TAG, "read state: %s", state.toString());
				callback.onSuccess(state);
			}
		});
	}

	/**
	 * Send the give command to the control characteristic. the device then executes the command defined
	 * by the command parameter
	 * @param address the address of the device
	 * @param command command to be executed on the device
	 * @param callback callback function to be called on success or error
	 */
	public void sendCommand(String address, CommandMsg command, final IStatusCallback callback) {
		byte[] bytes = command.toArray();
		BleLog.LOGd(TAG, "control command: write %s at service %s and characteristic %s", Arrays.toString(bytes), BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONTROL_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						BleLog.LOGd(TAG, "Successfully written to control characteristic");
						// delay probably not needed anymore since we decoupled characteristic writes
						// from interrupt in firmware
						callback.onSuccess();
//						// we need to give the crownstone some time to handle the control command
//						_timeoutHandler.postDelayed(new Runnable() {
//							@Override
//							public void run() {
//								callback.onSuccess();
//							}
//						}, 200);
					}

					@Override
					public void onError(int error) {
						BleLog.LOGe(TAG, "Failed to write to control characteristic");
						callback.onError(error);
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
	public void writeMeshMessage(String address, MeshMsg message, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "mesh message: write %s at service %s and characteristic %s", message.toString(), BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_MESH_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_MESH_CONTROL_UUID, message.toArray(),
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
	 * Read the list tracked devices characteristic to get the list of tracked devices
	 * @param address the address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		BleLog.LOGd(TAG, "read tracked devices at service %s and characteristic %s", BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACKED_DEVICES_UUID);
		read(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACKED_DEVICES_UUID, new IDataCallback() {
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
	public void addTrackedDevice(String address, TrackedDeviceMsg device, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "add tracked device: write %d at service %s and characteristic %s", device.toString(), BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACK_CONTROL_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACK_CONTROL_UUID, device.toArray(),
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
//		if (BluenetConfig.USE_COMMAND_CHARACTERISTIC) {
//			BleLog.LOGd(TAG, "use control characteristic");
//			sendCommand(address, new CommandMsg(BluenetConfig.CMD_RESET, 1, new byte[]{(byte) value}), callback);
//		} else {
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
//		}
	}

	@Deprecated
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
					int alertValue = BleUtils.toUint8(bytes[0]);
					int num = BleUtils.toUint8(bytes[1]);
					BleLog.LOGd(TAG, "Alert: %d, num: %d", alertValue, num);
					callback.onSuccess(new AlertState(alertValue, num));
				} catch (Exception e) {
					callback.onError(BleErrors.ERROR_RETURN_VALUE_PARSING);
				}
			}
		});
	}

	// only used to reset alerts (set value to 0)
	@Deprecated
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

	ByteBuffer _notificationBuffer = ByteBuffer.allocate(100);
	// set flag to false in case of buffer overflow. no other way to detect invalid messages
	// if needed, could add message number?
	boolean _notificationBufferValid = true;
	// for backwards compatibility
	boolean _hasStartMessageType = false;
	int _meshSubscriberId = 0;

	public void subscribeMeshData(final String address, final IMeshDataCallback callback) {
		BleLog.LOGd(TAG, "subscribing to mesh data...");
		_hasStartMessageType = false;
		subscribe(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
			new IIntegerCallback() {
				@Override
				public void onSuccess(int result) {
					_meshSubscriberId = result;
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			},
			new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					if (BleCore.getStatus(json) == BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY) {

						final byte[] notificationBytes = BleCore.getValue(json);

						BleMeshHubData meshData;
						BleMeshData meshDataPart = new BleMeshData(notificationBytes);
						try {
							switch (meshDataPart.getOpCode()) {
								case 0x0: {
//								meshData = BleMeshDataFactory.fromBytes(notificationBytes);
//								meshData = BleMeshDataFactory.fromBytes(meshDataPart.getData());
									break;
								}
								case 0x20: {
									_hasStartMessageType = true;
									_notificationBuffer.clear();
									_notificationBufferValid = true;
									_notificationBuffer.put(meshDataPart.getData());
									break;
								}
								case 0x21: {
									if (_notificationBufferValid) {
										_notificationBuffer.put(meshDataPart.getData());
									}
									break;
								}
								case 0x22: {
									if (_notificationBufferValid) {
										_notificationBuffer.put(meshDataPart.getData());
										meshData = BleMeshDataFactory.fromBytes(_notificationBuffer.array());
										callback.onData((BleMeshHubData) meshData);
									}
									if (!_hasStartMessageType) {
										_notificationBuffer.clear();
										_notificationBufferValid = true;
									}
									break;
								}
							}
						} catch (BufferOverflowException e) {
							Log.e(TAG, "notification buffer overflow. missed some messages?!");
							_notificationBufferValid = false;
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
				_meshSubscriberId,
				new IStatusCallback() {
					@Override
					public void onSuccess() {
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	public void readSessionNonce(final String address, final boolean setupMode, final IDataCallback callback) {
		Log.d(TAG, "readSessionNonce");
		IDataCallback sessionCallback = new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				byte[] data = getValue(json);
				EncryptionSessionData sessionData = null;
				if (setupMode) {
					sessionData = BleBaseEncryption.getSessionData(data, false);
				}
				else {
					if (_encryptionKeys == null) {
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						return;
					}
					byte[] decryptedData = BleBaseEncryption.decryptEcb(data, _encryptionKeys.getGuestKey());
					sessionData = BleBaseEncryption.getSessionData(decryptedData);
				}

				if (sessionData == null) {
					callback.onError(BleErrors.ENCRYPTION_ERROR);
					return;
				}
				Log.d(TAG, "sessionNonce:" + BleUtils.bytesToString(sessionData.sessionNonce));
				Log.d(TAG, "validationKey:" + BleUtils.bytesToString(sessionData.validationKey));
				addBytes(json, "sessionNonce", sessionData.sessionNonce);
				addBytes(json, "validationKey", sessionData.validationKey);
				callback.onData(json);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		};
		if (setupMode) {
			read(address, BluenetConfig.SETUP_SERVICE_UUID, BluenetConfig.CHAR_SETUP_SESSION_NONCE_UUID, false, sessionCallback);
		}
		else {
			read(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_SESSION_NONCE_UUID, false, sessionCallback);
		}

	}

}
