# KITT 办公室 / 家庭电脑统一签名（V0.3.5.2 前置门禁）

**2026-10-13：办公室已确认原密钥并原样复制到专用路径；实际构建与证书结果见 [HANDOFF.md](../HANDOFF.md)。家庭端 PENDING。** [#19](https://github.com/shimao1115/KITT/issues/19) 保持 open，交付办公室结果后停止，等待本人安全传输及家庭端验证；不启动 [#18](https://github.com/shimao1115/KITT/issues/18)。

## 哪把证书是标准

当前手机原位升级并保留 ChatGPT 设置的应用是：

- `applicationId = com.kitt.reader.runtimecheck`
- 当前已安装：0.3.5.1，`versionCode = 11`
- 可信已安装签名证书的 SHA-256（不含冒号、小写）：
  `3139a293aaf9a233accba2489f10bb59c8f79102b631abfd51a7ec7f929502ba`

这个值记录在 [HANDOFF.md](../HANDOFF.md) 和 [V0.3.5.1 验收](ACCEPTANCE_AREA_STABILITY.md)。优先**复制原密钥**，不要生成新证书。SHA-256 指纹只用于比对，无法倒推出签名私钥。

## 密钥统一位置（各 PC 的用户目录内）

`%USERPROFILE%\.kitt\signing\acceptance.keystore`

这不是 Git 仓库目录。目录内的 keystore 是 **私钥文件**，严禁上传 GitHub、Issue、聊天、公共网盘、CI日志，或打入APK。建议通过本人控制的加密U盘或其他安全私密渠道转移，在两个设备各备份一份加密副本并限制文件访问权限。不删除或覆盖已有手机应用，不替换全局 `%USERPROFILE%\.android\debug.keystore`，因为可能影响别的 Android 项目。

### 办公室电脑：确定源文件

旧脚本的默认验收密钥候选是：

`%USERPROFILE%\.android\debug.keystore`

先在 PowerShell 中执行（需要 JDK17，JAVA_HOME 已配置）：

```powershell
$source = Join-Path $env:USERPROFILE '.android\debug.keystore'
& (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -list -v -keystore $source -alias androiddebugkey
```

按提示输入该 keystore 的密码（Android 默认 Debug keystore 通常是 `android`）；检查输出的 SHA256 是否为：

`31:39:A2:93:AA:F9:A2:33:AC:CB:A2:48:9F:10:BB:59:C8:F7:91:02:B6:31:AB:FD:51:A7:EC:7F:92:95:02:BA`

如果不同，**停止**，不要复制错误的源密钥，也不要覆盖新手机上的 App；需要找到原来用于给 0.3.5.1 `runtimecheck` 签名的实际私钥文件。别把该文件上传给 ChatGPT 来查。

确认相同后，在办公室本机创建私有 KITT 路径，并把**同一原密钥文件原样复制**进去：

```powershell
$target = Join-Path $env:USERPROFILE '.kitt\signing\acceptance.keystore'
New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
if (Test-Path -LiteralPath $target) {
    if ((Get-FileHash $source).Hash -ne (Get-FileHash $target).Hash) {
        throw '目标已有不同文件，保留两者，不覆盖；先检查原密钥。'
    }
} else { Copy-Item -LiteralPath $source -Destination $target }
```

办公室已经准备好的迁移源就是 `%USERPROFILE%\.kitt\signing\acceptance.keystore`，与原全局密钥字节相同；专用目录/文件已限制为当前用户和 SYSTEM 访问。保留原文件。本人把此完整文件复制到加密 U 盘等安全介质；不要通过 GitHub、聊天或构建日志传输。家庭电脑不应覆盖它自己的 `~/.android/debug.keystore`。

### 家庭电脑最短步骤（PENDING，尚未执行）

1. 在家庭仓库 `git pull --ff-only origin main`，确保 JDK17、Android SDK35 和依赖可用。
2. 接入本人控制的安全介质，在仓库根 PowerShell 执行以下命令。`X:\acceptance.keystore` 是示例，改为实际介质路径。先验源文件，再复制；存在不同目标文件时保留双方并停止。

```powershell
$source = 'X:\acceptance.keystore'
.\scripts\check-kitt-signing.ps1 -KeystorePath $source
if (-not $?) { throw '源密钥验证失败，不复制。' }
$target = Join-Path $env:USERPROFILE '.kitt\signing\acceptance.keystore'
New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
if (Test-Path -LiteralPath $target) {
    if ((Get-FileHash $source).Hash -ne (Get-FileHash $target).Hash) { throw '目标不同，保留原文件，不覆盖。' }
} else { Copy-Item -LiteralPath $source -Destination $target }
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls.exe (Split-Path $target) /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F'
if ($LASTEXITCODE -ne 0) { throw '目录限权失败。' }
icacls.exe $target /inheritance:r /grant:r "${identity}:F" '*S-1-5-18:F'
if ($LASTEXITCODE -ne 0) { throw '文件限权失败。' }
.\scripts\check-kitt-signing.ps1
```

3. 构建并验签（依赖齐全可在构建命令后加 `-Offline`）：

```powershell
.\scripts\build-runtime-smoke.ps1
.\scripts\check-kitt-signing.ps1 -ApkPath artifacts\kitt-v0.3.5.1-runtimecheck.apk -ExpectedApplicationId com.kitt.reader.runtimecheck
.\scripts\verify.ps1
.\scripts\test-kitt-signing.ps1 -RuntimeApkPath artifacts\kitt-v0.3.5.1-runtimecheck.apk
```

第一项内部已校验证书和包名；`verify.ps1` 另验证标准 Debug/Release 同证书，最后运行签名负例回归。只分享 PASS/FAIL、公开指纹和包名/版本结果，私钥文件及密码不要分享。

4. 此次仍是 **0.3.5.1/code11**，仅构建验签，**不安装到手机**。家庭端结果实际取得后再更新 #19；后续正式升版才做原位升级，不为验签修改版本号。

### 两台电脑均校验

完成前置施工脚本交付后，可在仓库根目录运行：

```powershell
.\scripts\check-kitt-signing.ps1
```

脚本只会在证书 SHA256 等于上方标准时返回 PASS。构建包后可用：

```powershell
.\scripts\check-kitt-signing.ps1 -ApkPath 'artifacts\kitt-v0.3.5.1-runtimecheck.apk' -ExpectedApplicationId 'com.kitt.reader.runtimecheck'
```

脚本同时校验私钥条目、单一 APK signer 与包名，并显示版本元数据。文件不存在、签名无效或包名不符会失败。Gradle 在专用文件存在时也独立校验证书及私钥条目，不能将错误密钥静默当验收签名。缺少专用文件时普通本地单测仍可运行，但两个验收构建脚本会提前失败。

## 覆盖升级的三个必要条件

1. **相同 applicationId：** 手机装的是 `com.kitt.reader.runtimecheck`。默认 `com.kitt.reader` 的 Debug/Release 是另一个应用，即便签名相同也不是覆盖安装。
2. **相同签名证书/私钥：** 上述 SHA256 必须一致。仅签名工具名字或 alias 一样不代表签名一致。
3. **合法版本递增：** 新 APK 的 `versionCode` 应高于手机现有的11，不能用旧版本反向覆盖。

满足后使用 `adb install -r <APK完整路径>` 原位升级；不要带 `-d` 迫使旧版降级，也不要先 `adb uninstall` 或清除应用数据。成功后才检查原 ChatGPT 登录与设置是否保留（账号网络授权是否仍有效须另验）。

办公室与家里生成 APK 的**文件 SHA256 不必相同**，签名证书SHA256和包名/版本条件满足即可。

## 风险与止损

- 没拿到办公室那把真正的旧密钥，就不可能凭证书指纹重新生成它；应停止原位覆盖实验，不要生成“新的统一密钥”冒充原密钥。
- 原私钥丢失/损坏时，Android 不允许新证书覆盖已安装同包名应用并保留本地数据；这属于高风险迁移，必须先讨论备份/账户恢复，而非直接卸载。
- 开发 Agent 可以先实现项目签名配置、预检、打包规则，无需拿私钥；但**两个实际 PC 的证书匹配与手机升级**必须有真实结果才能标记 #19 PASS。
- 本轮不进行 V0.3.5.2 研究缓存业务施工，只处理工具链与安全迁移。
