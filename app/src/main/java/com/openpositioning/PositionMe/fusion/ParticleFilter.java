package com.openpositioning.PositionMe.fusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleFilter {

    private List<Particle> particles;
    private int numParticles;
    private Random random = new Random(); // 在类的成员变量里创建一个随机数生成器

    public ParticleFilter(int numParticles) {
        this.numParticles = numParticles;
        this.particles = new ArrayList<>(numParticles);
    }

    /**
     * 步骤 1: 初始化
     * 在地图的某个区域内随机撒点
     */
    /**
     * 步骤 1: 初始化
     * 在地图的某个区域内随机撒点
     * @param mapBounds 地图的边界信息
     */
    public void initialize(MapBounds mapBounds) {
        // 1. 清空旧的粒子列表，确保每次初始化都是全新的开始
        particles.clear();

        // 2. 计算地图的宽度和高度
        float width = mapBounds.maxX - mapBounds.minX;
        float height = mapBounds.maxY - mapBounds.minY;

        // 3. 这是一个循环，会执行 numParticles 次，i会从0变到numParticles-1
        for (int i = 0; i < numParticles; i++) {

            // 4. 生成一个0.0到1.0之间的随机小数，乘以宽度，再加上最小X值
            //    这就得到了一个在 minX 和 maxX 之间的随机X坐标
            float randomX = mapBounds.minX + random.nextFloat() * width;

            // 5. 同理，生成一个在 minY 和 maxY 之间的随机Y坐标
            float randomY = mapBounds.minY + random.nextFloat() * height;

            // 6. 假设开始时都在0楼
            int initialFloor = 0;

            // 7. 使用这些随机生成的坐标和楼层，创建一个新的粒子对象
            Particle newParticle = new Particle(randomX, randomY, initialFloor);

            // 8. 把这个新创建的粒子添加到我们的粒子列表中
            particles.add(newParticle);
        }
    }


    /**
     * 步骤 2: 预测
     * 根据 PDR 的移动数据，移动所有粒子
     * @param movement 包含本次移动 deltaX 和 deltaY 的对象
     */
    public void predict(PDRMovement movement) {
        // 1. 获取本次移动的x和y方向的距离
        float dx = movement.deltaX;
        float dy = movement.deltaY;

        // 2. 遍历粒子列表中的每一个粒子 p
        for (Particle p : particles) {
            // 3. 调用该粒子自己的 move 方法，让它移动
            //    我们把移动距离和粒子滤波器自己的随机数生成器传给它
            p.move(dx, dy, this.random);
        }
    }

    /**
     * 步骤 3: 更新
     * 根据 WiFi/GNSS 测量结果，更新每个粒子的权重
     * @param measurement 包含测量位置和精度的对象
     */
    public void updateWeights(Measurement measurement) {
        // 1. 我们需要一个变量来记录所有粒子权重的新增总和，以便后续归一化
        double totalWeight = 0;

        // 2. 遍历每一个粒子 p
        for (Particle p : particles) {
            // 3. 计算这个粒子到测量点的欧几里得距离的平方
            //    (x1-x2)^2 + (y1-y2)^2
            //    我们用距离的平方可以避免开方运算，效率更高。
            double distanceSquared = Math.pow(p.x - measurement.x, 2) + Math.pow(p.y - measurement.y, 2);

            // 4. 使用高斯函数计算这个粒子基于本次测量的“似然度”（likelihood）
            //    这就是 p(z|x) 的核心，即“在x位置看到z测量的概率”
            //    公式是: (1/sqrt(2*PI*sigma^2)) * exp(-(distance^2)/(2*sigma^2))
            //    我们忽略前面的常数部分，因为它在归一化时会被消掉。
            double sigma = measurement.accuracy;
            double likelihood = Math.exp(-distanceSquared / (2 * Math.pow(sigma, 2)));

            // 5. 更新粒子的权重。新的权重 = 旧的权重 * 本次计算出的似然度
            //    如果一个粒子连续多次都离测量点很近，它的权重会持续增大。
            p.weight *= likelihood;

            // 6. 累加这个新权重到总权重中
            totalWeight += p.weight;
        }

        // 7. 归一化所有粒子的权重（让它们的总和等于1）
        //    这是为下一步的“重采样”做准备。
        for (Particle p : particles) {
            if (totalWeight > 0) {
                p.weight /= totalWeight;
            } else {
                // 如果所有粒子权重都变成0（极少发生，但可能），给它们一个平均权重
                p.weight = 1.0 / numParticles;
            }
        }
    }

    /**
     * 步骤 4: 重采样
     * 根据当前所有粒子的权重，生成一个全新的粒子集合。
     * 权重高的粒子有更大概率被多次选中，权重低的则可能被淘汰。
     * 这个实现使用了“低方差采样”（或称“轮盘赌采样”）算法。
     */
    public void resample() {
        // 1. 创建一个新的、空的列表，用来存放重采样后的新粒子
        List<Particle> newParticles = new ArrayList<>(numParticles);

        // 2. 计算当前权重最大的值，用于轮盘赌算法
        double maxWeight = 0;
        for (Particle p : particles) {
            if (p.weight > maxWeight) {
                maxWeight = p.weight;
            }
        }

        // 如果所有粒子权重都为0，无法进行重采样，直接返回
        if (maxWeight == 0) {
            return;
        }

        // 3. 轮盘赌算法的核心
        //    想象一个周长为 maxWeight * 2.0 的轮盘
        //    我们从一个随机的位置开始，然后均匀地在轮盘上走 N 步来挑选粒子（跨过小的扇区
        double beta = 0.0;
        int index = random.nextInt(numParticles); // 随机选一个起始粒子

        for (int i = 0; i < numParticles; i++) {
            beta += random.nextDouble() * 2.0 * maxWeight;

            // 寻找下一个权重足够大的粒子
            while (beta > particles.get(index).weight) {
                beta -= particles.get(index).weight;
                index = (index + 1) % numParticles; // 循环地移动到下一个粒子
            }

            // 找到了！这个粒子 'index' 被选中了。
            // 我们不是直接把它放进去，而是创建一个它的“克隆”
            Particle selected = particles.get(index);
            Particle newParticle = new Particle(selected.x, selected.y, selected.floor);

            // 把这个克隆体添加到新粒子列表中
            // 注意：新粒子的权重在构造函数里已经被默认设为1.0，为下一轮做好了准备
            newParticles.add(newParticle);
        }

        // 4. 最后，用这个全新的、充满了“精英”和他们“克隆体”的列表，
        //    替换掉旧的、权重不均的粒子列表。
        this.particles = newParticles;
    }

//    // 其他辅助方法
//    private void normalizeWeights() { ... }
//    private double calculateDistance(Particle p, Position pos) { ... }
//    private double gaussian(double distance, double sigma) { ... }

    /**
     * 获取最终的估计位置
     * 使用所有粒子的“加权平均值”。
     * 权重越大的粒子，对最终结果的影响越大。
     * @return 估计的 Position 对象
     */
    public Position getEstimatedPosition() {
        if (particles == null || particles.isEmpty()) {
            return new Position(0, 0);
        }

        float expectedX = 0;
        float expectedY = 0;
        double totalWeight = 0;

        // 遍历所有粒子，计算加权总和
        for (Particle p : particles) {
            expectedX += p.x * p.weight; // 坐标乘以它的权重
            expectedY += p.y * p.weight;
            totalWeight += p.weight;     // 累加总权重
        }

        // 如果总权重为0（极少发生），就返回简单平均
        if (totalWeight == 0) {
            float sumX = 0, sumY = 0;
            for (Particle p : particles) {
                sumX += p.x;
                sumY += p.y;
            }
            return new Position(sumX / particles.size(), sumY / particles.size());
        }

        // 最终坐标 = 加权总和 / 总权重
        return new Position((float)(expectedX / totalWeight), (float)(expectedY / totalWeight));
    }
}