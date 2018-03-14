package nl.dobots.bluenet.service;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;

public class BluetoothPermissionRequest extends AppCompatActivity {

	private static final String TAG = BluetoothPermissionRequest.class.getCanonicalName();

	private BleScanService _service;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// do not need to set a content view, we just use this activity to receive the
		// permission results

		// create / bind to the BleScanService
		Intent intent = new Intent(this, BleScanService.class);
		bindService(intent, _connection, Context.BIND_AUTO_CREATE);
	}

	// if the service was connected successfully, the service connection gives us access to the service
	private ServiceConnection _connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// get the service from the binder
			BleScanService.BleScanBinder binder = (BleScanService.BleScanBinder) service;
			_service = binder.getService();
			Log.i(TAG, "request permissions");
			_service.getScanner().checkLocationServicesPermissions(true, BluetoothPermissionRequest.this, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "disconnected from service");
		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// unbind again from the service
		unbindService(_connection);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (_service.getScanner().getBleExt().handlePermissionResult(requestCode, permissions, grantResults)) {
			return;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
