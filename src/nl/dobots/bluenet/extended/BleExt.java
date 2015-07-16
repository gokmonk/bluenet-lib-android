package nl.dobots.bluenet.extended;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import nl.dobots.bluenet.BleBase;
import nl.dobots.bluenet.BleDeviceConnectionState;
import nl.dobots.bluenet.BleDeviceFilter;
import nl.dobots.bluenet.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.callbacks.IDataCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.core.BleCoreTypes;
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceList;

/**
 * Created by dominik on 15-7-15.
 */
public class BleExt {

    private static final String TAG = BleExt.class.getSimpleName();

    private BleBase _bleBase;

    private BleDeviceList _devices = new BleDeviceList();

    private String _targetAddress;

    private BleDeviceFilter _scanFilter = BleDeviceFilter.all;
    private BleDeviceConnectionState _connectionState = BleDeviceConnectionState.uninitialized;

    private Activity _context;

    public BleExt() {
        _bleBase = new BleBase();
    }

    protected void LOGd(String message) {
        Log.d(TAG, message);
    }

    protected void LOGd(String fmt, Object ... args) {
        LOGd(String.format(fmt, args));
    }

    protected void LOGe(String message) {
        Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    protected void LOGe(String fmt, Object ... args) {
        LOGe(String.format(fmt, args));
    }

    public void init(Activity context, final IStatusCallback callback) {
        _context = context;

        // wrap the callback to update the connection state
        _bleBase.init(context, new IStatusCallback() {
            @Override
            public void onSuccess() {
                _connectionState = BleDeviceConnectionState.initialized;
                callback.onSuccess();
            }

            @Override
            public void onError(int error) {
                _connectionState = BleDeviceConnectionState.uninitialized;
                callback.onError(error);
            }
        });
    }

    /**
     * Returns a raw json object. To get an already parsed BleDevice object, use the
     * overloaded function instead
     * @param callback
     * @return
     */
    public boolean startEndlessScan(final IDataCallback callback) {
        if (_connectionState != BleDeviceConnectionState.initialized) {
            LOGe("State is not initialized: %s", _connectionState.toString());
            callback.onError(BleExtTypes.WRONG_STATE);
            return false;
        }

        _devices.clear();
        _connectionState = BleDeviceConnectionState.scanning;

        return _bleBase.startEndlessScan(new IDataCallback() {
            @Override
            public void onData(JSONObject json) {
                BleDevice device;
                try {
                    device = new BleDevice(json);
                } catch (JSONException e) {
                    LOGe("Failed to parse json into device!");
                    e.printStackTrace();
                    return;
                }

                switch (_scanFilter) {
                    case crownstone:
                        if (!device.isCrownstone()) return;
                        break;
                    case doBeacon:
                    case iBeacon:
                        if (!device.isIBeacon()) return;
                        break;
                    case all:
                        break;
                }

                _devices.updateDevice(device);
                _devices.sort();
                callback.onData(json);
            }

            @Override
            public void onError(int error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Returns an object of type BleDevice which is an already parsed json object
     * If you want to get the raw json object, use the overloaded function instead
     * @param callback
     * @return
     */
    public boolean startEndlessScan(final IBleDeviceCallback callback) {
        return startEndlessScan(new IDataCallback() {
            @Override
            public void onData(JSONObject json) {
                try {
                    BleDevice device = _devices.getDevice(json.getString(BleExtTypes.PROPERTY_ADDRESS));
                    callback.onSuccess(device);
                } catch (JSONException e) {
                    // should not happen, otherwise it would have happened in the other
                    // startEndlessScan function already!
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int error) {
                onError(error);
            }
        });
    }

    public boolean stopEndlessScan(final IStatusCallback callback) {
        _connectionState = BleDeviceConnectionState.initialized;
        return _bleBase.stopEndlessScan(callback);
    }

    private static final int CONNECT_TIMEOUT = 5; // 5 seconds

    public void connect(String address, final IStatusCallback callback) {
        if (_targetAddress != null) {
            _targetAddress = address;
        }
        _connectionState = BleDeviceConnectionState.connecting;
        _bleBase.connectDevice(_targetAddress, CONNECT_TIMEOUT, new IStatusCallback() {
            @Override
            public void onSuccess() {
                onConnect();
                callback.onSuccess();
            }

            @Override
            public void onError(int error) {
                callback.onError();
            }
        });
    }

    private void onConnect() {
        LOGd("successfully connected");
        _connectionState = BleDeviceConnectionState.connected;
    }

    public boolean disconnect(final IStatusCallback callback) {
        if (!checkState(BleDeviceConnectionState.connected, callback)) return false;

        _connectionState = BleDeviceConnectionState.disconnecting;
        return _bleBase.disconnectDevice(_targetAddress, new IStatusCallback() {
            @Override
            public void onSuccess() {
                onDisconnect();
                callback.onSuccess();
            }

            @Override
            public void onError(int error) {
                callback.onError(error);
            }
        });
    }

    private void onDisconnect() {
        LOGd("successfully disconnected");
        _connectionState = BleDeviceConnectionState.initialized;
        // todo: clear characteristics
    }

    private boolean checkState(BleDeviceConnectionState state, IStatusCallback callback) {
        if (_connectionState != state) {
            LOGe("wrong state: %s", _connectionState.toString());
            callback.onError(BleExtTypes.WRONG_STATE);
            return false;
        }
        return true;
    }

    public void discoverServices(IDataCallback callback) {

        _bleBase.discoverServices(address, callback);
    }

    public void setScanFilter(BleDeviceFilter filter) {
        _scanFilter = filter;
    }

    public BleDeviceFilter getScanFilter() {
        return _scanFilter;
    }
}
