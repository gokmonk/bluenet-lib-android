package nl.dobots.bluenet.service;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.le.ScanCallback;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.scanner.BleIntervalScanner;
import nl.dobots.bluenet.scanner.callbacks.IntervalScanListener;
import nl.dobots.bluenet.scanner.callbacks.ScanBeaconListener;
import nl.dobots.bluenet.scanner.callbacks.ScanDeviceListener;
import nl.dobots.bluenet.scanner.callbacks.IScanListCallback;
import nl.dobots.bluenet.utils.BleLog;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * Defines a service that wraps the BleIntervalScanner.
 * When starting the service, a set of Parameters can be provided through the intent which define
 * the behaviour of the service:
 *   * EXTRA_LOG_LEVEL:      The log level, defaults to LOG_LEVEL.
 *   * EXTRA_FILE_LOG_LEVEL: The log level to file, defaults to LOG_LEVEL.
 */
public class BleScanService extends Service {
	private static final String TAG = BleScanService.class.getCanonicalName();

	// Default log level
	private static final int LOG_LEVEL = Log.INFO;

	/**
	 * Set the default log level of the scan service and the ble library that the service is using
	 */
	public static final String EXTRA_LOG_LEVEL = "logLevel";

	/**
	 * Set the default log level to file of the scan service and the ble library that the service is using
	 */
	public static final String EXTRA_FILE_LOG_LEVEL = "fileLogLevel";

	/**
	 * Preferences stored by the service to keep track of e.g. scanning state when the
	 * service is being restarted by the android OS
	 */
	public static final String BLE_SERVICE_CFG = "ble_service";
	public static final String SCANNING_STATE = "scanningState";

	// Instance of this class
	private static BleScanService INSTANCE;

	// binding to the service
	public class BleScanBinder extends Binder {
		public BleScanService getService() {
			return INSTANCE;
		}
	}

	private final IBinder _binder = new BleScanBinder();

	// The scanner library
	private BleIntervalScanner _scanner;

	// The logger
	private BleLog _logger;

	@Override
	public void onCreate() {
		super.onCreate();

		INSTANCE = this;

		// Create logger before logging anything.
		_logger = new BleLog(LOG_LEVEL);
		getLogger().LOGi(TAG, "Create scan service");
		_scanner = new BleIntervalScanner();
//		_ble = new BleExt();
//		_ble.setLogger(_logger);
//		_ble.setEventListener(_btEventListener);
	}

	/**
	 * @see BleIntervalScanner#init(boolean, Activity, IStatusCallback)
	 */
	public void init(boolean makeReady, @Nullable Activity activity, final IStatusCallback callback) {
		_scanner.init(makeReady, activity, callback);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getLogger().LOGw(TAG, "onDestroy");
		if (_scanner != null) {
			_scanner.destroy();
		}
	}

	/**
	 * Get the logger.
	 *
	 * @return The logger used by this service.
	 */
	public BleLog getLogger() {
//		if (_logger != null) {
//			return _logger;
//		}
//		else {
//			return BleLog.getInstance();
//		}
		return _logger;
	}

	/**
	 * Get the scanner class.
	 *
	 * @return The scanner used by this service.
	 */
	public BleIntervalScanner getScanner() {
		return _scanner;
	}


	@Override
	public IBinder onBind(Intent intent) {
		getLogger().LOGi(TAG, "onBind");
		parseParameters(intent);
		return _binder;
	}

	/**
	 * Parse parameters from intent.
	 *
	 * @param intent Intent with optional bundle.
	 */
	private void parseParameters(Intent intent) {
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				int logLevel = bundle.getInt(EXTRA_LOG_LEVEL, LOG_LEVEL);
				int fileLogLevel = bundle.getInt(EXTRA_FILE_LOG_LEVEL, LOG_LEVEL);
				getLogger().setLogLevel(logLevel, fileLogLevel);
			}
		}
	}

	/**
	 * Extras can be provided with the intent to customize the service on start.
	 * See EXTRA_LOG_LEVEL, EXTRA_FILE_LOG_LEVEL.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		getLogger().LOGi(TAG, "onStartcommand");

		// get the parameters from the intent
		parseParameters(intent);

		// sticky makes the service restart if gets killed (by the user or by android due to
		// low memory.)
		return Service.START_STICKY;
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
