package nl.dobots.bluenet.scanner;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.core.callbacks.StatusSingleCallback;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.scanner.callbacks.ScanDeviceListener;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Class that can be used for scanning.
 */
public class BleScanner implements EventListener, ScanDeviceListener {
	private static final String TAG = BleScanner.class.getCanonicalName();

	private Context _context = null;
	private Activity _activity = null;

	private boolean _initialized = false;
	private StatusSingleCallback _initCallback = new StatusSingleCallback();

	// Settings
	private boolean            _settingsInitialized = false;
	private int                _scanDuration;
	private int                _scanPause;
	private int                _scanFilter;

	// Without service
	private boolean            _initializedScanner = false;
	private BleIntervalScanner _intervalScanner = null;

	// With service
	private boolean            _initializedScanService = false;
	private BleScanService     _scanService = null;
	private BleIntervalScanner _scanServiceScanner = null;
	private Notification       _notification = null;
	private Integer            _notificationId = null;


	/**
	 * Initialize the scanner.
	 *
	 * @param runInBackground Whether to run the scanner in the background.
	 * @param activity        The activity required to use bluetooth, ask for permission, etc.
	 *                        It's best when this activity has Activity.onActivityResult() implemented,
	 *                        and from there call BleCore.handleActivityResult().
	 *                        Also, this activity should best have Activity.onRequestPermissionsResult()
	 *                        implemented, and from there call BleCore.handlePermissionResult().
	 * @param callback        The callback to be notified about success or failure.
	 * @param notification
	 * @param notificationId
	 */
	public void init(boolean runInBackground, Activity activity, final IStatusCallback callback, @Nullable Notification notification, @Nullable Integer notificationId) {
		if (_initialized) {
			BleLog.getInstance().LOGe(TAG, "Already initialized");
			callback.onSuccess();
			return;
		}
		if (activity == null || activity.isDestroyed()) {
			BleLog.getInstance().LOGe(TAG, "Missing activity");
			callback.onError(BleErrors.ERROR_NO_CONTEXT);
			return;
		}

		// Cache activity and context.
		_activity = activity;
		_context = activity.getApplicationContext();

		IStatusCallback initCallback = new IStatusCallback() {
			@Override
			public void onSuccess() {
				cacheScannerSettings(getScanner());
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		};

		if (!_initCallback.setCallback(callback)) {
			BleLog.getInstance().LOGe(TAG, "busy");
			callback.onError(BleErrors.ERROR_BUSY);
			return;
		}

		if (runInBackground) {
			initScanService();
		}
		else {
			initScanner();
		}

	}


	/**
	 * Change whether to run scanner in background.
	 *
	 * @param enable          Whether to run the scanner in the background.
	 */
	public void runInBackground(boolean enable, @Nullable Notification notification, @Nullable Integer notificationId, IStatusCallback callback) {
		if (!_initialized) {
			BleLog.getInstance().LOGe(TAG, "Not initialized");
			return;
		}
		if (!_initCallback.setCallback(callback)) {
			BleLog.getInstance().LOGe(TAG, "busy");
			callback.onError(BleErrors.ERROR_BUSY);
			return;
		}
		if (enable && !_initializedScanService) {
			deinitScanner();
			initScanService(notification, notificationId);
		}
		else if (!enable && !_initializedScanner) {
			deinitScanService();
			initScanner();
		}
	}

	/**
	 * Stops scanning, stops any service, and destroys the ble library.
	 */
	public void destroy() {
		deinitScanService();
		deinitScanner();
	}


	/**
	 * Start scanning.
	 */
	public void startScanning() {

	}

	/**
	 * Stop scanning.
	 */
	public void stopScanning() {

	}

	/**
	 * @see BleIntervalScanner#setScanInterval(int, int)
	 */
	public void setScanInterval(int scanDuration, int scanPause) {
		_scanDuration = scanDuration;
		_scanPause = scanPause;
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.setScanInterval(scanDuration, scanPause);
		}
	}

