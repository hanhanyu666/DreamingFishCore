Xaero 依赖（compileOnly，仅编译期用）
来源: https://chocolateminecraft.com/maven
- xaero.map:xaeroworldmap-neoforge-1.21.1:1.44.2   （小地图 26.6.0 要求 >= 1.44.0）
- xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0
- xaero.lib:xaerolib-neoforge-1.21.1:1.7.3

升级方式: 从上面的 maven 目录下载新 jar 放进 libs/，同步改 build.gradle 里的文件名。
注意小地图对世界地图有最低版本要求（不匹配会在启动时报 "only supports ... or above"）。
不要改成远程 maven 依赖——那个仓库很慢，会让每次编译多等好几分钟。
