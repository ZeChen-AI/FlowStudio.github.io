# FlowStudio 迭代过程记录

## 1. 项目目标

FlowStudio 的目标是做一个课堂可演示的交互式视频编辑系统。用户不需要直接运行 Python 脚本，而是在网页里完成视频上传、prompt 输入、mask 框选，然后由 Java 后端统一接收请求、管理任务状态，并调用 Python/AutoDL 上的视频编辑模型生成结果。

最终希望讲清楚的主线是：

```text
前端交互
→ Java Spring Boot 任务编排
→ AutoDL Python 编辑模型
→ 结果视频预览与下载
```

## 2. 早期方案：直接使用 HPC

项目一开始考虑过使用 HPC 环境运行模型，因为 HPC 通常有较强 GPU 资源，适合跑大模型或视频编辑算法。

但在实际推进时，HPC 对这个课堂项目不太适合作为主流程，主要原因是：

- 使用门槛较高：通常需要登录节点、提交任务、等待调度，不适合网页实时交互演示。
- 文件流转复杂：前端上传的视频、mask、输出结果需要在本地、后端、HPC 文件系统之间多次传递。
- 任务反馈不友好：HPC 更适合批处理，不容易给前端实时返回 `PENDING / RUNNING / SUCCESS / FAILED` 这种状态。
- 演示不稳定：课堂展示时如果排队、环境变量、模型路径或作业调度出问题，排查成本较高。
- Java 调用链路不清晰：如果 Java 后端直接对接 HPC 作业系统，业务逻辑会被调度细节拖复杂，偏离项目“Java 主框架编排”的重点。

因此，我们没有把 HPC 作为首版闭环的核心方案，而是把目标收敛到更可控的在线演示链路。

## 3. 方案收敛：Java + AutoDL + FastAPI

经过对比后，项目选择了：

```text
GitHub Pages 前端
Java Spring Boot 后端
AutoDL Python/FastAPI 模型服务
```

选择这个方案的原因：

- GitHub Pages 适合展示前端页面，部署简单，访问方便。
- Java Spring Boot 能体现课程项目中 Java 的主框架作用，负责接口、文件、任务状态和模块编排。
- AutoDL 更适合放置完整模型、`edit.py`、pipeline 和依赖环境，避免把大模型迁移到本地。
- FastAPI wrapper 可以把原本命令行运行的 `edit.py` 包装成 HTTP 服务，让 Java 后端更容易调用。
- 这个架构更容易解释，也更适合课堂验收：前端可见、Java 可讲、Python 模型可替换。

## 4. 第一轮：前端从展示页变成可交互工作台

早期前端更像一个高质量视觉展示页，包含 hero、pipeline 展示和结果占位，但还不能真正完成视频编辑任务。

本轮将前端改成了真实工作台：

- 支持上传视频文件。
- 显示视频文件名、大小、格式。
- 支持填写 `source prompt` 和 `target prompt`。
- 从视频中截取一帧作为 mask 绘制画布。
- 支持在画布上拖拽矩形框生成黑白 mask。
- 支持上传外部 mask 图片。
- 点击 `Create Task` 后提交 multipart 表单到 Java 后端。
- 前端轮询任务状态，并在成功后显示结果视频和下载入口。

过程中发现并修复了一个前端问题：最初右侧预览区域叠加了 video 控件，导致看到的是视频预览而不是稳定第一帧；同时部分视频不是 16:9，画布比例会错位。后来改为按视频真实宽高设置 canvas，并默认截取 0.1 秒附近画面，避免开头黑帧。

## 5. 第二轮：新增 Java Spring Boot 后端

为了让系统不只是静态页面，我们新增了 `backend/` Spring Boot 工程。

后端目前完成：

- `POST /api/tasks/edit`：接收视频、mask、prompt 和项目名。
- 保存上传文件到 `runtime/tasks/{taskId}`。
- 使用内存 Map 管理任务，不引入数据库。
- 维护任务状态：`PENDING / RUNNING / SUCCESS / FAILED`。
- 提供任务查询、状态查询、结果查询和文件访问接口。
- 默认启用 mock runner：把输入视频复制为结果视频，用于验证完整链路。
- 支持后续通过环境变量切换到真实 AutoDL runner。

