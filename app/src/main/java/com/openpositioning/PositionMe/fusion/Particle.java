package com.openpositioning.PositionMe.fusion;

import java.util.Random;
public class Particle {
    float x;       // x坐标
    float y;       // y坐标
    int floor;     // 楼层
    double weight; // 权重

    // 构造函数等...
    public Particle(float x, float y, int floor) {
        this.x = x;
        this.y = y;
        this.floor = floor;
        this.weight = 1.0; // 初始权重都一样
    }

    /**
     * 根据给定的位移来移动粒子，并增加一些随机噪声。
     * @param deltaX X方向的位移
     * @param deltaY Y方向的位移
     * @param random 一个随机数生成器实例
     */
    public void move(float deltaX, float deltaY, Random random) {
        // 这是基础移动：直接把位移加上去
        this.x += deltaX;
        this.y += deltaY;

        // 这是关键的“增加噪声”部分
        // random.nextGaussian() 生成一个符合标准正态分布（均值为0，标准差为1）的随机数
        // 我们把它乘以一个很小的值（比如0.1），来控制噪声的大小（即标准差为0.1米）
        // 这意味着大部分噪声会在-0.1米到+0.1米之间，少数会更大一些。这比均匀分布更真实。
        this.x += random.nextGaussian() * 0.1;
        this.y += random.nextGaussian() * 0.1;
    }
}
