const taskButton = document.querySelector(".control-panel .button-primary");
const statusLine = document.querySelector(".task-card dd");

if (taskButton && statusLine) {
  taskButton.addEventListener("click", () => {
    statusLine.innerHTML = '<span class="status-dot"></span>Queued';
    taskButton.textContent = "Task Queued";
  });
}
