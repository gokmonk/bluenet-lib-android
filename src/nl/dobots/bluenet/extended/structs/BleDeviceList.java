package nl.dobots.bluenet.extended.structs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by dominik on 15-7-15.
 */
public class BleDeviceList extends ArrayList<BleDevice> {

	public enum SortOrder {
		ascending,
		descending
	}

	public BleDevice getDevice(String address) {
		for (BleDevice dev : this) {
			if (dev.getAddress().matches(address)) {
				return dev;
			}
		}
		return null;
	}

	public boolean contains(BleDevice device) {
		return getDevice(device.getAddress()) != null;
	}

	public void updateDevice(BleDevice device) {
		BleDevice storedDevice = getDevice(device.getAddress());
		if (storedDevice == null) {
			add(device);
		} else {
			int index = indexOf(storedDevice);
			set(index, device);
		}
	}

	public void sort() {
		sort(SortOrder.descending);
	}

	public void sort(final SortOrder order) {
		Collections.sort(this, new Comparator<BleDevice>() {
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
	}

	public BleDeviceList clone() {
		return (BleDeviceList)super.clone();
	}

}
