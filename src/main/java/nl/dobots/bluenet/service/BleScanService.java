package nl.dobots.bluenet.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.service.callbacks.EventListener;
import nl.dobots.bluenet.service.callbacks.IntervalScanListener;
import nl.dobots.bluenet.service.callbacks.ScanDeviceListener;

/**
 * Defines a Ble Scan Service which provides the functionality to do interval scanning. This means
 * the service will scan for a given amount of time, then pause for another defined amount of time,
 * before starting another scan. During a scan, the BluetoothAdapter scans at the highest duty cycle
 * to make sure as many advertisements are received as possible. By adjusting the scanInterval and
 * scanPause values, battery vs detection can be optimized.
 * When starting the service, a set of Parameters can be provided through the intent which define
 * the behaviour of the service:
 *   * EXTRA_SCAN_INTERVAL: the scan interval in ms, defaults to DEFAULT_SCAN_INTERVAL
 *   * EXTRA_SCAN_PAUSE:    the scan pause in ms, defaults to DEFAULT_SCAN_PAUSE
 *   * EXTRA_AUTO_START:    set to true if the service should start scanning directly after starting
 *   * EXTRA_SCAN_FILTER:   define the device filter (only used if EXTRA_AUTO_START is set to true)
 *
 * If EXTRA_AUTO_START is set to false, the scan can be started after binding to the service by
 * calling one of the startIntervalScan functions.
 * The scan interval and scan pause can be defined using the functions setScanInterval and setScanPause
 * or can be provided directly to the startIntervalScan function.
 *
 * After starting the scan, the service will scan for BLE devices, if a device is detected, the service
 * notifies all registered ScanDeviceListeners with the detected device as a parameter. At the start
 * and end of a each scan interval, the service notifies all registered IntervalScanListeners by calling
 * onScanStart and onScanEnd. If events occur, such as a change in bluetooth state, e.g. turning on/off
 * bluetooth, the service will notify all registered EventListeners about the detected event.
 *
 * The service keeps up a list of the scanned devices. It averages the rssi values and creates distance
 * estimates for iBeacon devices. To clear the list, call clearDeviceMap
 *
 * An application can detect / receive detected devices in the following two fashions:
 *
 * 1. instant reception of detected devices
 *
 * 		By registering as a ScanDeviceListener to the service, the application will receive an onDeviceScanned
 * 		event every time a device is seen. this can happen several times per scan interval, depending on the
 * 		advertisement frequency of the device and the chosen scan interval.
 *
 * 2. get device list at end of scan interval
 *
 * 		By registering as an IntervalScanListener to the service, the application will receive an onScanEnd
 * 		event after every scan interval. At this point, the application can call the getDeviceMap function
 * 	    to receive the updated list of scanned devices. The list will be kept intact between intervals and
 * 	    updated with the received devices and their RSSI values during the scan intervals.
 *
 */
public class BleScanService extends Service {

	private static final String TAG = BleScanService.class.getCanonicalName();

	/**
	 * Provide a scan interval to the service on start.
	 */
	public static final String EXTRA_SCAN_INTERVAL = "nl.dobots.bluenet.SCAN_INTERVAL";

	/**
	 * Provide a scan pause to the service on start.
	 */
	public static final String EXTRA_SCAN_PAUSE = "nl.dobots.bluenet.SCAN_PAUSE";

	/**
	 * Default values for scan interval and pause
	 */
	private static final int DEFAULT_SCAN_INTERVAL = 500;
	private static final int DEFAULT_SCAN_PAUSE = 500;

	/**
	 * Set auto start to true if the service should start scanning directly on start.
	 */
	public static final String EXTRA_AUTO_START = "nl.dobots.bluenet.AUTO_START";

	/**
	 * Default value for EXTRA_AUTO_START
	 */
	private static final boolean DEFAULT_AUTO_START = false;

	/**
	 * Set the scan device filter on start. only used if EXTRA_AUTO_START is set to true.
	 */
	public static final String EXTRA_SCAN_FILTER = "nl.dobots.bluenet.SCAN_FILTER";

	/**
	 * values for EXTRA_SCAN_FILTER:
	 *   FILTER_ALL: return all scanned BLE devices
	 *   FILTER_CROWNSTONE: return only scanned crownstones
	 *   FILTER_DOBEACON: return only scanned doBeacons
	 *   FILTER_IbEACON: return all scanned iBeacon devices
	 */
	public static final int FILTER_ALL = 0;
	public static final int FILTER_CROWNSTONE = 1;
	public static final int FILTER_DOBEACON = 2;
	public static final int FILTER_IBEACON = 3;

