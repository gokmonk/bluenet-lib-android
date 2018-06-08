package nl.dobots.bluenet.scanner;

import android.app.Activity;
import android.bluetooth.le.ScanCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.scanner.callbacks.ScanBeaconListener;
import nl.dobots.bluenet.scanner.callbacks.ScanDeviceListener;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2018 Crownstone
 *
 * @author Bart van Vliet
 */

public class BleIntervalScanner {
	private static final String TAG = BleIntervalScanner.class.getCanonicalName();

	/**
	 * Default log level
	 */
	private static final int DEFAULT_LOG_LEVEL = Log.INFO;

	/**
	 * Default values for scan interval.
	 */
	private static final int DEFAULT_SCAN_DURATION = 60000;
	private static final int DEFAULT_SCAN_PAUSE = 100;

	/**
	 * Sometimes stopScan() didn't work properly, resulting in a failure at startScan().
	 * We will retry to call stopScan for a couple of times with some delay
	 */
	private static final int STOP_SCAN_NUM_RETRIES = 5;
	private static final int STOP_SCAN_RETRY_DELAY = 100;
	private static final int START_SCAN_NUM_RETRIES = 5;
	private static final int START_SCAN_RETRY_DELAY = 100;

	// The bluenet library
	private BleExt _ble;

	// Logger
	private BleLog _logger;

	// The interval scan handler, handles stop, start, pause, etc.
	private Handler _intervalScanHandler = null;

	// Keep up a list of listeners to notify
	private ArrayList<EventListener>      _eventListeners = new ArrayList<>();
	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<ScanBeaconListener> _scanBeaconListeners = new ArrayList<>();

	// Whether or not to parse service data. Not parsing should save batteries.
	private boolean _parseServiceData = true;

	// Values and flags used at runtime
	private int _scanPause = DEFAULT_SCAN_PAUSE;
	private int _scanDuration = DEFAULT_SCAN_DURATION;
	private boolean _running = false;
	private boolean _wasRunning = false;
	private boolean _scanning = false;

	private int _stopScanRetryNum = 0;
	private int _startScanRetryNum = 0;

	public BleIntervalScanner() {
		_logger = new BleLog(DEFAULT_LOG_LEVEL);
		_ble = new BleExt();
		_ble.setLogger(_logger);
		_ble.setEventListener(_btEventListener);

		HandlerThread handlerThread = new HandlerThread("IntervalScanHandler");
		handlerThread.start();
		_intervalScanHandler = new Handler(handlerThread.getLooper());
	}

	/**
	 * @see BleExt#init(boolean, Activity, IStatusCallback)
	 */
	public void init(boolean makeReady, @Nullable Activity activity, final IStatusCallback callback) {
		getLogger().LOGi(TAG, "init");

		_ble.init(makeReady, activity, new IStatusCallback() {
			@Override
			public void onSuccess() {
				getLogger().LOGi(TAG, "Successfully initialized");
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				getLogger().LOGw(TAG, "Init error: " + error);
				callback.onError(error);
			}
		});
	}

	/**
	 * Close the library and release all callbacks
	 */
	public void destroy() {
		getLogger().LOGi(TAG, "onDestroy");
		_intervalScanHandler.removeCallbacksAndMessages(null);
		if (_running) {
			_ble.stopScan(null); // don' t care if it worked or not, so don' t need a callback
		}
		_ble.destroy();
	}

