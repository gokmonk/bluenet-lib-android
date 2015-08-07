package nl.dobots.bluenet.extended.structs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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

	public BleDevice updateDevice(BleDevice device) {
		if (contains(device)) {
			BleDevice old = getDevice(device.getAddress());
			old.updateRssiValue(System.currentTimeMillis(), device.getRssi());
			return old;
		} else {
			put(device.getAddress(), device);
			return device;
		}
	}

	public ArrayList<BleDevice> getList() {
		ArrayList<BleDevice> result = new ArrayList<>();
		result.addAll(values());
		return result;
	}

	public ArrayList<BleDevice> getRssiSortedList() {
		return getRssiSortedList(SortOrder.descending);
	}

	public ArrayList<BleDevice> getRssiSortedList(final SortOrder order) {
		ArrayList<BleDevice> result = new ArrayList<>();
		result.addAll(values());
		Collections.sort(result, new Comparator<BleDevice>() {
			@Override
			public int compare(BleDevice lhs, BleDevice rhs) {
				switch (order) {
					case ascending:
//						return lhs.getRssi() - rhs.getRssi();
						return lhs.getAverageRssi() - rhs.getAverageRssi();
					case descending:
//						return rhs.getRssi() - lhs.getRssi();
						return rhs.getAverageRssi() - lhs.getAverageRssi();
				}
				return 0;
			}
		});
		return result;
	}

	public ArrayList<BleDevice> getDistanceSortedList() {
		ArrayList<BleDevice> result = new ArrayList<>();
		result.addAll(values());
		Collections.sort(result, new Comparator<BleDevice>() {
			@Override
			public int compare(BleDevice lhs, BleDevice rhs) {
				double ld = lhs.getDistance();
				double rd = rhs.getDistance();
				if (ld == -1) return 1;
				if (rd == -1) return -1;
				if (ld > rd) {
					return 1;
				} else if (ld < rd) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		return result;
	}

	public void refresh() {
		for (BleDevice device : values()) {
			device.refresh();
		}
	}

}
