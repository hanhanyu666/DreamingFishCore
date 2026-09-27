# Rank 黑洞击杀特效

1.21.1 的客户端表现以参考图中的引力透镜为目标：更大的纯黑球形视界、橙红至炽白的宽吸积盘、环绕视界顶部的放大光弧，以及会弯曲实际场景画面的引力场。倾斜吸积盘固定在世界坐标中，物质流螺旋下落；坍缩后释放带场景折射的主冲击波与余波。

默认一次演出为 36 tick（1.8 秒）。约前 13% 张开视界，16%–50% 配合原有实体下沉，55%–73.5% 将核心压缩为奇点，70% 释放主冲击波并播放低音冲击，77.5% 释放余波，最终全部消散。冲击波的半径与透明度分别计算，淡出时仍向外传播。

## 渲染维护

- `BlackHoleGeometry` 只生成位置与顶点颜色；可以脱离 Minecraft 渲染线程进行几何检查和预览。
- `KillEffectClientRenderer` 在 `AFTER_LEVEL` 负责世界位置、视锥剔除、观察方向和四个绘制阶段：阴影 → 后侧光 → 黑色半球 → 前侧光。此事件位于世界合成之后、原版手持物品和 HUD 绘制之前；空姿态栈需应用事件的视图矩阵。不要启用阶段内透明四边形重排，否则后侧吸积盘可能覆盖核心。
- `BlackHoleLensRenderer` 每帧至多复制一次场景颜色与深度，通过 `black_hole_lens` 着色器扭曲背景。最多选择屏幕上最大的四个透镜，重叠时选择主导位移，避免连续击杀把画面反复扭曲。`BlackHoleLensProjection` 拒绝背向、近裁剪面和屏幕外的无效投影。
- 着色器读取复制的深度，避免拉伸挡在黑洞前的墙体；几何也执行深度测试。场景扭曲遵循原版“扭曲效果”强度设置，归零时仍显示核心和吸积盘。
- 发光使用 `SRC_ALPHA + ONE` 混合。Minecraft 的 `ADDITIVE_TRANSPARENCY` 是 `ONE + ONE`，不会应用顶点透明度；这里使用 `LIGHTNING_TRANSPARENCY`。
- 使用独立即时缓冲，不向光影加载器的延迟世界缓冲提交特效。若当前帧仍处于光影包私有帧缓冲，跳过场景折射；着色器加载失败时保留几何特效。退出世界、资源重载和窗口尺寸变化会释放或重建场景副本。光影包兼容性和实际帧耗时由客户端实测确认。
- 距离超过 24 格或同时存在超过 6 个特效时减少曲面分段和物质流。仍保留完整核心、吸积盘和两次释放波。客户端最多维持 16 个特效。
- 调整最大波纹半径时同步维护 `reachFor`；测试覆盖小型、常规和最大体型的完整生命周期，检查顶点是否落在剔除包围盒内。

## 验证与预览

正常依赖环境下运行：

```powershell
./gradlew.bat test --tests '*BlackHoleGeometryTest' --tests '*KillEffectConfigTest'
```

使用 JDK 21 和安装了 NumPy、Pillow 的 Python，可以直接导出生产几何并生成六阶段预览：

```powershell
New-Item -ItemType Directory -Force build/black-hole-preview | Out-Null
javac -d build/black-hole-preview src/main/java/com/hhy/dreamingfishcore/gameplay/kill_effect_system/client/BlackHoleGeometry.java tools/BlackHolePreview.java
java -cp build/black-hole-preview BlackHolePreview build/black-hole-preview 27
python tools/preview_black_hole.py
```

最后一个 Java 参数是观察俯角，建议检查 5、27、80 度。输出在 `build/black-hole-preview/black-hole-sequence.png`。这些图片不是游戏截图，也不模拟地形遮挡和光影包后处理。
