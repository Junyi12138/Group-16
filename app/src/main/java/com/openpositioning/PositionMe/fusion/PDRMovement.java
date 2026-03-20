package com.openpositioning.PositionMe.fusion;

/**
 * 这个类用来封装一次PDR（行人航位推算）的移动数据。
 * x方向和y方向的位移。
 */
public class PDRMovement {
    public final float deltaX;
    public final float deltaY;

    public PDRMovement(float deltaX, float deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }
}
