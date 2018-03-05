package nl.dobots.bluenet.scanner;

import android.app.Activity;
import android.bluetooth.le.ScanCallback;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.service.BluetoothPermissionRequest;
import nl.dobots.bluenet.service.callbacks.ScanBeaconListener;
import nl.dobots.bluenet.service.callbacks.ScanDeviceListener;
import nl.dobots.bluenet.utils.BleLog;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * Copyright (c) 2018 Crownstone
 *
 * @author Bart van Vliet
 */

public class BleScanner {
	private static final String TAG = BleScanner.class.getCanonicalName();

	/**
	 * Default log level
	 */
	private static final int DEFAULT_LOG_LEVEL = Log.INFO;

	/**
	 * Default values for scan interval and pause
	 */
	private static final int DEFAULT_SCAN_INTERVAL = 500;
	private static final int DEFAULT_SCAN_PAUSE = 500;

	/**
	 * Sometimes stopScan() didn't work properly, resulting in a failure at startScan().
	 * We will retry to call stopScan for a couple of times with some delay
	 */
	private static final int STOP_SCAN_NUM_RETRIES = 5;
	private static final int STOP_SCAN_RETRY_DELAY = 100;
	private static final int START_SCAN_NUM_RETRIES = 5;
	private static final int START_SCAN_RETRY_DELAY = 100;

	// the library
	private BleExt _ble;

	private BleLog _logger;

	// the interval scan handler, handles stop, start, pause, etc.
	private Handler _intervalScanHandler = null;

	// Keep up a list of listeners to notify
	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<ScanBeaconListener> _scanBeaconListeners = new ArrayList<>();
	private ArrayList<EventListener> _eventListeners = new ArrayList<>();

	// values and flags used at runtime
	private int _scanPause = DEFAULT_SCAN_PAUSE;
	private int _scanInterval = DEFAULT_SCAN_INTERVAL;
	private int _scanFilter = BleDeviceFilter.ALL;
	private boolean _running = false;
	private boolean _wasRunning = false;
	private boolean _initialized = false;
	private boolean _scanning = false;

	private int _stopScanRetryNum = 0;
	private int _startScanRetryNum = 0;

	public BleScanner() {
		_logger = new BleLog(DEFAULT_LOG_LEVEL);
		_ble = new BleExt();
		_ble.setLogger(_logger);
		_ble.setEventListener(_btEventListener);
	}

	/**
	 * 
	 */
	private void initBluetooth() {
		getLogger().LOGi(TAG, "initBluetooth");
		_ble.init(this, new IStatusCallback() {
			@Override
			public void onSuccess() {
				getLogger().LOGi(TAG, "successfully initialized BLE");
				_initialized = true;

				// if scanning enabled, resume scanning
				if (_running || _wasRunning) {
					_running = true;
					_intervalScanHandler.removeCallbacksAndMessages(null);
					_intervalScanHandler.postDelayed(_startScanRunnable, 100);
				}
			}

			@Override
			public void onError(int error) {
				switch (error) {
					case BleErrors.ERROR_BLE_PERMISSION_MISSING: {
						onPermissionsMissing();
						break;
					}
					case BleErrors.ERROR_BLUETOOTH_NOT_ENABLED: {
						getLogger().LOGe(TAG, "Failed to enable bluetooth!!");
						_running = false;
						sendEvent(EventListener.Event.BLUETOOTH_NOT_ENABLED);
						break;
					}
					default:
						getLogger().LOGe(TAG, "Init Error: " + error);
				}
				_initialized = false;
			}
		});
	}

