package nl.dobots.bluenet.ble.base.structs;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 6-7-17
 *
 * @author Bart van Vliet
 */
public class ScheduleEntryPacket {
	private static final String TAG = ScheduleEntryPacket.class.getCanonicalName();
	public static final int ENTRY_SIZE = 1+1+1+4+2+3;

	public static final int REPEAT_MINUTES = 0;
	public static final int REPEAT_DAY     = 1;
	public static final int REPEAT_ONCE    = 2;

	public static final int ACTION_SWITCH = 0;
	public static final int ACTION_FADE   = 1;
	public static final int ACTION_TOGGLE = 2;

	public static final int OVERRIDE_BIT_POS_ALL      = 0;
	public static final int OVERRIDE_BIT_POS_LOCATION = 1;

	public static final int WEEKDAY_BIT_POS_SUNDAY    = 0;
	public static final int WEEKDAY_BIT_POS_MONDAY    = 1;
	public static final int WEEKDAY_BIT_POS_TUESDAY   = 2;
	public static final int WEEKDAY_BIT_POS_WEDNESDAY = 3;
	public static final int WEEKDAY_BIT_POS_THURSDAY  = 4;
	public static final int WEEKDAY_BIT_POS_FRIDAY    = 5;
	public static final int WEEKDAY_BIT_POS_SATURDAY  = 6;
	public static final int WEEKDAY_BIT_POS_ALL_DAYS  = 7;
	public static final int WEEKDAY_MASK_ALL_DAYS     = 0x7F; // 01111111


	public int _reserved;
	public int _repeatType;
	public int _actionType;
	public int _overrideMask;
	public long _timestamp;

	// Repeat
	public int _minutes;
	public int _dayOfWeekMask;

	// Action
	public int _switchVal;
	public int _fadeDuration;

	public ScheduleEntryPacket() {
	}

	public ScheduleEntryPacket(int reserved, int repeatType, int actionType, int overrideMask, long timestamp) {
		_reserved = reserved;
		_repeatType = repeatType;
		_actionType = actionType;
		_overrideMask = overrideMask;
		_timestamp = timestamp;
	}

	/* Returns whether this entry is active or not
	 */
	public boolean isActive() {
		return (_timestamp != 0);
	}

	public void setInactive() {
		_timestamp = 0;
	}

	/**
	 * Returns true when given bit position is set in override mask.
	 * Use the OVERRIDE_BIT_POS_* values.
	 */
	public boolean isIgnoreBitSet(int bitPos) {
		return (_overrideMask & (1 << bitPos)) != 0;
	}

	/**
	 * Returns true when given bit position is set in day of week mask.
	 * Use the WEEKDAY_BIT_POS_* values.
	 */
	public boolean isWeekdayBitSet(int bitPos) {
		return (_dayOfWeekMask & (1 << bitPos)) != 0;
	}

	/**
	 * Sets a bit in the day of week mask. Use the WEEKDAY_BIT_POS_* values.
	 */
	public void setWeekdayBit(int bitPos) {
		_dayOfWeekMask |= (1 << bitPos);
	}

	/**
	 * Returns true if given day is set, or all bit is set in day of week mask.
	 */
	public boolean isWeekdayActive(int bitPos) {
		return (_dayOfWeekMask & (1 << WEEKDAY_BIT_POS_ALL_DAYS)) != 0 || isWeekdayBitSet(bitPos);
	}

