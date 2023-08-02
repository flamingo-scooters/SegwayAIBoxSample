package com.segway.robot.sample.uart;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Scooter Speed
 */
public class WheelData implements Parcelable {
    private long timestamp;
    private int wheelSpeed;

    public WheelData() {
    }

    public WheelData(long timestamp, int wheelSpeed) {
        this.timestamp = timestamp;
        this.wheelSpeed = wheelSpeed;
    }

    protected WheelData(Parcel in) {
        timestamp = in.readLong();
        wheelSpeed = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
        dest.writeInt(wheelSpeed);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<WheelData> CREATOR = new Creator<WheelData>() {
        @Override
        public WheelData createFromParcel(Parcel in) {
            return new WheelData(in);
        }

        @Override
        public WheelData[] newArray(int size) {
            return new WheelData[size];
        }
    };

    public int getWheelSpeed() {
        return wheelSpeed;
    }

    public void setWheelSpeed(int wheelSpeed) {
        this.wheelSpeed = wheelSpeed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "WheelData{" +
                "timestamp=" + timestamp +
                ", wheelSpeed=" + wheelSpeed +
                '}';
    }
}
