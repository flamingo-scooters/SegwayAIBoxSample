package com.segway.robot.sample.uart;

import android.os.RemoteException;

import com.segway.robot.datatransmit.DataTransmitV1;
import com.segway.robot.datatransmit.exception.DataTransmitUnbindException;
import com.segway.robot.datatransmit.utils.NativeByteBuffer;

/**
 * @author xianghui.zhang
 * <p>
 * Communication data is defined as follows.
 * <p>
 * #pragma pack(1)
 * typedef struct AiResult_ {
 * uint8_t aiInferenceResult:4;
 * uint8_t pedestrianDetected:1;
 * uint8_t reserve:3;
 * } AiResult;
 * #pragma pack()
 * <p>
 * #pragma pack(1)
 * typedef struct WheelData_ {
 * int8_t power;
 * int8_t speed;
 * } WheelData;
 * #pragma pack()
 * <p>
 * #pragma pack(1)
 * typedef struct GpsData_ {
 * int32_t timestamp;
 * int32_t gps_longitude;
 * int32_t gps_latitude;
 * int32_t gps_at;
 * int16_t gps_heading;
 * int16_t gps_speed;
 * int16_t gps_hdop;
 * } GpsData;
 * #pragma pack()
 * <p>
 * The format of the data received is as follows.
 * -------------------------------------------------------------------------------------------
 * Data Type(1byte) | Received timestamp(8byte) | Data length(1byte) | Data content(N byte) ｜
 * -------------------------------------------------------------------------------------------
 * <p>
 * WheelData is of type 1.
 * GpsData is of type 2.
 * <p>
 * WheelData:
 * power: (Data Format: int8_t 1Bytes, 0-100)
 * speed: (Data Format: int8_t 1Bytes, 0-25km/h)
 * <p>
 * GpsData:
 * timestamp: (Data Format: uint32 4Bytes,  Unit: sec)
 * gps_latitude: GPS latitude information (Data Format: int32 4bytes,  22631426 is 22.631426)
 * gps_longitude: GPS longitude information (Data Format: int32 4bytes,  -114123922 is -114.123922)
 * gps_at: GPS attitude information (Data Format: Data Format: int32 4bytes,  12345 is 1234.5m)
 * gps heading: GPS direction information (Data Format: int16 2bytes,  456 is 45.6 degree)
 * gps_speed: Gps speed information (Data Format: int16 2bytes,  123 is 12.3 Km/h)
 * gps_hdop: Gps horizontal dilution of precision (Data Format: int16 2bytes, 123 is 1.23)
 */
public class ProtocolV1Util {

    public static final int TYPE_WHEEL = 1;
    public static final int TYPE_IOT = 2;

    private static final int[] WHEEL_PARAM = new int[]{TYPE_WHEEL};
    private static final int[] WHEEL_LEN_PARAM = new int[]{2};
    private static final int[] IOT_PARAM = new int[]{TYPE_IOT};
    private static final int[] IOT_LEN_PARAM = new int[]{22};

    public static void sendAiResult(int aiInferenceResult, int pedestrianDetected) throws DataTransmitUnbindException, RemoteException {
        NativeByteBuffer nativeData = NativeByteBuffer.obtain(1);
        nativeData.put(aiInferenceResult, 4);
        nativeData.put(pedestrianDetected, 1);
        DataTransmitV1.getInstance().sendData(nativeData.getData());
        nativeData.recycle();
    }

    public static WheelData getWheelData() throws DataTransmitUnbindException, RemoteException {
        byte[] data = DataTransmitV1.getInstance().getData(WHEEL_PARAM, WHEEL_LEN_PARAM);
        if (data != null && data.length > 0) {
            NativeByteBuffer nativeData = NativeByteBuffer.obtain().wrap(data);
            int type = nativeData.getByte();
            long timestamp = nativeData.getLong();
            int size = nativeData.getByte();
            int power = nativeData.getByte();
            int speed = nativeData.getByte();
            nativeData.recycle();
            if (type == TYPE_WHEEL) {
                return new WheelData(timestamp, speed);
            }
        }
        return null;
    }

    public static LocationData getLocationData() throws DataTransmitUnbindException, RemoteException {
        byte[] data = DataTransmitV1.getInstance().getData(IOT_PARAM, IOT_LEN_PARAM);
        if (data != null && data.length > 0) {
            NativeByteBuffer nativeData = NativeByteBuffer.obtain().wrap(data);
            int type = nativeData.getByte();
            long timestamp = nativeData.getLong();
            int size = nativeData.getByte();
            long locTimestamp = nativeData.getInt() * 1000000L;
            int longitude = nativeData.getInt();
            int latitude = nativeData.getInt();
            int gpsAt = nativeData.getInt();
            int gpsHeading = nativeData.getShort();
            int gpsSpeed = nativeData.getShort();
            int gpsHdop = nativeData.getShort();
            nativeData.recycle();
            if (type == TYPE_IOT) {
                return new LocationData(locTimestamp, longitude, latitude, gpsAt, gpsHeading, gpsSpeed, gpsHdop);
            }
        }
        return null;
    }

}
