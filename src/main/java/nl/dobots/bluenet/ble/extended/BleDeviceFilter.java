package nl.dobots.bluenet.ble.extended;

/** Include filter bitmask.
 *
 * Filter can be a combination of several values. To check if a type is included by the filter: (filter & CROWNSTONE_PLUG) != 0
 *
 */

public class BleDeviceFilter {
	public static final int IBEACON = 1;
	public static final int CROWNSTONE_PLUG = 2;
	public static final int CROWNSTONE_BUILTIN = 4;
	public static final int GUIDESTONE = 8;
	public static final int SETUPSTONE = 16;
	public static final int DFUSTONE = 32;

	// combinations
	public static final int ALL = 0xFFFFFF;
	public static final int ANYSTONE = CROWNSTONE_PLUG | CROWNSTONE_BUILTIN | GUIDESTONE | SETUPSTONE | DFUSTONE;
}