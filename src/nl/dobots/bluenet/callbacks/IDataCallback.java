package nl.dobots.bluenet.callbacks;

import org.json.JSONObject;

/**
 * Created by dominik on 14-7-15.
 */
public interface IDataCallback extends IBaseCallback {

	void onData(JSONObject json);

}