mock runner 的价值是先验证：

```text
前端上传
→ Java 接收
→ Java 保存文件
→ Java 更新状态
→ 前端展示结果
```

这样即使模型还没接上，也能证明系统主流程已经成立。

## 6. 第三轮：AutoDL FastAPI wrapper

为了让 Java 能调用 AutoDL 上的模型，新增了 `autodl/flowstudio_autodl_api.py`。

它的作用是把 AutoDL 上的 `edit.py` 包成 HTTP API：

- `/health`：检查服务是否在线。
- `/edit`：接收 Java 传来的视频、mask、prompt。
- 保存输入文件。
- 调用命令行形式的 `edit.py`。
- 返回 `success`、`resultPath`、`message`。
- 提供结果文件下载接口。

当前约定的 `edit.py` 调用形式是：

```bash
python edit.py \
  --video input.mp4 \
  --mask mask.png \
  --target_prompt "..." \
  --source_prompt "..." \
  --output result.mp4
```

如果真实 `edit.py` 参数不同，只需要修改 FastAPI wrapper 的 command 部分，不需要改前端和 Java 接口。

## 7. 当前验证结果

目前已经完成本地 mock 闭环验证。

已验证成功：

- Maven 安装完成。
- Java Spring Boot 后端成功启动。
- 浏览器访问 `http://localhost:8080` 成功。
- 前端可以上传视频。
- 前端可以绘制 mask。
- 前端可以提交任务。
- Java 后端可以创建任务并保存文件。
- mock runner 可以生成结果。
- 前端显示 `Task completed. Result video is ready.`

这说明当前已经跑通：

```text
前端
→ Java 后端
→ 文件保存
→ 任务状态更新
→ 结果视频返回
```

## 8. 下一步计划

下一步重点是接入真实 AutoDL 模型链路。

计划如下：

1. 将 `flowstudio_autodl_api.py` 和 `requirements.txt` 放到 AutoDL 的 `edit.py` 同级目录。
2. 在 AutoDL 上安装依赖并启动 FastAPI 服务。
3. 确认 AutoDL 的端口可以被 Java 后端访问。
4. 停止 Java mock 模式。
5. 使用真实模式启动 Java：

```bash
FLOWSTUDIO_MOCK_RUNNER=false \
AUTODL_BASE_URL=http://AutoDL地址:8000 \
mvn spring-boot:run
```

6. 从前端提交任务，观察 AutoDL 是否调用 `edit.py`。
7. 检查 Java 是否保存真实结果视频，前端是否能播放。

当前 AutoDL 接入分两步推进：

- 第一步先跑通真实 `edit.py`：前端只上传第一帧 mask，AutoDL wrapper 暂时将这张 mask 复制成与视频帧数一致的逐帧 mask 目录。
- 第二步再加入 VACE / 光流传播：用第一帧 mask 自动传播到后续帧，替换掉第一步中的静态复制逻辑。

这样做的原因是先验证 Java 到 AutoDL 的接口、文件传输和参数映射，再处理更复杂的视频 mask 传播问题，避免一次性引入太多不确定性。

## 9. 汇报时可以强调的设计取舍

本项目不是直接追求复杂平台化，而是优先保证课堂项目可落地、可展示、可解释。

关键取舍：

- 不做登录、用户权限和数据库，降低实现成本。
- 不把 HPC 作为首版主链路，避免调度和文件系统复杂度影响演示。
- 用 Java 作为主框架，体现课程重点。
- 用 AutoDL 放置模型和 pipeline，保持 AI 模块独立。
- 用 FastAPI 包装 `edit.py`，让 Java 与 Python 解耦。
- 先用 mock runner 验证系统链路，再切换真实模型，降低联调风险。

最终形成的故事是：

```text
我们先评估 HPC，但它更适合离线批处理，不适合课堂实时交互；
因此我们选择 GitHub Pages + Java Spring Boot + AutoDL FastAPI；
先完成前端交互和 Java mock 闭环，再接真实 Python 视频编辑模型；
这样既能保证演示稳定，也能清楚体现 Java 在系统编排中的作用。
```
