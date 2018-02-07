package nl.dobots.bluenet.ble.base.utils;

/**
 * Copyright (c) 2018 Bart van Vliet. All rights reserved.
 * Created on 05-02-18
 *
 * @author Bart van Vliet
 */

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import nl.dobots.bluenet.utils.BleUtils;

public class PartialTime {

	final static long HALF_UINT16 = 0x7FFF;
	final static long MAX_UINT16 =  0xFFFF;


	/** Reconstructs a full timestamp from the current time and a partial timestamp.
	 *
	 * Works as long as the source of the partial timestamp is not more than 9 hours off.
	 *
	 * @param lsbTimestamp   Partial timestamp.
	 * @return               The reconstructed timestamp.
	 */
	public long reconstructTimestamp(int lsbTimestamp) {
		TimeZone timeZone = TimeZone.getTimeZone("GMT");
		Calendar calendar = Calendar.getInstance(timeZone);
		long gmtTimestamp = calendar.getTime().getTime() / 1000;
		return reconstructTimestamp(gmtTimestamp, lsbTimestamp);
	}


	/** Reconstructs a full timestamp from the current unix timestamp and a partial timestamp.
	 *
	 * Works as long as the source of the partial timestamp is not more than 9 hours off.
	 *
	 * @param unixTimestamp  Seconds since epoch (gmt).
	 * @param lsbTimestamp   Partial timestamp.
	 * @return               The reconstructed timestamp.
	 */
	public long reconstructTimestamp(long unixTimestamp, int lsbTimestamp) {
		Calendar calendar = Calendar.getInstance();
		TimeZone timeZone = calendar.getTimeZone();
		long secondsFromGmt = timeZone.getRawOffset() / 1000;
		long correctedTimestamp = unixTimestamp + secondsFromGmt;

		long reconstructedTimestamp = combineTimestamp(correctedTimestamp, lsbTimestamp);

		long delta = correctedTimestamp - reconstructedTimestamp;
		if (delta > -HALF_UINT16 && delta < HALF_UINT16) {
			return reconstructedTimestamp;
		}
		else if (delta < -HALF_UINT16) {
			reconstructedTimestamp = combineTimestamp(correctedTimestamp - MAX_UINT16, lsbTimestamp);
		}
		else if (delta > HALF_UINT16) {
			reconstructedTimestamp = combineTimestamp(correctedTimestamp + MAX_UINT16, lsbTimestamp);
		}
		return reconstructedTimestamp;
	}


	/** Replaces the least significant bytes of a timestamp by the partial timestamp.
	 *
	 * @param timestamp      Timestamp in seconds.
	 * @param lsbTimestamp   Partial timestamp.
	 * @return               Timestamp with the least significant bytes replaced by the partial timestamp.
	 */
	private long combineTimestamp(long timestamp, int lsbTimestamp) {
		byte[] arr = BleUtils.uint32ToByteArray(timestamp);
		byte[] arrLsb = BleUtils.shortToByteArray(lsbTimestamp);
		arr[0] = arrLsb[0];
		arr[1] = arrLsb[1];
		long combinedTimestamp = BleUtils.toUint32(BleUtils.byteArrayToInt(arr));
		return combinedTimestamp;
	}


// // Other method:
//	modulo = timestamp % (1 << 16);
//
//	if (modulo >= lsbTimestamp) {
//		if (modulo - lsbTimestamp > (1<<15)) {
//			// Assume partial timestamp is older
//			timeDiff = modulo - (lsbTimestamp + (1<<16));
//		}
//		else {
//			timeDiff = modulo - lsbTimestamp;
//		}
//	}
//			else {
//		if (lsbTimestamp - modulo > (1<<15)) {
//			timeDiff = (modulo + (1<<16)) - lsbTimestamp;
//		}
//		else {
//			timeDiff = modulo - lsbTimestamp;
//		}
//	}
//	// timestamp is current mesh time
//	// timeDiff is current mesh time - service data time
//	reconstructedTimestamp = timestamp - timeDiff;

}