	/**
	 * The runnable executed when a scan interval starts. It calls startIntervalScan
	 * on the library, and posts the stopScanRunnable, to stop the scan at the end of the scan
	 * interval. If a device was detected, all ScanDeviceListeners will be informed with the
	 * device as a parameter.
	 * Additionally, an onScanStart event is issued to all IntervalScanListeners to notify them
	 * about the start of the scan interval.
	 */
	private Runnable _startScanRunnable = new Runnable() {
		@Override
		public void run() {
//			if (isScanActive()) {
//				getLogger().LOGw(TAG, "already scanning ...");
//				return;
//			}

			getLogger().LOGd(TAG, "starting scan interval ...");
			_ble.startScan(false, new IBleDeviceCallback() {

				@Override
				public void onSuccess() {
					getLogger().LOGd(TAG, "... scan interval started");
					_scanning = true;
					_stopScanRetryNum = 0;
					_startScanRetryNum = 0;

					onIntervalScanStart();
					if (_scanPause > 0) {
						_intervalScanHandler.postDelayed(_stopScanRunnable, _scanInterval);
					}
				}

				@Override
				public void onDeviceScanned(BleDevice device) {
					notifyDeviceScanned(device);
				}

				@Override
				public void onError(int error) {
//					_running = false; // TODO: why do we have to set it to false here? It breaks the retries.
					getLogger().LOGe(TAG, "... scan interval start error: " + error);
					boolean sendError = true;

					if (error == BleErrors.ERROR_ALREADY_SCANNING) {
						// TODO: quickly stop AND start, righ now it can take a long time to start again.
						// Retry to stop scan
						if (_stopScanRetryNum++ < STOP_SCAN_NUM_RETRIES) {
							// Cancel the stopScanRunnable that was posted after the line onIntervalScanStart() below.
							_intervalScanHandler.removeCallbacks(_stopScanRunnable);
							_intervalScanHandler.postDelayed(_stopScanRunnable, STOP_SCAN_RETRY_DELAY);
							sendError = false;
						}
					}
					if (error == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
						if (_startScanRetryNum++ < START_SCAN_NUM_RETRIES) {
							getLogger().LOGi(TAG, "Retrying to start ...");
							// Cancel the stopScanRunnable that was posted after the line onIntervalScanStart() below.
							_intervalScanHandler.removeCallbacks(_stopScanRunnable);
							_intervalScanHandler.postDelayed(_startScanRunnable, START_SCAN_RETRY_DELAY);
							sendError = false;
						}
					}

					if (sendError) {
						_running = false;
						sendEvent(EventListener.Event.BLUETOOTH_START_SCAN_ERROR);
					}
				}
			});
		}
	};

