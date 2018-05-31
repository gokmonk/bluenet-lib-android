package nl.dobots.bluenet.scanner;

import android.app.Activity;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.core.callbacks.StatusSingleCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.scanner.callbacks.ScanBeaconListener;
import nl.dobots.bluenet.scanner.callbacks.ScanDeviceListener;
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Class that can be used for scanning.
 */
public class BleScanner {
	private static final String TAG = BleScanner.class.getCanonicalName();

	// Cache
	private Context            _context = null;
	private Activity           _activity = null;
	private boolean            _runInBackground = false;

	private boolean _initialized = false;
	private StatusSingleCallback _initCallback = new StatusSingleCallback();

	// Settings
	private boolean            _settingsInitialized = false;
	private int                _scanDuration;
	private int                _scanPause;
	private BleDeviceFilter    _scanFilter;
	private int                _scanMode;

	// Without service
	private boolean            _initializedScanner = false;
	private BleIntervalScanner _intervalScanner = null;

	// With service
	private boolean            _initializedScanService = false;
	private BleScanService     _scanService = null;
	private BleIntervalScanner _scanServiceScanner = null;
	private Notification       _notification = null;
	private Integer            _notificationId = null;
	// Used to cache whether to init scanner with makeReady.
	private boolean            _makeScanServiceReady = false;

	// Listeners
	private ArrayList<ScanDeviceListener> _scanDeviceListeners = new ArrayList<>();
	private ArrayList<EventListener>      _eventListeners = new ArrayList<>();
	private ArrayList<ScanBeaconListener> _scanBeaconListeners = new ArrayList<>();

	/**
	 * Initialize the scanner.
	 *
	 * @param makeReady       Set to true when the scanner should be made ready. This also means
	 *                        the user may be requested to enable bluetooth and location services.
	 * @param runInBackground Whether to run the scanner in the background.
	 * @param activity        The activity required to use bluetooth, ask for permission, etc.
	 *                        It's best when this activity has Activity.onActivityResult() implemented,
	 *                        and from there call BleCore.handleActivityResult().
	 *                        Also, this activity should best have Activity.onRequestPermissionsResult()
	 *                        implemented, and from there call BleCore.handlePermissionResult().
	 * @param notification    Optional, but required to run in the background.
	 * @param notificationId  Id of the notification.
	 * @param callback        The callback to be notified about success or failure.
	 */
	public void init(boolean makeReady, boolean runInBackground, Activity activity, @Nullable Notification notification, @Nullable Integer notificationId, final IStatusCallback callback) {
		if (_initialized) {
			BleLog.getInstance().LOGe(TAG, "Already initialized");
			callback.onSuccess();
			return;
		}

		// Cache notification.
		// Do this before any return, or else the notification is not cached
		// as in checkReady the notification is no parameter.
		if (notification != null) {
			_notification = notification;
		}
		if (notificationId != null) {
			_notificationId = notificationId;
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
				_initialized = true;
				cacheScannerSettings(getScanner());
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		};

		if (!_initCallback.setCallback(initCallback)) {
			BleLog.getInstance().LOGe(TAG, "busy");
			initCallback.onError(BleErrors.ERROR_BUSY);
			return;
		}

		if (runInBackground) {
			initScanService(makeReady);
		}
		else {
			initScanner(makeReady);
		}

	}


