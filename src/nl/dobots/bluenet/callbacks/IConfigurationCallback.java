package nl.dobots.bluenet.callbacks;

import nl.dobots.bluenet.structs.BleConfiguration;

/**
 * Created by dominik on 15-7-15.
 */
public interface IConfigurationCallback extends IBaseCallback {

	void onSuccess(BleConfiguration configuration);

}
