const API_BASE =
  window.FLOWSTUDIO_API_BASE ||
  localStorage.getItem("FLOWSTUDIO_API_BASE") ||
  "";
const POLL_INTERVAL_MS = 1800;

const form = document.querySelector("#task-form");
const projectNameInput = document.querySelector("#project-name");
const videoInput = document.querySelector("#video-input");
const maskInput = document.querySelector("#mask-input");
const sourcePromptInput = document.querySelector("#source-prompt");
const targetPromptInput = document.querySelector("#target-prompt");
const submitButton = document.querySelector("#submit-task");
const clearMaskButton = document.querySelector("#clear-mask");
const formMessage = document.querySelector("#form-message");
const videoMeta = document.querySelector("#video-meta");
const videoPreview = document.querySelector("#video-preview");
const frameCanvas = document.querySelector("#frame-canvas");
const placeholder = document.querySelector("#canvas-placeholder");
const maskPreview = document.querySelector("#mask-preview");
const resultVideo = document.querySelector("#result-video");
const resultPlaceholder = document.querySelector("#result-placeholder");
const downloadLink = document.querySelector("#download-link");

const taskStatus = document.querySelector("#task-status");
const taskIdText = document.querySelector("#task-id");
const taskProject = document.querySelector("#task-project");
const taskPrompt = document.querySelector("#task-prompt");
const taskMessage = document.querySelector("#task-message");

const ctx = frameCanvas.getContext("2d");
let videoObjectUrl = "";
let maskObjectUrl = "";
let currentMaskBlob = null;
let currentMaskSource = "";
let baseFrame = null;
let dragStart = null;
let activeTaskId = "";
let pollTimer = 0;

function setMessage(message, tone = "neutral") {
  formMessage.textContent = message;
  formMessage.dataset.tone = tone;
}

function setStatus(status, message = "") {
  taskStatus.innerHTML = `<span class="status-dot status-${status.toLowerCase()}"></span>${status}`;
  taskMessage.textContent = message || status;
}

function formatBytes(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function resetResult() {
  resultVideo.removeAttribute("src");
  resultVideo.load();
  resultVideo.hidden = true;
  resultPlaceholder.hidden = false;
  downloadLink.hidden = true;
  downloadLink.removeAttribute("href");
}

function clearPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = 0;
  }
}

function drawBaseFrame() {
  if (!baseFrame) return;
  frameCanvas.width = baseFrame.width;
  frameCanvas.height = baseFrame.height;
  ctx.drawImage(baseFrame, 0, 0);
}

function drawSelection(rect) {
  drawBaseFrame();
  if (!rect) return;
  ctx.fillStyle = "rgba(131, 196, 156, 0.28)";
  ctx.strokeStyle = "rgba(244, 181, 91, 0.96)";
  ctx.lineWidth = Math.max(2, frameCanvas.width * 0.004);
  ctx.fillRect(rect.x, rect.y, rect.w, rect.h);
  ctx.strokeRect(rect.x, rect.y, rect.w, rect.h);
}

function getCanvasPoint(event) {
  const bounds = frameCanvas.getBoundingClientRect();
  const scaleX = frameCanvas.width / bounds.width;
  const scaleY = frameCanvas.height / bounds.height;
  return {
    x: (event.clientX - bounds.left) * scaleX,
    y: (event.clientY - bounds.top) * scaleY,
  };
}

function normalizeRect(start, end) {
  return {
    x: Math.max(0, Math.min(start.x, end.x)),
    y: Math.max(0, Math.min(start.y, end.y)),
    w: Math.abs(end.x - start.x),
    h: Math.abs(end.y - start.y),
  };
}

function updateMaskPreview(blob, source) {
  if (maskObjectUrl) URL.revokeObjectURL(maskObjectUrl);
  maskObjectUrl = URL.createObjectURL(blob);
  maskPreview.src = maskObjectUrl;
  maskPreview.hidden = false;
  currentMaskBlob = blob;
  currentMaskSource = source;
  setMessage(source === "drawn" ? "Mask generated from the selected rectangle." : "Mask file loaded.", "success");
}

function generateMask(rect) {
  if (rect.w < 8 || rect.h < 8) {
    setMessage("Please draw a larger mask rectangle.", "error");
    return;
  }

  const maskCanvas = document.createElement("canvas");
  maskCanvas.width = frameCanvas.width;
  maskCanvas.height = frameCanvas.height;
  const maskCtx = maskCanvas.getContext("2d");
  maskCtx.fillStyle = "#000";
  maskCtx.fillRect(0, 0, maskCanvas.width, maskCanvas.height);
  maskCtx.fillStyle = "#fff";
  maskCtx.fillRect(rect.x, rect.y, rect.w, rect.h);
  maskCanvas.toBlob((blob) => {
    if (blob) updateMaskPreview(blob, "drawn");
  }, "image/png");
}

function captureFirstFrame() {
  const width = videoPreview.videoWidth || 960;
  const height = videoPreview.videoHeight || 540;
  frameCanvas.width = width;
  frameCanvas.height = height;
  ctx.drawImage(videoPreview, 0, 0, width, height);

  baseFrame = new Image();
  baseFrame.onload = () => {
    placeholder.hidden = true;
    frameCanvas.hidden = false;
    drawBaseFrame();
  };
  baseFrame.src = frameCanvas.toDataURL("image/png");
}

