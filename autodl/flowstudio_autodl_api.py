import argparse
import shutil
import subprocess
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import FileResponse


app = FastAPI(title="FlowStudio AutoDL Runner")
BASE_DIR = Path(__file__).resolve().parent
TASK_DIR = BASE_DIR / "runtime" / "tasks"
EDIT_SCRIPT = BASE_DIR / "edit.py"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/edit")
async def edit(
    taskId: str = Form(...),
    targetPrompt: str = Form(...),
    sourcePrompt: str = Form(""),
    video: UploadFile = File(...),
    mask: UploadFile = File(...),
):
    task_dir = TASK_DIR / safe_name(taskId)
    task_dir.mkdir(parents=True, exist_ok=True)
    input_video = task_dir / "input.mp4"
    input_mask = task_dir / "mask.png"
    output_video = task_dir / "result.mp4"

    await save_upload(video, input_video)
    await save_upload(mask, input_mask)

    if not EDIT_SCRIPT.exists():
      return {
          "success": False,
          "message": f"edit.py not found at {EDIT_SCRIPT}. Place this wrapper next to edit.py.",
      }

    command = [
        "python",
        str(EDIT_SCRIPT),
        "--video",
        str(input_video),
        "--mask",
        str(input_mask),
        "--target_prompt",
        targetPrompt,
        "--source_prompt",
        sourcePrompt or "",
        "--output",
        str(output_video),
    ]

    try:
        completed = subprocess.run(
            command,
            cwd=BASE_DIR,
            text=True,
            capture_output=True,
            timeout=60 * 60,
            check=False,
        )
    except subprocess.TimeoutExpired:
        return {"success": False, "message": "edit.py timed out."}

    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "edit.py failed."
        return {"success": False, "message": message[-1200:]}

    if not output_video.exists():
        return {
            "success": False,
            "message": "edit.py completed but result.mp4 was not created.",
        }

    return {
        "success": True,
        "resultPath": f"/files/{safe_name(taskId)}/result.mp4",
        "message": "edit success",
    }


@app.get("/files/{task_id}/{file_name}")
def files(task_id: str, file_name: str):
    file_path = (TASK_DIR / safe_name(task_id) / safe_name(file_name)).resolve()
    if not str(file_path).startswith(str(TASK_DIR.resolve())):
        return {"success": False, "message": "Invalid file path."}
    return FileResponse(file_path)


async def save_upload(upload: UploadFile, path: Path):
    with path.open("wb") as handle:
        shutil.copyfileobj(upload.file, handle)


def safe_name(value: Optional[str]) -> str:
    keep = []
    for char in value or "":
        keep.append(char if char.isalnum() or char in "._-" else "_")
    return "".join(keep) or "unnamed"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", default=8000, type=int)
    args = parser.parse_args()

    import uvicorn

    uvicorn.run(app, host=args.host, port=args.port)
