package nl.dobots.bluenet.utils;

import android.util.Log;

import java.util.HashMap;

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

	private int _logLevel = Log.VERBOSE;

	private static BleLog _instance;

	public BleLog(int logLevel) {
		_logLevel = logLevel;
	}

	public BleLog() {
		_logLevel = Log.VERBOSE;
	}

	public static BleLog getInstance() {
		if (_instance == null) {
			_instance = new BleLog();
		}
		return _instance;
	}

	private HashMap<String, Integer> _logLevels = new HashMap<>();

	public void setLogLevelPerTag(String tag, int logLevel) {
		_logLevels.put(tag, logLevel);
	}

	public Integer getLogLevel(String tag) {
		return _logLevels.get(tag);
	}

	/**
	 * Uses levels found in android.util.Log
	 * @param level the log level to be set
	 */
	public void setLogLevel(int level) {
		_logLevel = level;
	}

	public int getLogLevel() {
		return _logLevel;
	}

	private int getLineNumber() {
		return Thread.currentThread().getStackTrace()[5].getLineNumber();
	}

	private boolean checkLogLevel(String tag, int checkLevel) {
		Integer logLevel = _logLevels.get(tag);
		if (logLevel == null) {
			return _logLevel <= checkLevel;
		} else {
			return Math.max(logLevel, _logLevel) <= checkLevel;
		}
	}

	private void log(int level, String tag, String message) {
		if (checkLogLevel(tag, level)) {
			Log.println(level, tag, String.format("[%d] %s", getLineNumber(), message));
		}
	}

	// Helper functions for logging
	public void LOGi(String tag, String message) {
		log(Log.INFO, tag, message);
	}

	public void LOGi(String tag, String fmt, Object ... args) {
		log(Log.INFO, tag, String.format(fmt, args));
	}

	// Helper functions for logging
	public void LOGd(String tag, String message) {
		log(Log.DEBUG, tag, message);
	}

	public void LOGd(String tag, String fmt, Object ... args) {
		log(Log.DEBUG, tag, String.format(fmt, args));
	}

	public void LOGe(String tag, String message) {
		log(Log.ERROR, tag, message);
	}

	public void LOGe(String tag, String message, Throwable tr) {
		log(Log.ERROR, tag, message + '\n' + Log.getStackTraceString(tr));
	}

	public void LOGe(String tag, String fmt, Object ... args) {
		log(Log.ERROR, tag, String.format(fmt, args));
	}

	public void LOGv(String tag, String message) {
		log(Log.VERBOSE, tag, message);
	}

	public void LOGv(String tag, String fmt, Object ... args) {
		log(Log.VERBOSE, tag, String.format(fmt, args));
	}

	public void LOGw(String tag, String message) {
		log(Log.WARN, tag, message);
	}

	public void LOGw(String tag, String fmt, Object ... args) {
		log(Log.WARN, tag, String.format(fmt, args));
	}

}
