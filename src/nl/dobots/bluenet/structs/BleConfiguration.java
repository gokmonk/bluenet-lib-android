package nl.dobots.bluenet.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.BleTypes;

/**
 * Created by dominik on 15-7-15.
 */
public class BleConfiguration {
    private int type;
    private int length;
    private byte[] payload;

    public BleConfiguration(int type, int length, byte[] payload) {
        this.type = type;
        this.length = length;
        this.payload = payload;
    }

    public BleConfiguration(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        type = bb.get();
        bb.get(); // skip the RESERVED
        length = bb.getShort();
        payload = new byte[length];
        bb.get(payload);
    }

    public byte[] toArray() {
        ByteBuffer bb = ByteBuffer.allocate(4 + payload.length);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.put((byte) type);
        bb.put((byte) BleTypes.RESERVED); // add RESERVED (for byte alignment)
        bb.putShort((short) length);
        bb.put(payload);

        return bb.array();
    }

    @Override
    public String toString() {
        return String.format("{type: %d, length: %d, payload: %s}", type, length, Arrays.toString(payload));
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

}
