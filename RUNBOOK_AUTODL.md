# FlowStudio AutoDL 联调复现手册

这份手册记录真实 AutoDL pipeline 的运行路径。命令会明确标注在哪里执行，避免混淆本地 Java 后端和 AutoDL VS Code 远程环境。

## 1. 整体链路

```text
Mac 本地浏览器
→ Mac 本地 Java Spring Boot 后端
→ VS Code 端口转发 localhost:8000
→ AutoDL FastAPI wrapper
→ AutoDL edit.py / FlowAnchor 模型
→ AutoDL result.mp4
→ Java 下载结果
→ 前端播放结果
```

## 2. AutoDL VS Code 终端：启动 FastAPI wrapper

在 AutoDL VS Code 远程窗口中打开终端，进入 FlowAnchor 目录：

```bash
cd /root/autodl-tmp/FlowAnchor
```

确保 `flowstudio_autodl_api.py` 和 `requirements.txt` 与 `edit.py` 在同一目录。

安装依赖：

```bash
pip install -r requirements.txt
```

启动 AutoDL wrapper：

```bash
python flowstudio_autodl_api.py --host 0.0.0.0 --port 8000
```

成功标志：

```text
Uvicorn running on http://0.0.0.0:8000
```

这个终端不要关闭。

## 3. AutoDL VS Code：设置端口转发

在 VS Code 底部打开 `端口` 面板，确认有：

```text
8000 -> localhost:8000
```

如果没有，点击添加端口，输入：

```text
8000
```

注意：AutoDL 终端里的 `http://0.0.0.0:8000` 不是本地浏览器使用的地址。本地 Java 使用的是 VS Code 转发出来的：

```text
http://localhost:8000
```

## 4. Mac 本地终端：验证 AutoDL wrapper

在 Mac 本地终端执行，不是在 AutoDL 终端执行：

```bash
curl http://localhost:8000/health
```

成功返回：

```json
{"status":"ok"}
```

## 5. Mac 本地终端：启动 Java 真实模式

进入本地后端目录：

```bash
cd /Users/chenze/Desktop/FlowStudio/backend
```

如果 8080 或 8090 被 VS Code / 其他程序占用，可以使用 18080：

```bash
PORT=18080 FLOWSTUDIO_MOCK_RUNNER=false AUTODL_BASE_URL=http://localhost:8000 mvn spring-boot:run
```

成功标志：

```text
Tomcat started on port 18080
```

验证 Java 配置：

```bash
curl http://localhost:18080/api/health
```

期望看到：

```json
{
  "status": "ok",
  "mockRunner": false,
  "autodlBaseUrl": "http://localhost:8000",
  "autodlConfigured": true
}
```

## 6. Mac 本地浏览器：提交任务

打开：

```text
http://localhost:18080
```

填写示例：

```text
Project Name:
Project_Flower

Source Prompt:
A blue flower is blooming in a garden surrounded by many other blue flowers, its petals glowing softly in sunlight against a blurred green background.

Target Prompt:
A red rose is blooming in a garden surrounded by many other blue flowers, its petals glowing softly in sunlight against a blurred green background.

Target Word:
rose
```

然后：

1. 上传短视频。
2. 在第一帧上框选 mask。
3. 点击 `Create Task`。

Java 终端应该出现：

```text
[FlowStudio] Task task-xxxx started runner.
[FlowStudio] Calling AutoDL: http://localhost:8000/edit
```

AutoDL 侧进入真实模型后，`nvitop` 可以看到显存占用上升。

## 7. 当前 mask 策略

真实 FlowAnchor `edit.py` 需要的是逐帧 mask 目录：

```text
mask_frames/
  00000.png
  00001.png
  ...
```

但前端目前只画第一帧 mask。当前第一阶段策略是：

```text
AutoDL wrapper 读取视频帧数
→ 将第一帧 mask resize 到视频尺寸
→ 复制成每一帧的 mask
→ 调用 edit.py
```

后续计划：

```text
用 VACE / 光流传播替代静态复制
```

这个改动应放在 AutoDL wrapper 内部，不改前端和 Java API。

## 8. 常见问题

### 8.1 Java 启动失败：Port 8080 was already in use

在 Mac 本地终端查看：

```bash
lsof -i :8080
```

如果是 VS Code 插件或其他程序占用，可以换端口启动 Java：

```bash
PORT=18080 FLOWSTUDIO_MOCK_RUNNER=false AUTODL_BASE_URL=http://localhost:8000 mvn spring-boot:run
```

### 8.2 AutoDL 没有出现 POST /edit

先看 Java 终端有没有：

```text
[FlowStudio] Calling AutoDL: http://localhost:8000/edit
```

如果没有，说明任务没进入 AutoDL runner，检查：

```bash
curl http://localhost:18080/api/health
```

确保：

```json
"mockRunner": false
```

如果 Java 打印了 Calling AutoDL，但 AutoDL 没日志，检查端口转发：

```bash
curl http://localhost:8000/health
```

### 8.3 AutoDL 返回 422 Unprocessable Entity

这表示请求到达 AutoDL，但 FastAPI 没解析到 multipart 字段。曾经出现过该问题，后来 Java 侧改为标准 multipart 构造方式。

需要确认 Java 已重启到最新代码，并且重新提交任务。

### 8.4 GPU 没动

如果 AutoDL 没有进入 `edit.py`，GPU 不会动。先确认：

```text
Java 是否打印 Calling AutoDL
AutoDL 是否收到 POST /edit
AutoDL runtime/tasks 下是否生成 input.mp4、first_frame_mask.png、mask_frames
```

在 AutoDL VS Code 新终端中可以查看：

```bash
find /root/autodl-tmp/FlowAnchor/runtime/tasks -maxdepth 3 -type f | tail -30
```

如果 `nvitop` 出现显存占用，说明已经进入真实模型推理阶段。

## 9. 已验证成功的 baseline

当前已完成一次真实链路成功验证。

成功任务：

```text
task-a0cad7de
```

成功现象：

```text
前端 TASK STATUS = SUCCESS
Message = edit success; prepared 81 mask frames
页面可播放结果视频
页面提供 Download Result
```

关键日志：

```text
[FlowStudio] Task task-a0cad7de started runner.
[FlowStudio] Calling AutoDL: http://127.0.0.1:8000/edit
[FlowStudio] AutoDL multipart bytes: 227711
[FlowStudio AutoDL] /edit received taskId=task-a0cad7de, targetWord=rose
```

这个 baseline 的 mask 策略是：

```text
第一帧 mask 静态复制到全部 81 帧
```

下一阶段计划：

```text
将静态复制替换为 VACE / 光流传播
```