	/**
	 * The stopScanRunnable takes care of stopping the interval scan at the end of the scan
	 * interval. It issues an onScanEnd event to all IntervalScanListeners to inform them about
	 * the end of the scan interval, then posts the startScanRunnable to start the next scan
	 * interval once the scan pause expired.
	 */
	private Runnable _stopScanRunnable = new Runnable() {
		@Override
		public void run() {
			getLogger().LOGd(TAG, "pausing scan interval ...");
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					getLogger().LOGd(TAG, "... scan interval paused");
					_scanning = false;

					onIntervalScanEnd();
					if (_running) {
						getLogger().LOGi(TAG, "running");
						_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
					} else {
						getLogger().LOGi(TAG, "not running");
					}
				}

				@Override
				public void onError(int error) {
					getLogger().LOGe(TAG, "... scan interval pause error: " + error);
					_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
					sendEvent(EventListener.Event.BLUETOOTH_STOP_SCAN_ERROR);
				}
			});
		}
	};



	/**
	 * Tell the service to start scanning for devices with the given interval and pause
	 * durations and setting a device filter.
	 * @param scanInterval the scan interval in ms, the service scans for this amount of time before
	 *                     pausing again
	 * @param scanPause the scan paus in ms, the service pauses for this amount of time before starting
	 *                  a new scan
	 * @param deviceFilter set the scan device filter, see BleDeviceFilter. By setting a filter, only the devices specified will
	 *                     be reported to the application, any other detected devices will be ignored.
	 */
	public void startIntervalScan(int scanInterval, int scanPause, int deviceFilter) {
		this._scanInterval = scanInterval;
		this._scanPause = scanPause;
		startIntervalScan(deviceFilter);
	}

	/**
	 * Tell the service to start scanning for devices and report only devices specified by the filter.
	 * This will use the scan interval and pause values set earlier, or the default values if nothing
	 * was set previously.
	 * @param deviceFilter set the scan device filter, see BleDeviceFilter. By setting a filter, only the devices specified will
	 *                     be reported to the application, any other detected devices will be ignored.
	 */
	public void startIntervalScan(int deviceFilter) {
		_ble.setScanFilter(deviceFilter);
		startIntervalScan();
	}

	/**
	 * Tell the service to start scanning for devices and report any devices found.
	 * This will use the scan interval and pause values set earlier, or the default values if nothing
	 * was set previously.
	 */
	public void startIntervalScan() {
//		setScanningState(true);
		getLogger().LOGi(TAG, "startIntervalScan with interval=" + _scanInterval + " pause=" + _scanPause);

		if (!_initialized) {
			getLogger().LOGi(TAG, "Start scan");
			// set wasScanning flag to true so that once bluetooth is enabled, and we receive
			// the event, the service will automatically start scanning
			_wasRunning = true;
			initBluetooth();
		}
		else if (!_running) {
			getLogger().LOGi(TAG, "Start scan");
			_running = true;
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_intervalScanHandler.post(_startScanRunnable);
		}
	}

	/**
	 * Sop interval scanning. the service will go into pause.
	 */
	public void stopIntervalScan() {
		if (_running) {
			getLogger().LOGi(TAG, "Stop scan");
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_running = false;
//			setScanningState(false);
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					getLogger().LOGi(TAG, "scan stopped");
				}

				@Override
				public void onError(int error) {
					getLogger().LOGe(TAG, "Failed to stop scan: " + error);
					sendEvent(EventListener.Event.BLUETOOTH_STOP_SCAN_ERROR);
				}
			});
		}
	}

	/**
	 * Check if interval scanning was started.
	 * @return true if interval scanning is started, false if the service is paused.
	 */
	public boolean isRunning() {
		return _running;
	}

	/**
	 * Check if currently scanning for devices.
	 * @return true if currently scanning, false if currently pausing.
	 */
	public boolean isScanActive() {
		return _scanning;
	}

	/**
	 * Callback handling bluetooth connection state changes. If bluetooth is turned
	 * off, an event BLUETOOTH_TURNED_OFF is sent to all EventListeners, if bluetooth is turned on
	 * an event BLUETOOTH_TURNED_ON is sent to all EventListeners.
	 * The service will also automatically pause scanning if bluetooth is turned off, and
	 * resume scanning if bluetooth is turned on again (only if it was scanning before it was turned off)
	 */
	private EventListener _btEventListener = new EventListener() {

		@Override
		public void onEvent(Event event) {
			// will (also) be called whenever bluetooth is disabled

			switch (event) {
				case BLUETOOTH_TURNED_ON: {
					getLogger().LOGd(TAG, "Bluetooth turned on");

					if (_ble.getBleBase().isReady()) {
						_initialized = true;

						if (_running || _wasRunning) {
							_running = true;
							_intervalScanHandler.removeCallbacksAndMessages(null);
							_intervalScanHandler.postDelayed(_startScanRunnable, 100);
						}
					}
					break;
				}
				case BLUETOOTH_TURNED_OFF: {
					getLogger().LOGe(TAG, "Bluetooth turned off!!");

					_initialized = false;
					_scanning = false;
					_intervalScanHandler.removeCallbacksAndMessages(null);

					// if bluetooth was turned off and scanning is enabled, issue a notification that present
					// detection won't work without BLE ...
					if (_running) {
						_wasRunning = true;
						stopIntervalScan();
					} else {
						_wasRunning = false;
					}
					break;
				}
				case LOCATION_SERVICES_TURNED_ON: {
					getLogger().LOGd(TAG, "Location Services turned on");

					if (_ble.getBleBase().isReady()) {
						_initialized = true;

						if (_running || _wasRunning) {
							_running = true;
							_intervalScanHandler.removeCallbacksAndMessages(null);
							_intervalScanHandler.postDelayed(_startScanRunnable, 100);
						}
					}
					break;
				}
				case LOCATION_SERVICES_TURNED_OFF: {
					getLogger().LOGe(TAG, "Location Services turned off!!");

					_initialized = false;
					_scanning = false;
					_intervalScanHandler.removeCallbacksAndMessages(null);

					if (_running) {
						_wasRunning = true;
						stopIntervalScan();
					} else {
						_wasRunning = false;
					}
					break;
				}
			}
			sendEvent(event);
		}
	};







	/**
	 * Helper functions to retrieve the current scanning state (scanning or not scanning) from the
	 * preferences. We store the scanning state because the service can be killed and/or restarted by
	 * the android OS, and we want to start in the same state as it was before.
	 * @return
	 */