	/**
	 * @see BleIntervalScanner#setScanFilter(int)
	 */
	public void setScanFilter(int deviceFilter) {
		_deviceFilter = deviceFilter;
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.setScanFilter(deviceFilter);
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









	private void initScanService() {
		// create and bind to the BleScanService
		BleLog.getInstance().LOGi(TAG, "binding to service..");
		Intent intent = new Intent(_context, BleScanService.class);
//		intent.putExtra(BleScanService.EXTRA_LOG_LEVEL, );
//		intent.putExtra(BleScanService.EXTRA_FILE_LOG_LEVEL, );
		boolean success = _context.bindService(intent, _serviceConnection, Context.BIND_AUTO_CREATE);
		BleLog.getInstance().LOGi(TAG, "successfully bound to service: " + success);
	}

	private void deinitScanService() {
		if (_initializedScanService) {
			_context.unbindService(_serviceConnection);
			_initializedScanService = false;
			cacheScannerSettings(_intervalScanner);
			_scanService = null;
			_scanServiceScanner = null;
		}
	}

	private void initScanner() {
		_intervalScanner = new BleIntervalScanner();
		_intervalScanner.init(_activity, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_initializedScanner = true;
				applyScannerSettings(_intervalScanner);
				_initCallback.resolve();
			}

			@Override
			public void onError(int error) {
				_initCallback.reject(error);
			}
		});
	}

	private void deinitScanner() {
		if (_initializedScanner) {
			_initializedScanner = false;
			cacheScannerSettings(_intervalScanner);
			_intervalScanner.destroy();
			_intervalScanner = null;
		}
	}

	/**
	 * Get the scanner that's currently in use.
	 * @return Current scanner, or null if none in use.
	 */
	private BleIntervalScanner getScanner() {
		if (_initializedScanner) {
			return _intervalScanner;
		}
		if (_initializedScanService) {
			return _scanServiceScanner;
		}
		return null;
	}

	// Cache the settings from a scanner.
	private void cacheScannerSettings(BleIntervalScanner scanner) {
		if (scanner == null) {
			return;
		}
		_settingsInitialized = true;
		_scanDuration = scanner.getScanDuration();
		_scanPause =    scanner.getScanPause();
		_scanFilter =   scanner.getScanFilter();
	}

	// Apply the settings from cache to a scanner.
	private void applyScannerSettings(BleIntervalScanner scanner) {
		if (!_settingsInitialized || scanner == null) {
			return;
		}
		scanner.setScanInterval(_scanDuration, _scanPause);
		scanner.setScanFilter(_scanFilter);
	}


	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			BleLog.getInstance().LOGi(TAG, "connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_scanService = binder.getService();
			_scanService.init(_activity, new IStatusCallback() {
				@Override
				public void onSuccess() {
					_scanServiceScanner = _scanService.getScanner();
					_scanServiceScanner.registerEventListener(BleScanner.this);
					_scanServiceScanner.registerScanDeviceListener(BleScanner.this);
					applyScannerSettings(_scanServiceScanner);

//					_iBeaconRanger = bleExt.getIbeaconRanger();
//					_iBeaconRanger.setRssiThreshold(IBEACON_RANGING_MIN_RSSI);
//					_iBeaconRanger.registerListener(BluenetBridge.this);
//					BleLog.getInstance().LOGd(TAG, "registered: " + BluenetBridge.this);

					_scanService.startForeground(_notificationId, _notification);
					_initializedScanService = true;
					_initCallback.resolve();
				}

				@Override
				public void onError(int error) {
					BleLog.getInstance().LOGe(TAG, "Scan service init failed: " + error);
					// TODO: is this enough?
					_scanService = null;
					_context.unbindService(_serviceConnection);
					_initCallback.reject(error);
				}
			});
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// Only called when the service has crashed or has been killed, not when we unbind.
			BleLog.getInstance().LOGi(TAG, "disconnected from service");
			_scanService = null;
			_scanServiceScanner = null;
//			_iBeaconRanger = null;
			_initializedScanService = false;
		}
	};

	@Override
	public void onDeviceScanned(BleDevice device) {

	}

	@Override
	public void onEvent(Event event) {

	}
}
