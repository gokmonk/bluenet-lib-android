package nl.dobots.bluenet.callbacks;

/**
 * Created by dominik on 28-7-15.
 */
public interface IDiscoveryCallback extends IStatusCallback {

	void onDiscovery(String serviceUuid, String characteristicUuid);

}