	/**
	 * Change whether to run scanner in background.
	 * Note: scanning will be stopped and not restarted automatically.
	 *
	 * @param makeReady       Set to true when the scanner should be made ready. This also means
	 *                        the user may be requested to enable bluetooth and location services.
	 * @param enable          Whether to run the scanner in the background.
	 * @param notification    Required to run in the background.
	 * @param notificationId  Id of the notification.
	 * @param callback        The callback to be notified about success or failure.
	 */
	public void runInBackground(boolean makeReady, boolean enable, @Nullable Notification notification, @Nullable Integer notificationId, IStatusCallback callback) {
		if (!_initialized) {
			BleLog.getInstance().LOGe(TAG, "Not initialized");
			return;
		}
		if (!_initCallback.setCallback(callback)) {
			BleLog.getInstance().LOGe(TAG, "busy");
			callback.onError(BleErrors.ERROR_BUSY);
			return;
		}
		BleIntervalScanner scanner = getScanner();
		boolean wasRunning = (scanner != null && scanner.isRunning());
		if (enable && !_initializedScanService) {
			if (wasRunning) {
				stopScanning();
			}
			deinitScanner();
			initScanService(makeReady);
		}
		else if (!enable && !_initializedScanner) {
			if (wasRunning) {
				stopScanning();
			}
			deinitScanService();
			initScanner(makeReady);
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
	 * @return Whether the scanner was initialized.
	 */
	public boolean isInitialized() {
		return _initialized;
	}

	public void checkReady(boolean makeReady, boolean runInBackground, @Nullable Activity activity, @Nullable final IStatusCallback callback) {
		IStatusCallback checkCallback = new IStatusCallback() {
			@Override
			public void onSuccess() {
				if (callback != null) {
					callback.onSuccess();
				}
			}

			@Override
			public void onError(int error) {
				if (callback != null) {
					callback.onError(error);
				}
			}
		};

		if (!_initialized) {
//			if (makeReady) {
				init(makeReady, runInBackground, activity, null, null, checkCallback);
				return;
//			}
//			else {
//				callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
//			}
		}
		getIntervalScanner().checkReady(makeReady, activity, checkCallback);
	}

	/**
	 * Start scanning.
	 * Should be done after init.
	 */
	public void startScanning(@Nullable final IStatusCallback callback) {
		getScanner().startIntervalScan(new IStatusCallback() {
			@Override
			public void onSuccess() {
				BleLog.getInstance().LOGi(TAG, "Started scanning");
				if (callback != null) {
					callback.onSuccess();
				}
			}

			@Override
			public void onError(int error) {
				BleLog.getInstance().LOGe(TAG, "Failed to start scanning: " + error);
				if (callback != null) {
					callback.onError(error);
				}
			}
		});
	}

	/**
	 * Stop scanning.
	 * Should be done after init.
	 */
	public void stopScanning() {
		getScanner().stopIntervalScan();
	}

	/**
	 * @see BleIntervalScanner#setScanInterval(int, int)
	 * Should be done after init.
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
	 * @see BleIntervalScanner#setScanFilter(BleDeviceFilter)
	 * Should be done after init.
	 */
	public void setScanFilter(BleDeviceFilter deviceFilter) {
		_scanFilter = deviceFilter;
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.setScanFilter(deviceFilter);
		}
	}

	/**
	 * @see BleIntervalScanner#setScanMode(int)
	 * Should be done after init.
	 */
	public void setScanMode(int scanMode) {
		_scanMode = scanMode;
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.setScanMode(scanMode);
		}
	}

	/**
	 * @see BleIntervalScanner#getScanMode()
	 * Should be done after init.
	 */
	public int getScanmode() {
		return getScanner().getScanMode();
	}

