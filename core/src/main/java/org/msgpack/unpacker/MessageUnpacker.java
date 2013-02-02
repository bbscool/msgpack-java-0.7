//
// MessagePack for Java
//
// Copyright (C) 2009-2013 FURUHASHI Sadayuki
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//
package org.msgpack.unpacker;

import java.io.IOException;
import java.io.EOFException;
import java.math.BigInteger;
import org.msgpack.buffer.Buffer;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.accept.Accept;
import org.msgpack.unpacker.accept.IntAccept;
import org.msgpack.unpacker.accept.LongAccept;
import org.msgpack.unpacker.accept.BigIntegerAccept;
import org.msgpack.unpacker.accept.DoubleAccept;
import org.msgpack.unpacker.accept.BooleanAccept;
import org.msgpack.unpacker.accept.NilAccept;
import org.msgpack.unpacker.accept.ByteArrayAccept;
import org.msgpack.unpacker.accept.StringAccept;
import org.msgpack.unpacker.accept.ArrayAccept;
import org.msgpack.unpacker.accept.MapAccept;

public class MessageUnpacker implements Unpacker {
    private Buffer buffer;
    private UnpackerChannel ch;

    protected int rawSizeLimit = 134217728;
    protected int arraySizeLimit = 4194304;
    protected int mapSizeLimit = 2097152;

    private byte headByte = REQUIRE_TO_READ_HEAD;

    private byte[] raw;
    private int rawFilled;

    private static final IntAccept intAccept = new IntAccept();
    private static final LongAccept longAccept = new LongAccept();
    private static final BigIntegerAccept bigIntegerAccept = new BigIntegerAccept();
    private static final DoubleAccept doubleAccept = new DoubleAccept();
    private static final BooleanAccept booleanAccept = new BooleanAccept();
    private static final NilAccept nilAccept = new NilAccept();
    private static final ByteArrayAccept byteArrayAccept = new ByteArrayAccept();
    private static final StringAccept stringAccept = new StringAccept();
    private static final ArrayAccept arrayAccept = new ArrayAccept();
    private static final MapAccept mapAccept = new MapAccept();

    public MessageUnpacker(Buffer buffer) {
        this.buffer = buffer;
        if(buffer instanceof UnpackerChannelProvider) {
            ch = ((UnpackerChannelProvider) buffer).getUnpackerChannel();
        } else {
            ch = new BufferUnpackerChannel(buffer);
        }
    }

    private static final byte REQUIRE_TO_READ_HEAD = (byte) 0xc6;

    private void resetHeadByte() {
        headByte = REQUIRE_TO_READ_HEAD;
    }

    private byte getHeadByte() throws IOException {
        byte b = headByte;
        if(b == REQUIRE_TO_READ_HEAD) {
            b = headByte = ch.readByte();
        }
        return b;
    }

    @Override
    public void readToken(Accept a) throws IOException {
        if(raw != null) {
            readRawBodyCont();
            a.acceptByteArray(raw);
            raw = null;
            resetHeadByte();
            return;
        }

        final int b = (int) getHeadByte();

        if((b & 0x80) == 0) { // Positive Fixnum
            // System.out.println("positive fixnum "+b);
            a.acceptInt(b);
            resetHeadByte();
            return;
        }

        if((b & 0xe0) == 0xe0) { // Negative Fixnum
            // System.out.println("negative fixnum "+b);
            a.acceptInt(b);
            resetHeadByte();
            return;
        }

        if((b & 0xe0) == 0xa0) { // FixRaw
            int size = b & 0x1f;
            if(size == 0) {
                a.acceptEmptyByteArray();
                resetHeadByte();
                return;
            }
            readRawBody(size);
            a.acceptByteArray(raw);
            resetHeadByte();
            return;
        }

        if((b & 0xf0) == 0x90) { // FixArray
            int size = b & 0x0f;
            // System.out.println("fixarray size:"+size);
            checkArraySize(size);
            a.acceptArrayHeader(size);
            resetHeadByte();
            return;
        }

        if((b & 0xf0) == 0x80) { // FixMap
            int size = b & 0x0f;
            // System.out.println("fixmap size:"+size/2);
            checkMapSize(size);
            a.acceptMapHeader(size);
            resetHeadByte();
            return;
        }

        readTokenSwitch(a, b);
    }

