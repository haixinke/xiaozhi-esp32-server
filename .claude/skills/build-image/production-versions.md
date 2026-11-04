# 生产环境镜像版本记录
#
# 由 build-image skill 维护：每次镜像构建 + 推送成功后，把对应模块的版本更新到这里。
# 手动修改也可以，下次打镜像时读到的就是这里的值。
#
# 格式：模块key: 完整镜像tag（标签原样推送到 ACR，含 egg- 前缀）

manager-api: egg-4.8
manager-web: egg-2.8
xiaozhi-server: egg-1.9
