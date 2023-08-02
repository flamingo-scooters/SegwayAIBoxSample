package com.segway.robot.sample.uart;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * GPS Data
 */
public class LocationData implements Parcelable {
    long timestamp;
    private int longitude;
    private int latitude;
    private int attitude;
    private int direction;
    private int speed;
    private int hdop;

    public LocationData() {
    }

    public LocationData(long timestamp, int longitude, int latitude, int attitude, int direction, int speed, int hdop) {
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.attitude = attitude;
        this.direction = direction;
        this.speed = speed;
        this.hdop = hdop;
    }

    protected LocationData(Parcel in) {
        timestamp = in.readLong();
        longitude = in.readInt();
        latitude = in.readInt();
        attitude = in.readInt();
        direction = in.readInt();
        speed = in.readInt();
        hdop = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(timestamp);
        dest.writeInt(longitude);
        dest.writeInt(latitude);
        dest.writeInt(attitude);
        dest.writeInt(direction);
        dest.writeInt(speed);
        dest.writeInt(hdop);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<LocationData> CREATOR = new Creator<LocationData>() {
        @Override
        public LocationData createFromParcel(Parcel in) {
            return new LocationData(in);
        }

        @Override
        public LocationData[] newArray(int size) {
            return new LocationData[size];
        }
    };

    public int getLongitude() {
        return longitude;
    }

    public void setLongitude(int longitude) {
        this.longitude = longitude;
    }

    public int getLatitude() {
        return latitude;
    }

    public void setLatitude(int latitude) {
        this.latitude = latitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getAttitude() {
        return attitude;
    }

    public void setAttitude(int attitude) {
        this.attitude = attitude;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public int getHdop() {
        return hdop;
    }

    public void setHdop(int hdop) {
        this.hdop = hdop;
    }

    @Override
    public String toString() {
        return "LocationData{" +
                ", timestamp=" + timestamp +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                ", attitude=" + attitude +
                ", direction=" + direction +
                ", speed=" + speed +
                ", hdop=" + hdop +
                '}';
    }
}
