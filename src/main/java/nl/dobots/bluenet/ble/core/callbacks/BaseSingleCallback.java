package nl.dobots.bluenet.ble.core.callbacks;

/**
 * Copyright (c) 2018 Crownstone
 *
 * @author Bart van Vliet
 */

import android.util.Log;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.utils.Logging;

/**
 * Base class to wrap around callback interfaces, to make sure that the callback is only invoked once.
 * Derived classes should implement: public synchronized boolean resolve(data).
 */
public class BaseSingleCallback<T extends IBaseCallback> extends Logging {
	private static final String TAG = BaseSingleCallback.class.getCanonicalName();

	// default log level
	private static final int LOG_LEVEL = Log.WARN;

	// Callback to be called when action succeeded or failed.
	protected T _callback;

	/**
	 * Sets the callback.
	 * @param callback The callback to be set.
	 * @return False if a callback has been set already.
	 */
	public synchronized boolean setCallback(T callback) {
		if (_callback != null) {
			getLogger().LOGe(TAG, "Busy!");
			return false;
		}

		_callback = callback;
		return true;
	}

	/**
	 * Check if a callback has been set.
	 * @return True if callback is set.
	 */
	public synchronized boolean isCallbackSet() {
		return _callback != null;
	}

	/**
	 * Reject the call: the call failed.
	 * @param error The error code, see BleError.
	 * @return False if there was no callback set.
	 */
	public synchronized boolean reject(int error) {
		if (!preResolve()) {
			return false;
		}

		T callback = _callback;
		cleanup();
		callback.onError(error);

		return true;
	}

	/**
	 * Cancel the call, the call be rejected.
	 * @return False if there was no callback set.
	 */
	public synchronized boolean cancel() {
		return reject(BleErrors.ERROR_CANCELLED);
	}

	protected boolean preResolve() {
		if (_callback == null) {
			getLogger().LOGw(TAG, "Not busy!");
			return false;
		}
		return true;
	}

	protected void cleanup() {
		_callback = null;
	}

	@Override
	protected int getLogLevel() {
		return LOG_LEVEL;
	}

	@Override
	protected String getTag() {
		return TAG;
	}
}