	/**
	 * Default value for EXTRA_SCAN_FILTER
	 */
	private static final int DEFAULT_SCAN_FILTER = FILTER_ALL;

	/**
	 * Preferences stored by the service to keep track of e.g. scanning state when the
	 * service is being restarted by the android OS
	 */
	public static final String BLE_SERVICE_CFG = "ble_service";
	public static final String SCANNING_STATE = "scanningState";

	private static BleScanService INSTANCE;

	// binding to the service
	public class BleScanBinder extends Binder {
		public BleScanService getService() {
			return INSTANCE;
		}
	}

	private final IBinder _binder = new BleScanBinder();

	// Keep up a list of listeners to notify
	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<IntervalScanListener> _intervalScanListeners = new ArrayList<>();
	private ArrayList<EventListener> _eventListeners = new ArrayList<>();

	// the library
	private BleExt _ble;

	// the interval scan handler, handles stop, start, pause, etc.
	private Handler _intervalScanHandler = null;

	// values and flags used at runtime
	private int _scanPause = DEFAULT_SCAN_PAUSE;
	private int _scanInterval = DEFAULT_SCAN_INTERVAL;
	private boolean _scanning = false;
	private boolean _wasScanning = false;
	private boolean _initialized = false;

	@Override
	public void onCreate() {
		super.onCreate();

		INSTANCE = this;

		_ble = new BleExt();

		HandlerThread handlerThread = new HandlerThread("IntervalScanHandler");
		handlerThread.start();
		_intervalScanHandler = new Handler(handlerThread.getLooper());
	}

	@Override
	public IBinder onBind(Intent intent) {
		return _binder;
	}

	/**
	 * Helper function to get the ScanFilter enum from the bundle
	 * @param bundle bundle to be parsed
	 * @return returns the filter found in the bundle, or BleDeviceFilter.all
	 *   if the field EXTRA_SCAN_FILTER was not defined
	 */
	private BleDeviceFilter getFilterFromExtra(Bundle bundle) {
		if (bundle != null) {
			switch (bundle.getInt(EXTRA_SCAN_FILTER, DEFAULT_SCAN_FILTER)) {
				case FILTER_ALL:
					return BleDeviceFilter.all;
				case FILTER_CROWNSTONE:
					return BleDeviceFilter.crownstone;
				case FILTER_DOBEACON:
					return BleDeviceFilter.doBeacon;
				case FILTER_IBEACON:
					return BleDeviceFilter.iBeacon;
			}
		}
		return BleDeviceFilter.all;
	}