	/**
	 * @see BleExt#checkScannerReady(boolean, Activity, IStatusCallback)
	 */
	public void checkReady(final boolean makeReady, @Nullable final Activity activity, final IStatusCallback callback) {
		_ble.checkScannerReady(makeReady, activity, callback);
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
			if (isScanActive()) {
				getLogger().LOGd(TAG, "already scanning");
				return;
			}

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
						_intervalScanHandler.postDelayed(_stopScanRunnable, _scanDuration);
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

					if (error == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
						sendError = false;
					}

					if (error == BleErrors.ERROR_ALREADY_SCANNING) {
						// TODO: quickly stop AND start, right now it can take a long time to start again.
						// Retry to stop scanning
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
					}
					else {
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
	 * Set the scan interval: scan duration and pause.
	 * The scanner will scan for [duration] ms, and then pause for [pause] ms.
	 * The sum of duration and pause should be larger than 5s.
	 *
	 * @param scanDuration Scan duration in ms.
	 * @param scanPause Scan pause in ms. Set to 0 for an endless scan.
	 */
	public void setScanInterval(int scanDuration, int scanPause) {
		_scanDuration = scanDuration;
		_scanPause = scanPause;
	}

	/**
	 * Get the currently set scan duration.
	 */
	public int getScanDuration() {
		return _scanDuration;
	}

	/**
	 * Get the currently set scan pause.
	 */
	public int getScanPause() {
		return _scanPause;
	}

	/**
	 * @see BleExt#setScanFilter(BleDeviceFilter)
	 */
	public void setScanFilter(BleDeviceFilter deviceFilter) {
		_ble.setScanFilter(deviceFilter);
	}

	/**
	 * @see BleExt#getScanFilter()
	 */
	public BleDeviceFilter getScanFilter() {
		return _ble.getScanFilter();
	}

	/**
	 * @see BleCore#setScanMode(int)
	 */
	public void setScanMode(int scanMode) {
		_ble.getBleBase().setScanMode(scanMode);
	}

	/**
	 * @see BleCore#getScanMode()
	 */
	public int getScanMode() {
		return _ble.getBleBase().getScanMode();
	}



	/**
	 * Start scanning with already set interval and filter.
	 * Scanned devices will be reported back to registered ScanDeviceListeners.
	 *
	 * @param callback     The callback to be notified about success or failure.
	 */
	public void startIntervalScan(final IStatusCallback callback) {
		getLogger().LOGi(TAG, "startIntervalScan with duration=" + _scanDuration + " pause=" + _scanPause);

		_ble.getBleBase().checkScannerReady(false, null, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_running = true;
				// This check first, else the stopScanRunnable is removed without being started again.
				// TODO: make this thread safe
				if (isScanActive()) {
					getLogger().LOGd(TAG, "already scanning");
					callback.onSuccess();
					return;
				}
				_intervalScanHandler.removeCallbacksAndMessages(null);
				_intervalScanHandler.post(_startScanRunnable);
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Stop interval scanning.
	 */
	public void stopIntervalScan() {
		if (_running) {
			getLogger().LOGi(TAG, "Stop scan");
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_running = false;
			_scanning = false;
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
					getLogger().LOGi(TAG, "Bluetooth turned on");

					if (_ble.getBleBase().isScannerReady()) {
						getLogger().LOGi(TAG, "running=" + _running + " wasRunning=" + _wasRunning);
						if (_running || _wasRunning) {
							_running = true;
							_intervalScanHandler.removeCallbacksAndMessages(null);
							_intervalScanHandler.postDelayed(_startScanRunnable, 100);
						}
					}
					break;
				}
				case BLUETOOTH_TURNED_OFF: {
					getLogger().LOGw(TAG, "Bluetooth turned off");

					_scanning = false;
					_intervalScanHandler.removeCallbacksAndMessages(null);

					// if bluetooth was turned off and scanning is enabled, issue a notification that present
					// detection won't work without BLE ...
					if (_running) {
						getLogger().LOGi(TAG, "Set waRunning to true");
						_wasRunning = true;
						stopIntervalScan();
					}
					else {
						_wasRunning = false;
					}
					break;
				}
				case LOCATION_SERVICES_TURNED_ON: {
					getLogger().LOGd(TAG, "Location Services turned on");

					if (_ble.getBleBase().isScannerReady()) {
						if (_running || _wasRunning) {
							_running = true;
							_intervalScanHandler.removeCallbacksAndMessages(null);
							_intervalScanHandler.postDelayed(_startScanRunnable, 100);
						}
					}
					break;
				}
				case LOCATION_SERVICES_TURNED_OFF: {
					getLogger().LOGw(TAG, "Location Services turned off");
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
	 * Helper function to notify EventListeners
	 * @param event
	 */
	private void sendEvent(EventListener.Event event) {
		for (EventListener listener : _eventListeners) {
			listener.onEvent(event);
		}
	}



	/**
	 * Register an EventListener.
	 * Whenever an event happens, the onEvent function is called.
	 * @param listener The listener to register.
	 */
	public void registerEventListener(EventListener listener) {
		if (!_eventListeners.contains(listener)) {
			_eventListeners.add(listener);
		}
	}

	/**
	 * Unregister an EventListener.
	 * @param listener The listener to unregister.
	 */
	public void unregisterEventListener(EventListener listener) {
		if (_eventListeners.contains(listener)) {
			_eventListeners.remove(listener);
		}
	}

	/**
	 * Register a ScanDeviceListener.
	 * Whenever a device is scanned, the onDeviceScanned function is called.
	 * @param listener The listener to register.
	 */
	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (!_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.add(listener);
		}
	}

	/**
	 * Unregister an ScanDeviceListener.
	 * @param listener The listener to unregister.
	 */
	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.remove(listener);
		}
	}

	/**
	 * Register a ScanBeaconListener.
	 * Whenever a device is scanned, the onBeaconScanned function is called.
	 * @param listener The listener to register.
	 */
	public void registerScanBeaconListener(ScanBeaconListener listener) {
		if (!_scanBeaconListeners.contains(listener)) {
			_scanBeaconListeners.add(listener);
		}
	}

	/**
	 * Unregister an ScanBeaconListener.
	 * @param listener The listener to unregister.
	 */
	public void unregisterScanBeaconListener(ScanBeaconListener listener) {
		if (_scanBeaconListeners.contains(listener)) {
			_scanBeaconListeners.remove(listener);
		}
	}




	/**
	 * @see nl.dobots.bluenet.ble.core.BleCore#checkLocationServicesPermissions
	 */
	public void checkLocationServicesPermissions(boolean requestEnable, @Nullable Activity activity, IStatusCallback callback) {
		_ble.checkLocationServicesPermissions(requestEnable, activity, callback);
	}



	public BleLog getLogger() {
		if (_logger != null) {
			return _logger;
		} else {
			return BleLog.getInstance();
		}
	}

	/**
	 * Get the extended ble lib, used by this scanner.
	 * Warning: only use methods that don't have anything to do with scanning.
	 *
	 * @return Extended ble lib.
	 */
	public BleExt getBleExt() {
		return _ble;
	}
}