//	private boolean getScanningState() {
//		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
//		return sharedPreferences.getBoolean(SCANNING_STATE, true);
//	}

	/**
	 * Helper functions to store the current scanning state (scanning or not scanning) from the
	 * preferences. We store the scanning state because the service can be killed and/or restarted by
	 * the android OS, and we want to start in the same state as it was before.
	 * @return
	 */
//	private void setScanningState(boolean scanning) {
//		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
//		SharedPreferences.Editor editor = sharedPreferences.edit();
//		editor.putBoolean(SCANNING_STATE, scanning);
//		editor.commit();
//	}






	/**
	 * Helper function to notify ScanDeviceListeners
	 * @param device the scanned device
	 */
	private void notifyDeviceScanned(BleDevice device) {
		getLogger().LOGv(TAG, String.format(Locale.US, "scanned device: %s [%d] (%d) %s", device.getAddress(), device.getRssi(), device.getOccurrences(), device.getName()));

		for (ScanDeviceListener listener : _scanDeviceListeners) {
			listener.onDeviceScanned(device);
		}
	}

	/**
	 * Helper function to notify IntervalScanListeners when a scan interval starts
	 */
	private void onIntervalScanStart() {
		sendEvent(EventListener.Event.SCAN_INTERVAL_START);
	}

	/**
	 * Helper function to notify IntervalScanListeners when a scan interval ends
	 */
	private void onIntervalScanEnd() {
		sendEvent(EventListener.Event.SCAN_INTERVAL_END);
	}


	/**
	 * Register as an EventListener. Whenever an event, such as bluetooth state change, is triggerd
	 * the onEvent function is called
	 * @param listener the listener to register
	 */
	public void registerEventListener(EventListener listener) {
		if (!_eventListeners.contains(listener)) {
			_eventListeners.add(listener);
		}
	}

	/**
	 * Unregister from the service
	 * @param listener the listener to unregister
	 */
	public void unregisterEventListener(EventListener listener) {
		if (_eventListeners.contains(listener)) {
			_eventListeners.remove(listener);
		}
	}

	/**
	 * Helper function to notify EventListeners
	 * @param event
	 */
	private void sendEvent(EventListener.Event event) {
		for (EventListener listener : _eventListeners) {
			listener.onEvent(event);
		}
	}








	public void requestPermissions(Activity activity) {
		_ble.requestPermissions(activity);
	}

	public void onPermissionGranted() {
		sendEvent(EventListener.Event.BLE_PERMISSIONS_GRANTED);
	}

	int permissionRetryCount = 0;

	public void onPermissionDenied() {
//		onEvent(EventListener.Event.BLE_PERMISSIONS_MISSING);
		if (++permissionRetryCount < 2) {
			onPermissionsMissing();
		} else {
			sendEvent(EventListener.Event.BLE_PERMISSIONS_MISSING);
		}
	}

	private void onPermissionsMissing() {
		getLogger().LOGe(TAG, "Ble permissions missing, need to call BleExt.requestPermissions first!!");
		_running = false;
//					onEvent(EventListener.Event.BLE_PERMISSIONS_MISSING);
		Intent intent = new Intent(BleScanService.this, BluetoothPermissionRequest.class);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback statusCallback) {
		return _ble.handlePermissionResult(requestCode, permissions, grantResults, statusCallback);
	}






	public BleLog getLogger() {
		if (_logger != null) {
			return _logger;
		} else {
			return BleLog.getInstance();
		}
	}
}