	/**
	 * @see BleIntervalScanner#registerEventListener(EventListener)
	 */
	public void registerEventListener(EventListener listener) {
		if (!_eventListeners.contains(listener)) {
			_eventListeners.add(listener);
		}
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.registerEventListener(listener);
		}
	}

	/**
	 * @see BleIntervalScanner#unregisterEventListener(EventListener)
	 */
	public void unregisterEventListener(EventListener listener) {
		if (_eventListeners.contains(listener)) {
			_eventListeners.remove(listener);
		}
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.unregisterEventListener(listener);
		}
	}

	/**
	 * @see BleIntervalScanner#registerScanDeviceListener(ScanDeviceListener)
	 */
	public void registerScanDeviceListener(ScanDeviceListener listener) {
		if (!_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.add(listener);
		}
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.registerScanDeviceListener(listener);
		}
	}

	/**
	 * @see BleIntervalScanner#unregisterScanDeviceListener(ScanDeviceListener)
	 */
	public void unregisterScanDeviceListener(ScanDeviceListener listener) {
		if (_scanDeviceListeners.contains(listener)) {
			_scanDeviceListeners.remove(listener);
		}
		BleIntervalScanner scanner = getScanner();
		if (scanner != null) {
			scanner.unregisterScanDeviceListener(listener);
		}
	}

	/**
	 * @see BleIntervalScanner#registerScanBeaconListener(ScanBeaconListener)
	 */
	public void registerScanBeaconListener(ScanBeaconListener listener) {
		if (!_scanBeaconListeners.contains(listener)) {
			_scanBeaconListeners.add(listener);
		}
	}

	/**
	 * @see BleIntervalScanner#unregisterScanBeaconListener(ScanBeaconListener)
	 */
	public void unregisterScanBeaconListener(ScanBeaconListener listener) {
		if (_scanBeaconListeners.contains(listener)) {
			_scanBeaconListeners.remove(listener);
		}
	}

	/**
	 * Get the currently used interval scanner.
	 * Warning: becomes invalid when changing whether to run in background.
	 *
	 * @return Currently used interval scanner.
	 */
	public BleIntervalScanner getIntervalScanner() {
		return getScanner();
	}




	private void initScanner(boolean makeReady) {
		_intervalScanner = new BleIntervalScanner();
		_intervalScanner.init(makeReady, _activity, new IStatusCallback() {
			@Override
			public void onSuccess() {
//				_intervalScanner.registerEventListener(BleScanner.this);
//				_intervalScanner.registerScanDeviceListener(BleScanner.this);
				applyScannerSettings(_intervalScanner);
				registerListeners(_intervalScanner);
				_initializedScanner = true;
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

	private void initScanService(boolean makeReady) {
		if (_notification == null || _notificationId == null) {
			_initCallback.reject(BleErrors.ERROR_NO_NOTIFICATION);
			return;
		}

		// create and bind to the BleScanService
		_makeScanServiceReady = makeReady;
		BleLog.getInstance().LOGi(TAG, "Binding to ble scan service");
		Intent intent = new Intent(_context, BleScanService.class);
//		intent.putExtra(BleScanService.EXTRA_LOG_LEVEL, );
//		intent.putExtra(BleScanService.EXTRA_FILE_LOG_LEVEL, );
		boolean success = _context.bindService(intent, _serviceConnection, Context.BIND_AUTO_CREATE);
		BleLog.getInstance().LOGi(TAG, "Successfully bound to service: " + success);
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

	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			BleLog.getInstance().LOGi(TAG, "Connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_scanService = binder.getService();
			_scanService.init(_makeScanServiceReady, _activity, new IStatusCallback() {
				@Override
				public void onSuccess() {
					_scanServiceScanner = _scanService.getScanner();
//					_scanServiceScanner.registerEventListener(BleScanner.this);
//					_scanServiceScanner.registerScanDeviceListener(BleScanner.this);
					applyScannerSettings(_scanServiceScanner);
					registerListeners(_scanServiceScanner);


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
		_scanMode =     scanner.getScanMode();
	}

	// Apply the settings from cache to a scanner.
	private void applyScannerSettings(BleIntervalScanner scanner) {
		if (!_settingsInitialized || scanner == null) {
			return;
		}
		scanner.setScanInterval(_scanDuration, _scanPause);
		scanner.setScanFilter(_scanFilter);
		scanner.setScanMode(_scanMode);
	}

	// Register cached listeners.
	private void registerListeners(BleIntervalScanner scanner) {
		for (EventListener listener: _eventListeners) {
			scanner.registerEventListener(listener);
		}
		for (ScanDeviceListener listener: _scanDeviceListeners) {
			scanner.registerScanDeviceListener(listener);
		}
		for (ScanBeaconListener listener: _scanBeaconListeners) {
			scanner.registerScanBeaconListener(listener);
		}
	}

}