    private void readTokenSwitch(Accept a, final int b) throws IOException {
        switch (b & 0xff) {
        case 0xc0: // nil
            a.acceptNil();
            resetHeadByte();
            return;
        case 0xc2: // false
            a.acceptBoolean(false);
            resetHeadByte();
            return;
        case 0xc3: // true
            a.acceptBoolean(true);
            resetHeadByte();
            return;
        case 0xca: // float
            a.acceptFloat(ch.readFloat());
            resetHeadByte();
            return;
        case 0xcb: // double
            a.acceptDouble(ch.readDouble());
            resetHeadByte();
            return;
        case 0xcc: // unsigned int 8
            a.acceptInt((int) (ch.readByte() & 0xff));
            resetHeadByte();
            return;
        case 0xcd: // unsigned int 16
            a.acceptInt((int) (ch.readShort() & 0xffff));
            resetHeadByte();
            return;
        case 0xce: // unsigned int 32
            {
                int v = ch.readInt();
                if(v < 0) {
                    a.acceptLong((long) (v & 0x7fffffff) + 0x80000000L);
                } else {
                    a.acceptInt(v);
                }
            }
            return;
        case 0xcf: // unsigned int 64
            {
                long v = ch.readLong();
                if(v < 0) {
                    a.acceptUnsignedLong(v);
                } else {
                    a.acceptLong(v);
                }
            }
            resetHeadByte();
            return;
        case 0xd0: // signed int 8
            a.acceptInt((int) ch.readByte());
            resetHeadByte();
            return;
        case 0xd1: // signed int 16
            a.acceptInt((int) ch.readShort());
            resetHeadByte();
            return;
        case 0xd2: // signed int 32
            a.acceptInt(ch.readInt());
            resetHeadByte();
            return;
        case 0xd3: // signed int 64
            a.acceptLong(ch.readLong());
            resetHeadByte();
            return;
        case 0xda: // raw 16
            {
                int size = ch.readShort() & 0xffff;
                if(size == 0) {
                    a.acceptEmptyByteArray();
                    resetHeadByte();
                    return;
                }
                readRawBody(size);
            }
            a.acceptByteArray(raw);
            resetHeadByte();
            return;
        case 0xdb: // raw 32
            {
                int size = ch.readInt();
                if(size == 0) {
                    a.acceptEmptyByteArray();
                    resetHeadByte();
                    return;
                }
                readRawBody(size);
            }
            a.acceptByteArray(raw);
            raw = null;
            resetHeadByte();
            return;
        case 0xdc: // array 16
            {
                int size = ch.readShort() & 0xffff;
                checkArraySize(size);
                a.acceptArrayHeader(size);
                resetHeadByte();
                return;
            }
        case 0xdd: // array 32
            {
                int size = ch.readInt();
                checkArraySize(size);
                a.acceptArrayHeader(size);
                resetHeadByte();
                return;
            }
        case 0xde: // map 16
            {
                int size = ch.readShort() & 0xffff;
                checkMapSize(size);
                a.acceptMapHeader(size);
                resetHeadByte();
                return;
            }
        case 0xdf: // map 32
            {
                int size = ch.readInt();
                checkMapSize(size);
                a.acceptMapHeader(size);
                resetHeadByte();
                return;
            }
        default:
            // System.out.println("unknown b "+(b&0xff));
            // headByte = CS_INVALID
            //resetHeadByte();
            throw new IOException("Invalid MessagePack format: " + b);
        }
    }

    private void checkArraySize(int unsignedSize) throws IOException {
        if(unsignedSize < 0 || unsignedSize >= arraySizeLimit) {
            String reason = String.format(
                    "Size of array (%d) over limit at %d",
                    new Object[] { unsignedSize, arraySizeLimit });
            throw new MessageSizeException(reason);
        }
    }

