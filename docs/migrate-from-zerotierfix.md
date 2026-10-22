# 从 ZerotierFix 迁移身份

Zotiera 的包名是 `net.sumitm.zotiera`，与原版 ZerotierFix（`net.kaaass.zerotierfix`）是两个应用。
Android 11 起不允许一个应用读取另一个应用的私有目录，所以两者的数据不会自动接续，
需要手动把身份文件搬过来。

搬过去的目的是**保留同一个节点 ID**。节点身份决定节点 ID；换了身份就是换了一个节点，
原来在控制器里做的授权不会跟过来，管理员得重新批准一次。

## 需要准备什么

- 原版 ZerotierFix 还在手机上，且**至少成功连上过一次网络**——没有连过就不会生成密钥。
- 手机已 root（Magisk / KernelSU 均可），能拿到 `su`。
- 一台电脑，装了 `adb`；或者手机上有能打包 zip 的文件管理器。

## 一、确认原应用里确实有密钥

```sh
adb shell
su
ls -l /data/data/net.kaaass.zerotierfix/files/
```

应该能看到两个文件：

```
identity.public     # 141 字节左右，节点 ID + 公钥，可以公开
identity.secret     # 私钥（同时含公钥），不能外传
```

只有 `identity.public` 而没有 `identity.secret`，说明身份还没生成，先去原应用里连一次网络。
两个文件都看不到，多半是原应用已经被卸载——卸载会连同数据一起删掉，那就没有可搬的东西了，
只能接受一个新节点 ID。

## 二、把两个文件拷出来

```sh
su
cp /data/data/net.kaaass.zerotierfix/files/identity.secret /sdcard/Download/
cp /data/data/net.kaaass.zerotierfix/files/identity.public /sdcard/Download/
exit
exit
adb pull /sdcard/Download/identity.secret .
adb pull /sdcard/Download/identity.public .
```

## 三、打成 Zotiera 认的 zip

Zotiera 只按文件名取这两个条目，路径无所谓，但名字必须一模一样：

```
identity.zip
├── identity.secret
├── identity.public
└── README.txt        （可选，随便写，不会被读取）
```

电脑上：

```sh
zip identity.zip identity.secret identity.public
```

手机上用文件管理器选中这两个文件、压缩成 `identity.zip` 也一样。

检查一下压缩包内容对不对：

```sh
unzip -l identity.zip
```

## 四、导入 Zotiera

1. 打开 Zotiera → **设置** → 关掉「简易模式」（简易模式下看不到身份入口）。
2. 进 **管理身份** → 右下角 **添加身份** → **从文件导入**。
3. 选刚才那个 `identity.zip`。
4. 列表里会多出一条，节点 ID 应该和原 ZerotierFix 里显示的一致——这一步就是校验，
   两边对不上说明搬错了文件（比如拿到的是另一个应用的旧数据）。

原 ZerotierFix 显示的节点 ID 是 10 位十六进制，Zotiera 显示同样的 10 位，
可以逐位对一下。

## 五、把网络加回来

网络列表不跟着身份走，需要重新添加：

1. 概览页 → **添加网络** → 填 16 位网络 ID。
2. 控制器认的是节点 ID，所以身份对了，之前批准过的网络会直接变成 **工作中**，
   不需要管理员重新授权。

如果显示 **访问被拒**（红色图标），说明控制器里这个节点的授权已经不在了
（比如管理员清过成员列表），需要请他再批准一次。

## 六、原应用可以删了吗

确认新应用能正常连上之后再删。删掉原应用不影响已经导入的身份——
密钥已经复制到 Zotiera 自己的目录里了。

反过来，如果以后想搬回去或者搬到别的设备：Zotiera 里 **管理身份** → 长按那条身份 →
**导出身份**，得到的还是同样结构的 zip，可以在另一台设备的 Zotiera 上导入。

## 常见问题

**导入时提示「不是有效的身份导出」**
zip 里的文件名不是 `identity.secret` / `identity.public`。有的文件管理器压缩时会带上目录层级
或者用 `identity (1).secret` 这种重名后缀，重新打包成这两个平铺的文件即可。

**导入时提示「这个身份已经添加过」**
同一个节点 ID 已经在列表里了。不需要重复导入，直接用那条就行。

**导入成功但连不上，状态一直「连接中」**
身份对了但控制器没有回应。先确认原应用里这个网络当时是能连的，再检查手机的网络
和「允许移动网络」开关。

**没有 root**
只能在本机用 adb 读取应用私有目录，而这对非 debuggable 的正式包不成立；
ZerotierFix 也没有导出身份的功能。没有 root 就等于无法搬移身份，只能接受新节点 ID。
如果只是想让新节点能用，直接在 Zotiera 里加网络、请管理员批准新节点更省事。
