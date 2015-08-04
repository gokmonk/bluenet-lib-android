package nl.dobots.bluenet.extended.structs;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Created by dominik on 15-7-15.
 */
public class BleDeviceMap extends HashMap<String, BleDevice> {

	public enum SortOrder {
		ascending,
		descending
	}

	public BleDevice getDevice(String address) {
		return get(address);
	}

	public boolean contains(BleDevice device) {
		return containsKey(device.getAddress());
	}

	public void updateDevice(BleDevice device) {
		put(device.getAddress(), device);
	}

	public ArrayList<BleDevice> getSortedList() {
		return getSortedList(SortOrder.descending);
	}

	public ArrayList<BleDevice> getSortedList(final SortOrder order) {
		ArrayList<BleDevice> result = new ArrayList<>();
		result.addAll(values());
		Collections.sort(result, new Comparator<BleDevice>() {
			@Override
			public int compare(BleDevice lhs, BleDevice rhs) {
				switch (order) {
					case ascending:
						return lhs.getRssi() - rhs.getRssi();
					case descending:
						return rhs.getRssi() - lhs.getRssi();
				}
				return 0;
			}
		});
		return result;
	}

}