    private void checkMapSize(int unsignedSize) throws IOException {
        if(unsignedSize < 0 || unsignedSize >= mapSizeLimit) {
            String reason = String.format(
                    "Size of map (%d) over limit at %d",
                    new Object[] { unsignedSize, mapSizeLimit });
            throw new MessageSizeException(reason);
        }
    }

    private void checkRawSize(int unsignedSize) throws IOException {
        if(unsignedSize < 0 || unsignedSize >= rawSizeLimit) {
            String reason = String.format(
                    "Size of raw (%d) over limit at %d",
                    new Object[] { unsignedSize, rawSizeLimit });
            throw new MessageSizeException(reason);
        }
    }

    private void readRawBody(int size) throws IOException {
        checkRawSize(size);
        this.raw = new byte[size];
        this.rawFilled = 0;
        readRawBodyCont();
    }

    private void readRawBodyCont() throws IOException {
        int len = ch.read(raw, rawFilled, raw.length - rawFilled);
        rawFilled += len;
        if (rawFilled < raw.length) {
            throw new EOFException();
        }
    }

    @Override
    public boolean trySkipNil() throws IOException {
        int b = getHeadByte();
        if(b == 0xc0) {
            resetHeadByte();
            return true;
        }
        return false;
    }

    @Override
    public int readInt() throws IOException {
        readToken(intAccept);
        return intAccept.getValue();
    }

    @Override
    public long readLong() throws IOException {
        readToken(longAccept);
        return longAccept.getValue();
    }

    @Override
    public BigInteger readBigInteger() throws IOException {
        readToken(bigIntegerAccept);
        return bigIntegerAccept.getValue();
    }

    @Override
    public double readDouble() throws IOException {
        readToken(doubleAccept);
        return doubleAccept.getValue();
    }

    @Override
    public boolean readBoolean() throws IOException {
        readToken(booleanAccept);
        return booleanAccept.getValue();
    }

    @Override
    public void readNil() throws IOException {
        readToken(nilAccept);
    }

    @Override
    public byte[] readByteArray() throws IOException {
        readToken(byteArrayAccept);
        return byteArrayAccept.getValue();
    }

    @Override
    public String readString() throws IOException {
        readToken(stringAccept);
        return stringAccept.getValue();
    }

    @Override
    public int readArrayHeader() throws IOException {
        readToken(arrayAccept);
        return arrayAccept.getSize();
    }

    @Override
    public int readMapHeader() throws IOException {
        readToken(mapAccept);
        return mapAccept.getSize();
    }

    @Override
    public ValueType getNextType() throws IOException {
        final int b = (int) getHeadByte();
        if ((b & 0x80) == 0) { // Positive Fixnum
            return ValueType.INTEGER;
        }
        if ((b & 0xe0) == 0xe0) { // Negative Fixnum
            return ValueType.INTEGER;
        }
        if ((b & 0xe0) == 0xa0) { // FixRaw
            return ValueType.RAW;
        }
        if ((b & 0xf0) == 0x90) { // FixArray
            return ValueType.ARRAY;
        }
        if ((b & 0xf0) == 0x80) { // FixMap
            return ValueType.MAP;
        }
        switch (b & 0xff) {
        case 0xc0: // nil
            return ValueType.NIL;
        case 0xc2: // false
        case 0xc3: // true
            return ValueType.BOOLEAN;
        case 0xca: // float
        case 0xcb: // double
            return ValueType.FLOAT;
        case 0xcc: // unsigned int 8
        case 0xcd: // unsigned int 16
        case 0xce: // unsigned int 32
        case 0xcf: // unsigned int 64
        case 0xd0: // signed int 8
        case 0xd1: // signed int 16
        case 0xd2: // signed int 32
        case 0xd3: // signed int 64
            return ValueType.INTEGER;
        case 0xda: // raw 16
        case 0xdb: // raw 32
            return ValueType.RAW;
        case 0xdc: // array 16
        case 0xdd: // array 32
            return ValueType.ARRAY;
        case 0xde: // map 16
        case 0xdf: // map 32
            return ValueType.MAP;
        default:
            throw new MessageFormatException("Invalid MessagePack format: " + b);
        }
    }

    public void close() throws IOException {
        buffer.close();
    }
}

