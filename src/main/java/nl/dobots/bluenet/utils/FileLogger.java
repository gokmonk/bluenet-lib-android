package nl.dobots.bluenet.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Semaphore;

import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 24-1-17
 *
 * Use:
 *
 * 1. Create FileLogger object
 * 2. Call checkPermissions, if result is true, continue with step 6, else
 * 3. Call requestPermissions with Activity
 * 4. In Activity, overload onRequestPermissionsResult and call there the
 *    handlePermissionResult of the FileLogger
 * 5. If handlePermissionResult calls the status callback's onSuccess, continue with step 6, else
 *    abort because we can't log to file without write permissions
 * 6. Assign FileLogger to BleLog with BleLog.addFileLogger();
 * 7. Done
 *
 * Note:
 *
 * Files are created per day, up to a max of MAX_LOG_FILE_SIZE. Once a log file exceeds that limit,
 * a new file is created.
 * If the free disk space drops below MIN_FREE_SPACE, logging to file will be disabled
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class FileLogger {

	public static final String TAG = "FileLogger";

	private static final int PERMISSIONS_REQUEST_WRITE_STORAGE = 105;

	private static final long MIN_FREE_SPACE = 10 * 1024 * 1024; // 10 MB
	private static final long MAX_LOG_FILE_SIZE = 500 * 1024 * 1024; // 500 MB

	// log level identifier used by android log in logcat, first two are not used
	private static final char LOG_LEVELS_STR[] = { ' ', ' ', 'V', 'D', 'I', 'W', 'E' };

	private static final SimpleDateFormat _logTimestampFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
	private static final SimpleDateFormat _fileNameTimestampFormat = new SimpleDateFormat("yyMMdd_HHmmss");

	private boolean _hasWritePermissions;
	private DataOutputStream _logFileDos;
	private Date _logFileDate;
	private File _logFile;
	private String _logDir;
	private boolean _enabled = true;

	public FileLogger(String dir) {
		_logDir = dir;
	}

	public boolean checkPermissions(Activity context) {
		if (Build.VERSION.SDK_INT >= 23) {
			_hasWritePermissions = ContextCompat.checkSelfPermission(context,
					Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		} else {
			_hasWritePermissions = true;
		}
		return _hasWritePermissions;
	}

	public boolean hasRequestedPermissions() {
		return _hasWritePermissions;
	}

	public void requestPermissions(Activity context) {
		ActivityCompat.requestPermissions(context,
				new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
				PERMISSIONS_REQUEST_WRITE_STORAGE);
	}

	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_WRITE_STORAGE: {
				if (grantResults.length > 0 &&	grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					_hasWritePermissions = true;
					callback.onSuccess();
				} else {
					callback.onError(PackageManager.PERMISSION_DENIED);
				}
				return true;
			}
		}
		return false;
	}


	public void logToFile(int level, String tag, String line) {
		if (_enabled && checkFile()) {
			try {
				String log = String.format("%s %s/%s: %s\r\n",
						_logTimestampFormat.format(new Date()),
						LOG_LEVELS_STR[level],
						tag,
						line);
				_logFileDos.write(log.getBytes());
			} catch (IOException e) {
				Log.e(TAG, "Failed to write to log file");
				e.printStackTrace();
			}
		}
	}

	private boolean createLogFile() {

		_logFileDate = new Date();
		String fileName = "log_" + _fileNameTimestampFormat.format(_logFileDate) + ".txt";

		File path = new File(Environment.getExternalStorageDirectory().getPath() + "/" + _logDir);
		_logFile = new File(path, fileName);

		path.mkdirs();
		try {
			_logFileDos = new DataOutputStream(new FileOutputStream(_logFile));
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Error creating " + fileName, e);
			return false;
		}

		return true;
	}

	private Semaphore _semaphore = new Semaphore(1);

	private boolean checkFile() {

		try {
			_semaphore.acquire();

			if (_logFileDos == null) {
				createLogFile();
			} else {
				try {
					if (_logFileDate.getDay() != new Date().getDay()) {
						_logFileDos.close();
						createLogFile();
					}

					if (_logFile.length() > MAX_LOG_FILE_SIZE) {
						_logFileDos.close();
						createLogFile();
					}

					if (_logFile.getFreeSpace() < MIN_FREE_SPACE) {
						_logFileDos.close();
						_enabled = false;
					}
				} catch (IOException e) {
					Log.e("BleLog", "Error closing logfile", e);
				}
			}

			_semaphore.release();

			return true;

		} catch (InterruptedException e) {
			Log.e("BleLog", "Failed to aquire semaphore", e);

			return false;
		}

	}



}
