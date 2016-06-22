package nl.dobots.bluenet.utils;

import android.util.Log;

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

	private static int getLineNumber() {
		return Thread.currentThread().getStackTrace()[5].getLineNumber();
	}

	private static void log(int level, String tag, String message) {
		if (_logLevel <= level) {
			Log.println(level, tag, String.format("[%d] %s", getLineNumber(), message));
		}
	}

	// Helper functions for logging
	public static void LOGi(String tag, String message) {
		log(Log.INFO, tag, message);
	}

	public static void LOGi(String tag, String fmt, Object ... args) {
		log(Log.INFO, tag, String.format(fmt, args));
	}

	// Helper functions for logging
	public static void LOGd(String tag, String message) {
		log(Log.DEBUG, tag, message);
	}

	public static void LOGd(String tag, String fmt, Object ... args) {
		log(Log.DEBUG, tag, String.format(fmt, args));
	}

	public static void LOGe(String tag, String message) {
		log(Log.ERROR, tag, message);
	}

	public static void LOGe(String tag, String message, Throwable tr) {
		log(Log.ERROR, tag, message + '\n' + Log.getStackTraceString(tr));
	}

	public static void LOGe(String tag, String fmt, Object ... args) {
		log(Log.ERROR, tag, String.format(fmt, args));
	}

	public static void LOGv(String tag, String message) {
		log(Log.VERBOSE, tag, message);
	}

	public static void LOGw(String tag, String message) {
		log(Log.WARN, tag, message);
	}

	public static void LOGw(String tag, String fmt, Object ... args) {
		log(Log.WARN, tag, String.format(fmt, args));
	}

}
