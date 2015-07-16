package nl.dobots.bluenet.extended.structs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import nl.dobots.bluenet.extended.BleExtTypes;

/**
 * Created by dominik on 15-7-15.
 */
public class BleDevice {

    enum DeviceType {
        unknown,
        crownstone,
        dobeacon,
        ibeacon
    }

    private String _address;
    private String _name;
    private int _rssi;
    private DeviceType _type;

    private int _major;
    private int _minor;
    private UUID _proximityUuid;
    private int _calibratedRssi;

    public BleDevice(String address, String name, int rssi) {
        this._address = address;
        this._name = name;
        this._rssi = rssi;
        this._type = DeviceType.unknown;
    }

    public BleDevice(JSONObject json) throws JSONException {
        this._address = json.getString(BleExtTypes.PROPERTY_ADDRESS);
        this._name = json.getString(BleExtTypes.PROPERTY_NAME);
        this._rssi = json.getInt(BleExtTypes.PROPERTY_RSSI);
        this._type = determineDeviceType(json);
        if (isIBeacon()) {
            this._major = json.getInt(BleExtTypes.PROPERTY_MAJOR);
            this._minor = json.getInt(BleExtTypes.PROPERTY_MINOR);
            this._proximityUuid = (UUID) json.get(BleExtTypes.PROPERTY_PROXIMITY_UUID);
            this._calibratedRssi = json.getInt(BleExtTypes.PROPERTY_CALIBRATED_RSSI);
        }
    }

    public boolean isIBeacon() {
        return _type == DeviceType.ibeacon || _type == DeviceType.dobeacon;
    }

    public boolean isCrownstone() {
        return _type == DeviceType.crownstone;
    }

    private DeviceType determineDeviceType(JSONObject json) throws JSONException {
        if (json.has(BleExtTypes.PROPERTY_IS_CROWNSTONE)) {
            if (json.getBoolean(BleExtTypes.PROPERTY_IS_CROWNSTONE)) {
                return DeviceType.crownstone;
            }
        }
        if (json.has(BleExtTypes.PROPERTY_IS_IBEACON)) {
            if (json.getBoolean(BleExtTypes.PROPERTY_IS_IBEACON)) {
                return DeviceType.ibeacon;
            }
        }
        return DeviceType.unknown;
    }

    public String getAddress() {
        return _address;
    }

    public void setAddress(String address) {
        this._address = address;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        this._name = name;
    }

    public int getRssi() {
        return _rssi;
    }

    public void setRssi(int rssi) {
        this._rssi = rssi;
    }
}
