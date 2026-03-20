package com.openpositioning.PositionMe.fusion;

/**
 * 这个类封装了一次外部测量的数据。
 * 来自Wi-Fi定位或GNSS定位。
 * 它包含一个坐标点和一个表示精度的值。
 */
public class Measurement {
    public final float x;
    public final float y;
    public final double accuracy; // 定位的精度/标准差（比如5米）

    public Measurement(float x, float y, double accuracy) {
        this.x = x;
        this.y = y;
        this.accuracy = accuracy;
    }
}
