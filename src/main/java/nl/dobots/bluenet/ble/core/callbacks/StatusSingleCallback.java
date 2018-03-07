package nl.dobots.bluenet.ble.core.callbacks;

/**
 * Copyright (c) 2018 Crownstone
 *
 * @author Bart van Vliet
 */

import android.util.Log;

/**
 * This class wraps around the status callback interface, and makes sure that the callback is only called 1 time.
 */
public class StatusSingleCallback extends BaseSingleCallback<IStatusCallback> {
	private static final String TAG = StatusSingleCallback.class.getCanonicalName();

	// Default log level
	private static final int LOG_LEVEL = Log.WARN;

	/**
	 * Resolve the call: the call was successful.
	 * @return False if no callback was set.
	 */
	public synchronized boolean resolve() {
		if (!preResolve()) {
			return false;
		}

		IStatusCallback callback = _callback;
		cleanup();
		callback.onSuccess();
		return true;
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
