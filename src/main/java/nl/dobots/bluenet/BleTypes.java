package nl.dobots.bluenet;

public class BleTypes {

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Indoor Localization Service
	public static final String INDOOR_LOCALIZATION_SERVICE_UUID =       "7e170000-429c-41aa-83d7-d91220abeb33";
	// Indoor Localization Service - Characteristics
	public static final String CHAR_RSSI_UUID =                         "7e170001-429c-41aa-83d7-d91220abeb33";
	public static final String CHAR_ADD_TRACKED_DEVICE_UUID =           "7e170002-429c-41aa-83d7-d91220abeb33";
	public static final String CHAR_DEVICE_SCAN_UUID =                  "7e170003-429c-41aa-83d7-d91220abeb33";
	public static final String CHAR_DEVICE_LIST_UUID =                  "7e170004-429c-41aa-83d7-d91220abeb33";
	public static final String CHAR_LIST_TRACKED_DEVICES_UUID =         "7e170005-429c-41aa-83d7-d91220abeb33";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// General Service
	public static final String GENERAL_SERVICE_UUID =                   "f5f90000-59f9-11e4-aa15-123b93f75cba";
	// General Service - Characteristics
	public static final String CHAR_TEMPERATURE_UUID =                  "f5f90001-59f9-11e4-aa15-123b93f75cba";
//	public static final String unused                                   "f5f90002-59f9-11e4-aa15-123b93f75cba";
//	public static final String unused                                   "f5f90003-59f9-11e4-aa15-123b93f75cba";
//	public static final String unused                                   "f5f90004-59f9-11e4-aa15-123b93f75cba";
	public static final String CHAR_RESET_UUID =                        "f5f90005-59f9-11e4-aa15-123b93f75cba";
	public static final String CHAR_MESH_UUID =                         "f5f90006-59f9-11e4-aa15-123b93f75cba";
	public static final String CHAR_SET_CONFIGURATION_UUID =            "f5f90007-59f9-11e4-aa15-123b93f75cba";
	public static final String CHAR_SELECT_CONFIGURATION_UUID =         "f5f90008-59f9-11e4-aa15-123b93f75cba";
	public static final String CHAR_GET_CONFIGURATION_UUID =            "f5f90009-59f9-11e4-aa15-123b93f75cba";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Power Service
	public static final String POWER_SERVICE_UUID =                     "5b8d0000-6f20-11e4-b116-123b93f75cba";
	// Power Service - Characteristics
	public static final String CHAR_PWM_UUID =                          "5b8d0001-6f20-11e4-b116-123b93f75cba";
	public static final String CHAR_SAMPLE_CURRENT_UUID =               "5b8d0002-6f20-11e4-b116-123b93f75cba";
	public static final String CHAR_CURRENT_CURVE_UUID =                "5b8d0003-6f20-11e4-b116-123b93f75cba";
	public static final String CHAR_CURRENT_CONSUMPTION_UUID =          "5b8d0004-6f20-11e4-b116-123b93f75cba";
	public static final String CHAR_CURRENT_LIMIT_UUID =                "5b8d0005-6f20-11e4-b116-123b93f75cba";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Alert Service
	public static final String ALERT_SERVICE_UUID =                     "33690000-2a0a-11e5-b345-feff819cdc9f";
	// Alert Service - Characteristics
	public static final String CHAR_NEW_ALERT_UUID =                    "33690002-2a0a-11e5-b345-feff819cdc9f";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// BleConfiguration types
	public static final char CONFIG_TYPE_NAME =                         0x00;
	public static final char CONFIG_TYPE_DEVICE_TYPE =                  0x01;
	public static final char CONFIG_TYPE_ROOM =                         0x02;
	public static final char CONFIG_TYPE_FLOOR =                        0x03;
	public static final char CONFIG_TYPE_NEARBY_TIMEOUT =               0x04;
	public static final char CONFIG_TYPE_PWM_FREQUENCY =                0x05;
	public static final char CONFIG_TYPE_IBEACON_MAJOR =                0x06;
	public static final char CONFIG_TYPE_IBEACON_MINOR =                0x07;
	public static final char CONFIG_TYPE_IBEACON_PROXIMITY_UUID =       0x08;
	public static final char CONFIG_TYPE_IBEACON_RSSI =                 0x09;
	public static final char CONFIG_TYPE_WIFI =                         0x0A;
	public static final char CONFIG_TYPE_TX_POWER =                     0x0B;
	public static final char CONFIG_TYPE_ADV_INTERVAL =                 0x0C;
	public static final char CONFIG_TYPE_PASSKEY =                      0x0D;
	public static final char CONFIG_TYPE_MIN_ENV_TEMP =                 0x0E;
	public static final char CONFIG_TYPE_MAX_ENV_TEMP =                 0x0F;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Value set at reserved bytes for alignment
	public static final char RESERVED =                                 0x00;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh messages
	public static final char CHANNEL_DATA =                             0x02;
	public static final char MESH_TYPE_PWM =                            0x01;
	public static final char MESH_TYPE_BEACON_CONFIG =                  0x02;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// iBeacon Identifiers
	public static final short APPLE_COMPANY_ID =                        0x004c;
	public static final short IBEACON_ADVERTISEMENT_ID =                0x0215;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// DoBots
	public static final short DOBOTS_COMPANY_ID =                       0x1111;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Sample Current OP codes
	public static final short SAMPLE_CURRENT_CONSUMPTION =              0x01;
	public static final short SAMPLE_CURRENT_CURVE =                    0x02;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Reset OP codes
	public static final int RESET_DEFAULT =                             1;
	public static final int RESET_BOOTLOADER =                          66;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

}