	/**
	 * Extras can be provided with the intent to customize the service on start. See
	 * EXTRA_SCAN_INTERVAL, EXTRA_SCAN_PAUSE, EXTRA_AUTO_START, EXTRA_SCAN_FILTER for
	 * details
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		boolean autoStart = false;
		BleDeviceFilter scanFilter = BleDeviceFilter.all;

		// get the parameters from the intent
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				_scanInterval = bundle.getInt(EXTRA_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL);
				_scanPause = bundle.getInt(EXTRA_SCAN_PAUSE, DEFAULT_SCAN_PAUSE);
				autoStart = bundle.getBoolean(EXTRA_AUTO_START, DEFAULT_AUTO_START);
				scanFilter = getFilterFromExtra(bundle);
			}
		}

		// if intent had the auto start set, or if last scanning state was true, start interval scan
		if (autoStart || getScanningState()) {
			startIntervalScan(scanFilter);
		}

		// sticky makes the service restart if gets killed (by the user or by android due to
		// low memory.)
		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (_scanning) {
			_ble.stopScan(null); // don' t care if it worked or not, so don' t need a callback
		}

		// Remove all callbacks and messages that were posted
		_intervalScanHandler.removeCallbacksAndMessages(null);
	}

	/**
	 * Callback handling bluetooth connection state changes. If bluetooth is turned
	 * off, an event BLUETOOTH_TURNED_OFF is sent to all EventListeners, if bluetooth is turned on
	 * an event BLUETOOTH_INITIALIZED is sent to all EventListeners.
	 * The service will also automatically pause scanning if bluetooth is turned off, and
	 * resume scanning if bluetooth is turned on again (only if it was scanning before it was turned off)
	 */
	private IStatusCallback _btStateCallback = new IStatusCallback() {
		@Override
		public void onSuccess() {
			// will be called whenever bluetooth is enabled

			Log.d(TAG, "successfully initialized BLE");
			_initialized = true;

			// if scanning enabled, resume scanning
			if (_scanning || _wasScanning) {
				_scanning = true;
				_intervalScanHandler.removeCallbacksAndMessages(null);
				_intervalScanHandler.postDelayed(_startScanRunnable, 100);
			}

			onEvent(EventListener.Event.BLUETOOTH_INITIALIZED);
		}

		@Override
		public void onError(int error) {
			// will (also) be called whenever bluetooth is disabled

			switch (error) {
				case BleErrors.ERROR_BLUETOOTH_TURNED_OFF: {
					Log.e(TAG, "Bluetooth turned off!!");

					// if bluetooth was turned off and scanning is enabled, issue a notification that present
					// detection won't work without BLE ...
					if (_scanning) {
						onEvent(EventListener.Event.BLUETOOTH_TURNED_OFF);
						_wasScanning = true;

						_intervalScanHandler.removeCallbacksAndMessages(null);
						_scanning = false;
//						stopIntervalScan();
					} else {
						_wasScanning = false;
					}
					break;
				}
				case BleErrors.ERROR_BLUETOOTH_NOT_ENABLED: {
					Log.e(TAG, "Failed to enable bluetooth!!");
					_scanning = false;
					onEvent(EventListener.Event.BLUETOOTH_NOT_ENABLED);
					break;
				}
				default:
					Log.e(TAG, "Ble Error: " + error);
			}
			_initialized = false;
		}
	};

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
			Log.d(TAG, "Start endless scan");
			if (_ble.startIntervalScan(new IBleDeviceCallback() {

					@Override
					public void onDeviceScanned(BleDevice device) {
						notifyDeviceScanned(device);
					}

					@Override
					public void onError(int error) {
						Log.e(TAG, "start scan error: " + error);
					}
				}))
			{
				Log.e(TAG, "scan started");
				_scanning = true;
				onIntervalScanStart();
				_intervalScanHandler.postDelayed(_stopScanRunnable, _scanInterval);
			}
		}
	};

	/**
	 * Helper function to notify ScanDeviceListeners
	 * @param device the scanned device
	 */
	private void notifyDeviceScanned(BleDevice device) {
		Log.d(TAG, "scanned device: " + device.getName() + " " + device.getRssi());

		for (ScanDeviceListener listener : _scanDeviceListeners) {
			listener.onDeviceScanned(device);
		}
	}

	/**
	 * The stopScanRunnable takes care of stopping the interval scan at the end of the scan
	 * interval. It issues an onScanEnd event to all IntervalScanListeners to inform them about
	 * the end of the scan interval, then posts the startScanRunnable to start the next scan
	 * interval once the scan pause expired.
	 */
	private Runnable _stopScanRunnable = new Runnable() {
		@Override
		public void run() {
			Log.d(TAG, "Stop endless scan");
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					onIntervalScanEnd();
					_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
				}

				@Override
				public void onError(int error) {
					_intervalScanHandler.postDelayed(_startScanRunnable, _scanPause);
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
	 * @param filter set the scan device filter. by setting a filter, only the devices specified will
	 *               be reported to the application, any other detected devices will be ignored.
	 */
	public void startIntervalScan(int scanInterval, int scanPause, BleDeviceFilter filter) {
		this._scanInterval = scanInterval;
		this._scanPause = scanPause;
		startIntervalScan(filter);
	}

	/**
	 * Tell the service to start scanning for devices and report only devices specified by the filter.
	 * This will use the scan interval and pause values set earlier, or the default values if nothing
	 * was set previously.
	 * @param filter set the scan device filter. by setting a filter, only the devices specified will
	 *               be reported to the application, any other detected devices will be ignored.
	 */
	public void startIntervalScan(BleDeviceFilter filter) {
		_ble.setScanFilter(filter);
		setScanningState(true);

		if (!_initialized) {
			Log.i(TAG, "Starting interval scan");
			// set wasScanning flag to true so that once bluetooth is enabled, and we receive
			// the event, the service will automatically start scanning
			_wasScanning = true;
			_ble.init(this, _btStateCallback);
		} else if (!_scanning) {
			Log.i(TAG, "Starting interval scan");
			_intervalScanHandler.post(_startScanRunnable);
		}
	}

	/**
	 * Tell the service to start scanning for devices and report any devices found.
	 * This will use the scan interval and pause values set earlier, or the default values if nothing
	 * was set previously.
	 */
	public void startIntervalScan() {
		startIntervalScan(BleDeviceFilter.doBeacon);
	}

	/**
	 * Sop interval scanning. the service will go into pause.
	 */
	public void stopIntervalScan() {
		if (_scanning) {
			_intervalScanHandler.removeCallbacksAndMessages(null);
			_scanning = false;
			setScanningState(false);
			_ble.stopScan(new IStatusCallback() {
				@Override
				public void onSuccess() {
					Log.i(TAG, "Stopped interval scan");
				}

				@Override
				public void onError(int error) {
					Log.e(TAG, "Failed to stop interval scan: " + error);
				}
			});
		}
	}

	/**
	 * Check if interval scanning was started.
	 * @return true if interval scanning is started, false if the service is paused.
	 */
	public boolean isScanning() {
		return _scanning;
	}

	/**
	 * Register as a ScanDeviceListener. Whenever a device is detected, an onDeviceScanned event
	 * is triggered with the detected device as a parameter
	 * @param listener the listener to register
	 */
	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (!_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.add(listener);
		}
	}

	/**
	 * Unregister from the service
	 * @param listener the listener to unregister
	 */
	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.remove(listener);
		}
	}

	/**
	 * Helper function to notify IntervalScanListeners when a scan interval starts
	 */
	private void onIntervalScanStart() {
		for (IntervalScanListener listener : _intervalScanListeners) {
			listener.onScanStart();
		}
	}

	/**
	 * Helper function to notify IntervalScanListeners when a scan interval ends
	 */
	private void onIntervalScanEnd() {
		for (IntervalScanListener listener : _intervalScanListeners) {
			listener.onScanEnd();
		}
	}

	/**
	 * Register as an IntervalScanListener. Whenever a scan interval starts, an onScanStart event
	 * is issued, and whenever a scan interval ends, an onScanEnd is issued.
	 * @param listener the listener to register
	 */
	public void registerIntervalScanListener(IntervalScanListener listener) {
		if (!_intervalScanListeners.contains(listener)) {
			_intervalScanListeners.add(listener);
		}
	}

	/**
	 * Unregister from the service
	 * @param listener the listener to unregister
	 */
	public void unregisterIntervalScanListener(IntervalScanListener listener) {
		if (_intervalScanListeners.contains(listener)) {
			_intervalScanListeners.remove(listener);
		}
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
	private void onEvent(EventListener.Event event) {
		for (EventListener listener : _eventListeners) {
			listener.onEvent(event);
		}
	}

	/**
	 * Return the currently set scan pause duration
	 * @return the scan pause duration (in ms)
	 */
	public int getScanPause() {
		return _scanPause;
	}

	/**
	 * Set the scan pause duration to a new value
	 * @param scanPause the value to be used for the scan pause (in ms)
	 */
	public void setScanPause(int scanPause) {
		_scanPause = scanPause;
		if (isScanning()) {
			stopIntervalScan();
			startIntervalScan();
		}
	}

	/**
	 * Return the currently set scan interval duration
	 * @return the scan interval duration (in ms)
	 */
	public int getScanInterval() {
		return _scanInterval;
	}

	/**
	 * Set the scan interval duration to a new value
	 * @param scanInterval the value to be used for the scan duration (in ms)
	 */
	public void setScanInterval(int scanInterval) {
		_scanInterval = scanInterval;
		if (isScanning()) {
			stopIntervalScan();
			startIntervalScan();
		}
	}

	/**
	 * Return the list of devices which have been scanned so far (or since the last clear)
	 * @return list of scanned devices
	 */
	public BleDeviceMap getDeviceMap() {
		return _ble.getDeviceMap();
	}

	/**
	 * Clear the list of scanned devices
	 */
	public void clearDeviceMap() {
		_ble.clearDeviceMap();
	}

	/**
	 * Helper functions to retrieve the current scanning state (scanning or not scanning) from the
	 * preferences. We store the scanning state because the service can be killed and/or restarted by
	 * the android OS, and we want to start in the same state as it was before.
	 * @return
	 */
	private boolean getScanningState() {
		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
		return sharedPreferences.getBoolean(SCANNING_STATE, true);
	}

	/**
	 * Helper functions to store the current scanning state (scanning or not scanning) from the
	 * preferences. We store the scanning state because the service can be killed and/or restarted by
	 * the android OS, and we want to start in the same state as it was before.
	 * @return
	 */
	private void setScanningState(boolean scanning) {
		SharedPreferences sharedPreferences = getSharedPreferences(BLE_SERVICE_CFG, 0);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(SCANNING_STATE, scanning);
		editor.commit();
	}

}