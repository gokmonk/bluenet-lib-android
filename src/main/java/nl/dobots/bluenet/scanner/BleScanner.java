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
import nl.dobots.bluenet.service.BleScanService;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Class that can be used for scanning.
 */
public class BleScanner {
	private static final String TAG = BleScanner.class.getCanonicalName();

	private Context _context = null;
	private Activity _activity = null;

	private boolean _initialized = false;
	private StatusSingleCallback _initCallback = new StatusSingleCallback();

	// Without service
	private boolean            _initializedScanner = false;
	private BleIntervalScanner _intervalScanner = null;

	// With service
	private boolean            _initializedScanService = false;
	private BleScanService     _scanService = null;
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
	public void init(boolean runInBackground, Activity activity, IStatusCallback callback, @Nullable Notification notification, @Nullable Integer notificationId) {
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
		_activity = activity;
		_context = activity.getApplicationContext();
		if (!_initCallback.setCallback(callback)) {
			BleLog.getInstance().LOGe(TAG, "busy");
			callback.onError(BleErrors.ERROR_BUSY);
		}
		if (runInBackground) {
			initScanService();
		}
		else {
			initScanner(callback);
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
		if (enable && !_initializedScanService) {
			deinitScanner();
			initScanService();
		}
		else if (!enable && !_initializedScanner) {
			deinitScanService();
			initScanner(callback);
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
			_scanService = null;
		}
	}

	private void initScanner(final IStatusCallback callback) {
		_intervalScanner = new BleIntervalScanner();
		_intervalScanner.init(_activity, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_initializedScanner = true;
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private void deinitScanner() {
		if (_initializedScanner) {
			_initializedScanner = false;
			_intervalScanner.destroy();
			_intervalScanner = null;
		}
	}


	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			BleLog.getInstance().LOGi(TAG, "connected to ble scan service ...");
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_scanService = binder.getService();

			// register as event listener. Events, like bluetooth initialized, and bluetooth turned
			// off events will be triggered by the service, so we know if the user turned bluetooth
			// on or off
			_scanService.registerEventListener(BleScanner.this);

			// register as a scan device listener. If you want to get an event every time a device
			// is scanned, then this is the choice for you.
			_scanService.registerScanDeviceListener(BleScanner.this);
			// register as an interval scan listener. If you only need to know the list of scanned
			// devices at every end of an interval, then this is better. additionally it also informs
			// about the start of an interval.
			_scanService.registerIntervalScanListener(BleScanner.this);

//			// set the scan interval (for how many ms should the service scan for devices)
//			_scanService.setScanInterval(SCAN_INTERVAL_IN_SPHERE);
//			// set the scan pause (how many ms should the service wait before starting the next scan)
//			_scanService.setScanPause(SCAN_PAUSE_IN_SPHERE);

			_scanService.startForeground(_notificationId, _notification);

//			_iBeaconRanger = bleExt.getIbeaconRanger();
//			_iBeaconRanger.setRssiThreshold(IBEACON_RANGING_MIN_RSSI);
//			_iBeaconRanger.registerListener(BluenetBridge.this);
//			BleLog.getInstance().LOGd(TAG, "registered: " + BluenetBridge.this);

			_initializedScanService = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// Only called when the service has crashed or has been killed, not when we unbind.
			BleLog.getInstance().LOGi(TAG, "disconnected from service");
			_scanService = null;
//			_iBeaconRanger = null;
			_initializedScanService = false;
		}
	};

}
