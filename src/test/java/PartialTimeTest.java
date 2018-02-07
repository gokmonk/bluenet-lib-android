/**
 * Copyright (c) 2018 Bart van Vliet. All rights reserved.
 * Created on 05-02-18
 *
 * @author Bart van Vliet
 */

import org.junit.Test;

import java.util.Calendar;

import nl.dobots.bluenet.ble.base.utils.PartialTime;

import static org.junit.Assert.assertTrue;

public class PartialTimeTest {
	@Test
	public void test() {
		PartialTime pt = new PartialTime();
		long timestamp = 1515426625;
		int lsbTimestamp = (int)(timestamp % (0xFFFF+1));
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs);
	}

	@Test
	public void test2() {
		PartialTime pt = new PartialTime();
		long timestamp = 1516206008;
		int lsbTimestamp = 34186;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(reconstructedTs == 1516209546);
	}

	@Test
	public void testOverflow1() {
		PartialTime pt = new PartialTime();
		long timestamp = 0x5A53FFFF + 1500;
		int lsbTimestamp = 0xFFFF;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs+1500);
	}

	@Test
	public void testOverflow2() {
		PartialTime pt = new PartialTime();
		long timestamp = 0x5A53FFFF;
		int lsbTimestamp = 0;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs-1);
	}

	@Test
	public void testOverflow3() {
		PartialTime pt = new PartialTime();
		long timestamp = 0x5A530000 - 1;
		int lsbTimestamp = 0;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs-1);
	}

	@Test
	public void testOverflow4() {
		PartialTime pt = new PartialTime();
		long secondsFromGmt = Calendar.getInstance().getTimeZone().getRawOffset() / 1000;
		long timestamp = 0x5A537FFF - 6 - secondsFromGmt;
		int lsbTimestamp = 0x7FFF;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs-6-secondsFromGmt);
	}

	@Test
	public void testOverflow5() {
		PartialTime pt = new PartialTime();
		long timestamp = 0x5A537FFF;
		int lsbTimestamp = 0x7FFF + 1;
		long reconstructedTs = pt.reconstructTimestamp(timestamp, lsbTimestamp);
		assertTrue(timestamp == reconstructedTs-1);
	}
}