	public boolean isValidPacketToSet() {
		if (_timestamp == 0) {
			return false;
		}
		switch (_repeatType) {
			case REPEAT_MINUTES:{
				if (_minutes == 0) {
					return false;
				}
				break;
			}
			case REPEAT_DAY: {
				if (_dayOfWeekMask == 0) {
					return false;
				}
				break;
			}
			case REPEAT_ONCE: {
				break;
			}
			default:
				return false;
		}
		switch (_actionType) {
			case ACTION_SWITCH: {
				break;
			}
			case ACTION_FADE: {
				if (_fadeDuration <= 0) {
					return false;
				}
				break;
			}
			case ACTION_TOGGLE: {
				break;
			}
			default:
				return false;
		}
		return true;
	}

	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return fromArray(bb);
	}

	public boolean fromArray(ByteBuffer bb) {
		if (bb.remaining() < ENTRY_SIZE) {
			BleLog.getInstance().LOGd(TAG, "remaining bytes: " + bb.remaining());
			return false;
		}
		_reserved = BleUtils.toUint8(bb.get());
		byte type = bb.get();
		_repeatType = type & 0x0F;
		_actionType = (type & 0xF0) >> 4;
		_overrideMask = BleUtils.toUint8(bb.get());
		_timestamp = BleUtils.toUint32(bb.getInt());

		boolean repeatSuccess = true;
		switch (_repeatType) {
			case REPEAT_MINUTES:{
				_minutes = BleUtils.toUint16(bb.getShort());
				if (_minutes == 0) {
					BleLog.getInstance().LOGw(TAG, "repeat minutes: " + _minutes);
					repeatSuccess = false;
				}
				break;
			}
			case REPEAT_DAY: {
				_dayOfWeekMask = BleUtils.toUint8(bb.get());
				bb.get(); // Not used
				// If every day is 1, then the "all days" bit should be set too
				if ((_dayOfWeekMask & WEEKDAY_MASK_ALL_DAYS) == WEEKDAY_MASK_ALL_DAYS) {
					BleLog.getInstance().LOGd(TAG, "dayOfWeekMask: " + _dayOfWeekMask);
					setWeekdayBit(WEEKDAY_BIT_POS_ALL_DAYS);
				}
				if (_dayOfWeekMask == 0) {
					BleLog.getInstance().LOGd(TAG, "invalid dayOfWeekMask: " + _dayOfWeekMask);
					repeatSuccess = false;
				}
				break;
			}
			case REPEAT_ONCE: {
				bb.getShort(); // Not used
				break;
			}
			default:
				bb.getShort(); // Not used
				BleLog.getInstance().LOGd(TAG, "uknown repeat type: " + _repeatType);
				repeatSuccess = false;
		}
		if (!repeatSuccess && isActive()) {
			return false;
		}

		boolean actionSuccess = true;
		switch (_actionType) {
			case ACTION_SWITCH: {
				_switchVal = BleUtils.toUint8(bb.get());
				bb.getShort(); // Not used
				break;
			}
			case ACTION_FADE: {
				_switchVal = BleUtils.toUint8(bb.get());
				_fadeDuration = BleUtils.toUint16(bb.getShort());
				break;
			}
			case ACTION_TOGGLE: {
				bb.get(); // Not used
				bb.getShort(); // Not used
				break;
			}
			default:
				bb.get(); // Not used
				bb.getShort(); // Not used
				BleLog.getInstance().LOGd(TAG, "uknown action type: " + _repeatType);
				actionSuccess = false;
		}
		if (!actionSuccess && isActive()) {
			return false;
		}
		return true;
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(ENTRY_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte)_reserved);
		int type = _repeatType + (_actionType << 4);
		bb.put((byte)type);
		bb.put((byte)_overrideMask);
		bb.put(BleUtils.uint32ToByteArray(_timestamp));
		switch (_repeatType) {
			case REPEAT_MINUTES:{
				bb.putShort((short)_minutes);
				break;
			}
			case REPEAT_DAY: {
				// If every day is set, then the "all days" bit should be set too
				if ((_dayOfWeekMask & WEEKDAY_MASK_ALL_DAYS) == WEEKDAY_MASK_ALL_DAYS) {
					setWeekdayBit(WEEKDAY_BIT_POS_ALL_DAYS);
				}
				bb.put((byte)_dayOfWeekMask);
				bb.put((byte)0); // Not used
				break;
			}
			case REPEAT_ONCE:
			default: {
				bb.putShort((short)0); // Not used
				break;
			}
		}
		switch (_actionType) {
			case ACTION_SWITCH: {
				bb.put((byte)_switchVal);
				bb.putShort((short)0); // Not used
				break;
			}
			case ACTION_FADE: {
				bb.put((byte)_switchVal);
				bb.putShort((short)_fadeDuration);
				break;
			}
			case ACTION_TOGGLE:
			default: {
				bb.put((byte)0); // Not used
				bb.putShort((short)0); // Not used
				break;
			}
		}
		return bb.array();
	}

	public String toString() {

		String repeatStr = "";
		switch (_repeatType) {
			case REPEAT_MINUTES:{
				repeatStr += "every " + _minutes + " minutes";
				break;
			}
			case REPEAT_DAY: {
				int daysofWeekMask = _dayOfWeekMask;
				repeatStr += "weekdays mask: " + Integer.toBinaryString(0x100 | daysofWeekMask).substring(1);
				break;
			}
			case REPEAT_ONCE:
			default: {
				repeatStr += "no repeats";
				break;
			}
		}
		String actionStr = "";
		switch (_actionType) {
			case ACTION_SWITCH: {
				actionStr += "switchVal: " + _switchVal;
				break;
			}
			case ACTION_FADE: {
				actionStr += "switchVal: " + _switchVal + " duration:" + _fadeDuration;
				break;
			}
			case ACTION_TOGGLE:
			default: {
				actionStr += "toggle";
				break;
			}
		}

		return String.format(Locale.ENGLISH, "repeatType=%d actionType=%d override=%d timestamp=%d repeat=%s action=%s" ,
				_repeatType,
				_actionType,
				_overrideMask,
				_timestamp,
				repeatStr,
				actionStr
				);
	}
}
