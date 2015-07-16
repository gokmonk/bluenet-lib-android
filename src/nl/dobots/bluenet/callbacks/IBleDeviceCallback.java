package nl.dobots.bluenet.callbacks;

import nl.dobots.bluenet.extended.structs.BleDevice;

/**
 * Created by dominik on 15-7-15.
 */
public interface IBleDeviceCallback extends IBaseCallback {

    void onSuccess(BleDevice device);

}