videoInput?.addEventListener("change", () => {
  const file = videoInput.files?.[0];
  currentMaskBlob = null;
  currentMaskSource = "";
  maskInput.value = "";
  maskPreview.removeAttribute("src");
  maskPreview.hidden = true;
  baseFrame = null;
  ctx.clearRect(0, 0, frameCanvas.width, frameCanvas.height);
  frameCanvas.hidden = true;
  placeholder.hidden = false;

  if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl);
  resetResult();

  if (!file) {
    videoMeta.textContent = "No video selected";
    videoPreview.removeAttribute("src");
    videoPreview.load();
    return;
  }

  videoObjectUrl = URL.createObjectURL(file);
  videoPreview.src = videoObjectUrl;
  videoPreview.pause();
  videoPreview.currentTime = 0;
  videoMeta.textContent = `${file.name} · ${formatBytes(file.size)} · ${file.type || "video"}`;
  setMessage("Video loaded. Draw a rectangle on the first frame or upload a mask.", "success");
});

videoPreview?.addEventListener("loadeddata", () => {
  if (videoPreview.readyState >= 2) {
    captureFirstFrame();
  }
});

videoPreview?.addEventListener("seeked", () => {
  captureFirstFrame();
});

maskInput?.addEventListener("change", () => {
  const file = maskInput.files?.[0];
  if (!file) return;
  if (!["image/png", "image/jpeg"].includes(file.type)) {
    setMessage("Mask must be PNG or JPG.", "error");
    maskInput.value = "";
    return;
  }
  updateMaskPreview(file, "uploaded");
});

clearMaskButton?.addEventListener("click", () => {
  currentMaskBlob = null;
  currentMaskSource = "";
  maskInput.value = "";
  maskPreview.removeAttribute("src");
  maskPreview.hidden = true;
  drawBaseFrame();
  setMessage("Mask cleared. Draw a new rectangle or upload a mask file.");
});

frameCanvas?.addEventListener("pointerdown", (event) => {
  if (!baseFrame) return;
  frameCanvas.setPointerCapture(event.pointerId);
  dragStart = getCanvasPoint(event);
});

frameCanvas?.addEventListener("pointermove", (event) => {
  if (!dragStart) return;
  const rect = normalizeRect(dragStart, getCanvasPoint(event));
  drawSelection(rect);
});

frameCanvas?.addEventListener("pointerup", (event) => {
  if (!dragStart) return;
  const rect = normalizeRect(dragStart, getCanvasPoint(event));
  dragStart = null;
  drawSelection(rect);
  generateMask(rect);
});

async function fetchTask(taskId) {
  const response = await fetch(`${API_BASE}/api/tasks/${taskId}`);
  if (!response.ok) throw new Error("Unable to fetch task status.");
  return response.json();
}

function renderTask(task) {
  activeTaskId = task.taskId || activeTaskId;
  taskIdText.textContent = activeTaskId || "-";
  taskProject.textContent = task.projectName || projectNameInput.value || "-";
  taskPrompt.textContent = task.targetPrompt || targetPromptInput.value || "-";
  setStatus(task.status || "READY", task.errorMessage || task.message || "Task updated.");

  if (task.status === "SUCCESS" && task.resultUrl) {
    resultVideo.src = task.resultUrl;
    resultVideo.hidden = false;
    resultPlaceholder.hidden = true;
    downloadLink.href = task.resultUrl;
    downloadLink.hidden = false;
    clearPolling();
    submitButton.disabled = false;
    submitButton.textContent = "Create Task";
    setMessage("Task completed. Result video is ready.", "success");
  }

  if (task.status === "FAILED") {
    clearPolling();
    submitButton.disabled = false;
    submitButton.textContent = "Create Task";
    setMessage(task.errorMessage || "Task failed.", "error");
  }
}

function startPolling(taskId) {
  clearPolling();
  pollTimer = window.setInterval(async () => {
    try {
      const task = await fetchTask(taskId);
      renderTask(task);
    } catch (error) {
      setMessage(error.message, "error");
    }
  }, POLL_INTERVAL_MS);
}

form?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const video = videoInput.files?.[0];
  const targetPrompt = targetPromptInput.value.trim();

  if (!video) {
    setMessage("Please select an input video before creating a task.", "error");
    return;
  }
  if (!targetPrompt) {
    setMessage("Target prompt is required.", "error");
    return;
  }
  if (!currentMaskBlob) {
    setMessage("Please upload a mask or draw a rectangle on the frame.", "error");
    return;
  }

  const formData = new FormData();
  formData.append("projectName", projectNameInput.value.trim());
  formData.append("sourcePrompt", sourcePromptInput.value.trim());
  formData.append("targetPrompt", targetPrompt);
  formData.append("video", video, video.name);
  formData.append("mask", currentMaskBlob, currentMaskSource === "uploaded" ? "mask-upload.png" : "mask-bbox.png");

  submitButton.disabled = true;
  submitButton.textContent = "Submitting...";
  resetResult();
  setStatus("PENDING", "Uploading task files.");
  setMessage("Submitting task to Java backend...");

  try {
    const response = await fetch(`${API_BASE}/api/tasks/edit`, {
      method: "POST",
      body: formData,
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.errorMessage || data.message || "Task submission failed.");

    activeTaskId = data.taskId;
    renderTask(data);
    submitButton.textContent = "Task Running";
    setMessage("Task created. Polling Java backend for status.", "success");
    startPolling(data.taskId);
  } catch (error) {
    submitButton.disabled = false;
    submitButton.textContent = "Create Task";
    setStatus("FAILED", error.message);
    setMessage(error.message, "error");
  }
});

resetResult();
