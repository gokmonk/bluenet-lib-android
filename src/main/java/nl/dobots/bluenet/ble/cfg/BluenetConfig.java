package nl.dobots.bluenet.ble.cfg;

/**
 * Configuration of characteristics, uuids, configuration values, etc. used by the Bluenet code running
 * on the crownstones and guidestone.
 *
 * Needs to be kept consistent with the values used in the bluenet code!
 *
 * Created on 15-7-15
 * Updated on 9-6-16 for Protocol version 0.4.0
 * @author Dominik Egger
 */
public class BluenetConfig {

	public static final int BLE_DEVICE_ADDRESS_LENGTH = 6;
	public static final int BLE_MAX_MULTIPART_NOTIFICATION_LENGTH = 512;

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Crownstone Service
	public static final String CROWNSTONE_SERVICE_UUID =                "24f00000-7d10-4805-bfc1-7663a01c3bff";
	// Crownstone Service - Characteristics
	public static final String CHAR_CONTROL_UUID =                      "24f00001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_MESH_CONTROL_UUID =                 "24f00002-7d10-4805-bfc1-7663a01c3bff";
	// public static final String CHAR_MESH_READ_UUID =                    "24f00003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_CONFIG_CONTROL_UUID =               "24f00004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_CONFIG_READ_UUID =                  "24f00005-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_STATE_CONTROL_UUID =                "24f00006-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_STATE_READ_UUID =                   "24f00007-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SESSION_NONCE_UUID =                "24f00008-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RECOVERY_UUID =                     "24f00009-7d10-4805-bfc1-7663a01c3bff";

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Setup Service
	public static final String SETUP_SERVICE_UUID =                     "24f10000-7d10-4805-bfc1-7663a01c3bff";
	// Setup Service - Characteristics
	public static final String CHAR_SETUP_CONTROL_UUID =                "24f10001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_MAC_ADDRESS_UUID =                  "24f10002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SESSION_KEY_UUID =                  "24f10003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_CONFIG_CONTROL_UUID =         "24f10004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_CONFIG_READ_UUID =            "24f10005-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SETUP_SESSION_NONCE_UUID =          "24f10008-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// General Service
	public static final String GENERAL_SERVICE_UUID =                   "24f20000-7d10-4805-bfc1-7663a01c3bff";
	// General Service - Characteristics
	public static final String CHAR_TEMPERATURE_UUID =                  "24f20001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RESET_UUID =                        "24f20002-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Power Service
	public static final String POWER_SERVICE_UUID =                     "24f30000-7d10-4805-bfc1-7663a01c3bff";
	// Power Service - Characteristics
	public static final String CHAR_PWM_UUID =                          "24f30001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RELAY_UUID =                        "24f30002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_POWER_SAMPLES_UUID =                "24f30003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_POWER_CONSUMPTION_UUID =            "24f30004-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Indoor Localization Service
	public static final String INDOOR_LOCALIZATION_SERVICE_UUID =       "24f40000-7d10-4805-bfc1-7663a01c3bff";
	// Indoor Localization Service - Characteristics
	public static final String CHAR_TRACK_CONTROL_UUID =                "24f40001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_TRACKED_DEVICES_UUID =              "24f40002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SCAN_CONTROL_UUID =                 "24f40003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SCANNED_DEVICES_UUID =              "24f40004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_RSSI_UUID =                         "24f40005-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Schedule Service
	public static final String SCHEDULE_SERVICE_UUID =                  "24f50000-7d10-4805-bfc1-7663a01c3bff";
	// Alert Service - Characteristics
	public static final String CHAR_CURRENT_TIME_UUID =                 "24f50001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_WRITE_SCHEDULE_ENTRY =              "24f50002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_LIST_SCHEDULE_ENTRIES =             "24f50003-7d10-4805-bfc1-7663a01c3bff";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Alert Service
	public static final String ALERT_SERVICE_UUID =                     "24f60000-7d10-4805-bfc1-7663a01c3bff";
	// Alert Service - Characteristics
	public static final String CHAR_SUPPORTED_NEW_ALERT_UUID =          "24f60001-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_NEW_ALERT_UUID =                    "24f60002-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_SUPPORTED_UNREAD_ALERT_UUID =       "24f60003-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_UNREAD_ALERT_UUID =                 "24f60004-7d10-4805-bfc1-7663a01c3bff";
	public static final String CHAR_ALERT_CONTROL_POINT_UUID =          "24f60005-7d10-4805-bfc1-7663a01c3bff";

	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh Service
	public static final String MESH_SERVICE_UUID =                      "0000fee4-0000-1000-8000-00805f9b34fb";
	// Mesh Service - Characteristics
	public static final String MESH_META_CHARACTERISTIC_UUID =          "2a1e0004-fd51-d882-8ba8-b98c0000cd1e";
	public static final String MESH_DATA_CHARACTERISTIC_UUID =          "2a1e0005-fd51-d882-8ba8-b98c0000cd1e";
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// CommandMsg types
	public static final char CMD_SWITCH =                               0;    // 0x00
	public static final char CMD_PWM =                                  1;    // 0x01
	public static final char CMD_SET_TIME =                             2;    // 0x02
	public static final char CMD_GOTO_DFU =                             3;    // 0x03
	public static final char CMD_RESET =                                4;    // 0x04
	public static final char CMD_FACTORY_RESET =                        5;    // 0x05
	public static final char CMD_KEEP_ALIVE_STATE =                     6;    // 0x06
	public static final char CMD_KEEP_ALIVE =                           7;    // 0x07
	public static final char CMD_ENABLE_MESH =                          8;    // 0x08
	public static final char CMD_ENABLE_ENCRYPTION =                    9;    // 0x09
	public static final char CMD_ENABLE_IBEACON =                       10;   // 0x0A
	public static final char CMD_ENABLE_CONT_POWER_MEASURE =            11;   // 0x0B
	public static final char CMD_ENABLE_SCANNER =                       12;   // 0x0C
	public static final char CMD_SCAN_DEVICES =                         13;   // 0x0D
	public static final char CMD_USER_FEEDBACK =                        14;   // 0x0E
	public static final char CMD_SCHEDULE_ENTRY =                       15;   // 0x0F
	public static final char CMD_RELAY =                                16;   // 0x10
	public static final char CMD_VALIDATE_SETUP =                       17;   // 0x11
	public static final char CMD_REQUEST_SERVICE_DATA =                 18;   // 0x12
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Configuration types
	public static final char CONFIG_NAME =                              0;    // 0x00
	public static final char CONFIG_DEVICE_TYPE =                       1;    // 0x01
	public static final char CONFIG_ROOM =                              2;    // 0x02
	public static final char CONFIG_FLOOR =                             3;    // 0x03
	public static final char CONFIG_NEARBY_TIMEOUT =                    4;    // 0x04
	public static final char CONFIG_PWM_FREQ =                          5;    // 0x05
	public static final char CONFIG_IBEACON_MAJOR =                     6;    // 0x06
	public static final char CONFIG_IBEACON_MINOR =                     7;    // 0x07
	public static final char CONFIG_IBEACON_PROXIMITY_UUID =            8;    // 0x08
	public static final char CONFIG_IBEACON_TXPOWER =                   9;    // 0x09
	public static final char CONFIG_WIFI_SETTINGS =                     10;   // 0x0A
	public static final char CONFIG_TX_POWER =                          11;   // 0x0B
	public static final char CONFIG_ADV_INTERVAL =                      12;   // 0x0C
	public static final char CONFIG_PASSKEY =							13;   // 0x0D
	public static final char CONFIG_MIN_ENV_TEMP =                      14;   // 0x0E
	public static final char CONFIG_MAX_ENV_TEMP =                      15;   // 0x0F
	public static final char CONFIG_SCAN_DURATION =                     16;   // 0x10
	public static final char CONFIG_SCAN_SEND_DELAY =                   17;   // 0x11
	public static final char CONFIG_SCAN_BREAK_DURATION =               18;   // 0x12
	public static final char CONFIG_BOOT_DELAY =                        19;   // 0x13
	public static final char CONFIG_MAX_CHIP_TEMP =                     20;   // 0x14
	public static final char CONFIG_SCAN_FILTER =                       21;   // 0x15
	public static final char CONFIG_SCAN_FILTER_SEND_FRACTION =         22;   // 0x16
	public static final char CONFIG_CURRENT_LIMIT =                     23;   // 0x17
	public static final char CONFIG_MESH_ENABLED =                      24;   // 0x18
	public static final char CONFIG_ENCRYPTION_ENABLED =                25;   // 0x19
	public static final char CONFIG_IBEACON_ENABLED =                   26;   // 0x1A
	public static final char CONFIG_SCANNER_ENABLED =                   27;   // 0x1B
	public static final char CONFIG_CONT_POWER_SAMPLER_ENABLED =        28;   // 0x1C
	public static final char CONFIG_TRACKER_ENABLED =                   29;   // 0x1D
	public static final char CONFIG_ADC_SAMPLE_RATE =                   30;   // 0x1E
	public static final char CONFIG_POWER_SAMPLE_BURST_INTERVAL =       31;   // 0x1F
	public static final char CONFIG_POWER_SAMPLE_CONT_INTERVAL =        32;   // 0x20
	public static final char CONFIG_POWER_SAMPLE_CONT_NUM_SAMPLES =     33;   // 0x21
	public static final char CONFIG_CROWNSTONE_ID =                     34;   // 0x22
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// State types
	public static final char STATE_RESET_COUNTER =                      128;  // 0x80
	public static final char STATE_SWITCH_STATE =                       129;  // 0x81
	public static final char STATE_ACCUMULATED_ENERGY =                 130;  // 0x82
	public static final char STATE_POWER_USAGE =                        131;  // 0x83
	public static final char STATE_TRACKED_DEVICES =                    132;  // 0x84
	public static final char STATE_SCHEDULE =                           133;  // 0x85
	public static final char STATE_OPERATION_MODE =                     134;  // 0x86
	public static final char STATE_TEMPERATURE =                        135;  // 0x87
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Value Op Code
	public static final char READ_VALUE =                               0;    // 0x00
	public static final char WRITE_VALUE =                              1;    // 0x01
	public static final char NOTIFY_VALUE =                             2;    // 0x02

	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Value set at reserved bytes for alignment
	public static final char RESERVED =                                 0x00;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh handles (channel)
	public static final char CHANNEL_HUB =                              1;    // 0x01
	public static final char CHANNEL_DATA =                             2;    // 0x02
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mesh messages types
	public static final char MESH_CONTROL =                             0;    // 0x00;
	public static final char MESH_BEACON =                              1;    // 0x01;
	public static final char MESH_CONFIG =                              2;    // 0x02;
	public static final char MESH_STATE =                               3;    // 0x03;
	public static final char MESH_SCAN =                                101;  // 0x65;
	public static final char MESH_POWER_SAMPLES =                       102;  // 0x66;
	public static final char MESH_EVENT =                               103;  // 0x67;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// iBeacon Identifiers
	public static final int APPLE_COMPANY_ID =                          0x004c;
	public static final int IBEACON_ADVERTISEMENT_ID =                  0x0215;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// DoBots
	public static final short DOBOTS_COMPANY_ID =                       (short)0x038E;
	public static final short CROWNSTONE_SERVICE_DATA_UUID =            (short)0xC001;
	public static final short GUIDESTONE_SERVICE_DATA_UUID =            (short)0xC002;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Reset OP codes
	public static final int RESET_DEFAULT =                             1;     // 0x01
	public static final int RESET_DFU =                                 66;    // 0x42
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Recovery code
	public static final int RECOVERY_CODE =                             0xDEADBEEF;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Verification code for ECB encryption
	public static final int CAFEBABE = 0xCAFEBABE;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Alert Bit Mask
	public static final int ALERT_TEMP_LOW_POS =                        0;
	public static final int ALERT_TEMP_HIGH_POS =                       1;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Device Type Identifiers
	public static final int DEVICE_UNDEF =                              0;
	public static final int DEVICE_CROWNSTONE =                         1;
	public static final int DEVICE_GUIDESTONE =                         2;
	public static final int DEVICE_FRIDGE =                             3;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	// constant used to convert the advertisement interval from ms to the unit expected by the
	// characteristic (increments of 0.625 ms)
	public static final double ADVERTISEMENT_INCREMENT =                0.625;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static final int PWM_ON =                                    100;
	public static final int PWM_OFF =                                   0;
	public static final int RELAY_ON =                                  255;
	public static final int RELAY_OFF =                                 0;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////
}
