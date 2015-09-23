package nl.dobots.bluenet.ble.cfg;

import nl.dobots.bluenet.ble.core.BleCoreTypes;

/**
 * Defines fields and properties used by the ble classes for json objects that are given back and forth
 *
 * Created on 15-7-15
 * @author Dominik Egger
 */
public class BleTypes extends BleCoreTypes {

	public static final String PROPERTY_IS_CROWNSTONE = "isCrownstone";
	public static final String PROPERTY_CALIBRATED_RSSI = "calibratedRssi";
	public static final String PROPERTY_MINOR = "minor";
	public static final String PROPERTY_MAJOR = "major";
	public static final String PROPERTY_PROXIMITY_UUID = "proximityUuid";
	public static final String PROPERTY_IS_IBEACON = "isIBeacon";

	public static final String PROPERTY_IS_DOBEACON = "isDoBeacon";
	public static final String PROPERTY_IS_FRIDGE = "isFridge";
}
