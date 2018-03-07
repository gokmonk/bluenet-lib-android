package nl.dobots.bluenet.ble.core.callbacks;

/**
 * Copyright (c) 2018 Crownstone
 *
 * @author Bart van Vliet
 */

import android.util.Log;

import org.json.JSONObject;

import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.utils.Logging;

/**
 * This class wraps around several callback interfaces, and makes sure that the callback is only called 1 time.
 */
public class GenericSingleCallback extends SingleCallback<IBaseCallback> {
	private static final String TAG = GenericSingleCallback.class.getCanonicalName();

	// Default log level
	private static final int LOG_LEVEL = Log.WARN;

	/**
	 * Resolve the call: the call was successful.
	 * @return False if no, or wrong type of callback was set.
	 */
	public synchronized boolean resolve() {
		if (!preResolve()) {
			return false;
		}

		if (_callback instanceof IStatusCallback) {
			IStatusCallback callback = (IStatusCallback)_callback;
			cleanup();
			callback.onSuccess();
		}
		else {
			IBaseCallback callback = _callback;
			cleanup();
			callback.onError(BleErrors.ERROR_WRONG_PAYLOAD_TYPE);
		}
		return true;
	}

	/**
	 * Resolve the call: the call was successful.
	 * @return False if no, or wrong type of callback was set.
	 */
	public synchronized boolean resolve(byte[] data) {
		if (!preResolve()) {
			return false;
		}

		if (_callback instanceof IByteArrayCallback) {
			IByteArrayCallback callback = (IByteArrayCallback)_callback;
			cleanup();
			callback.onSuccess(data);
		}
		else {
			IBaseCallback callback = _callback;
			cleanup();
			callback.onError(BleErrors.ERROR_WRONG_PAYLOAD_TYPE);
		}
		return true;
	}

	/**
	 * Resolve the call: the call was successful.
	 * @return False if no, or wrong type of callback was set.
	 */
	public synchronized boolean resolve(JSONObject data) {
		if (!preResolve()) {
			return false;
		}

		if (_callback instanceof IDataCallback) {
			IDataCallback callback = (IDataCallback)_callback;
			cleanup();
			callback.onData(data);
		}
		else {
			IBaseCallback callback = _callback;
			cleanup();
			callback.onError(BleErrors.ERROR_WRONG_PAYLOAD_TYPE);
		}
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
