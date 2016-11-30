package nl.dobots.bluenet.ble.base.callbacks;

import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 24-11-16
 *
 * A generic IExecStatusCallback object which can be used in connectAndExecute functions.
 * A callback can be given as parameter which will be triggered onSuccess and onError
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class SimpleExecStatusCallback implements IExecStatusCallback {

	private static final String TAG = SimpleExecStatusCallback.class.getCanonicalName();

	private IIntegerCallback _integerCallback;
	private IStatusCallback _statusCallback;
	private IBooleanCallback _booleanCallback;

	public SimpleExecStatusCallback() {}

	public SimpleExecStatusCallback(IStatusCallback statusCallback) {
		_statusCallback = statusCallback;
	}

	public SimpleExecStatusCallback(IIntegerCallback integerCallback) {
		_integerCallback = integerCallback;
	}

	public SimpleExecStatusCallback(IBooleanCallback booleanCallback) {
		_booleanCallback = booleanCallback;
	}

	@Override
	public void onSuccess(int result) {
		if (_integerCallback != null) {
			_integerCallback.onSuccess(result);
		} else {
			BleLog.LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(boolean value) {
		if (_booleanCallback != null) {
			_booleanCallback.onSuccess(value);
		} else {
			BleLog.LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess() {
		if (_statusCallback != null) {
			_statusCallback.onSuccess();
		} else {
			BleLog.LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onError(int error) {
		if (_integerCallback != null) {
			_integerCallback.onError(error);
		} else if (_statusCallback != null) {
			_statusCallback.onError(error);
		} else if (_booleanCallback != null) {
			_booleanCallback.onError(error);
		} else {
			BleLog.LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onExecuteSuccess(boolean disconnect) {
		// nothing to do
	}
}
