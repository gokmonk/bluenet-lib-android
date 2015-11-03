package nl.dobots.bluenet.utils;

import android.util.Log;

import nl.dobots.bluenet.ble.core.BleCore;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 3-11-15
 *
 * @author Dominik Egger
 */
public class BleLog {

	private static int _logLevel = Log.VERBOSE;

	/**
	 * Uses levels found in android.util.Log
	 * @param level the log level to be set
	 */
	public static void setLogLevel(int level) {
		_logLevel = level;
	}

	// Helper functions for logging
	public static void LOGd(String tag, String message) {
		if (_logLevel <= Log.DEBUG) {
			Log.d(tag, message);
		}
	}

	public static void LOGd(String tag, String fmt, Object ... args) {
		LOGd(tag, String.format(fmt, args));
	}

	public static void LOGe(String tag, String message) {
		if (_logLevel <= Log.ERROR) {
			Log.e(tag, message);
		}
	}

	public static void LOGe(String tag, String message, Throwable tr) {
		if (_logLevel <= Log.ERROR) {
			Log.e(tag, message, tr);
		}
	}

	public static void LOGe(String tag, String fmt, Object ... args) {
		LOGe(tag, String.format(fmt, args));
	}

	public static void LOGv(String tag, String message) {
		if (_logLevel <= Log.VERBOSE) {
			Log.v(tag, message);
		}
	}

}
