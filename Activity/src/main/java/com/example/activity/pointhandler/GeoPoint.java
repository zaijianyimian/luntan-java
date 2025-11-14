package com.example.activity.pointhandler;

public class GeoPoint {
    private double longitude;
    private double latitude;

    public GeoPoint(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    // getter/setter
    public double getLongitude() { return longitude; }
    public double getLatitude() { return latitude; }

    @Override
    public String toString() {
        return String.format("POINT(%f %f)", longitude, latitude);
    }
}
