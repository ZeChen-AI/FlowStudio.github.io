import argparse
import shutil
import subprocess
from pathlib import Path
from typing import Optional

import cv2
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
    targetWord: str = Form(...),
    sourcePrompt: str = Form(""),
    video: UploadFile = File(...),
    mask: UploadFile = File(...),
):
    task_dir = TASK_DIR / safe_name(taskId)
    task_dir.mkdir(parents=True, exist_ok=True)
    input_video = task_dir / "input.mp4"
    first_frame_mask = task_dir / "first_frame_mask.png"
    mask_dir = task_dir / "mask_frames"
    output_video = task_dir / "result.mp4"

    await save_upload(video, input_video)
    await save_upload(mask, first_frame_mask)

    if not EDIT_SCRIPT.exists():
        return {
            "success": False,
            "message": f"edit.py not found at {EDIT_SCRIPT}. Place this wrapper next to edit.py.",
        }

    try:
        frame_count = prepare_static_mask_sequence(input_video, first_frame_mask, mask_dir)
    except Exception as error:
        return {"success": False, "message": f"Failed to prepare mask sequence: {error}"}

    command = [
        "python",
        str(EDIT_SCRIPT),
        "--video_path",
        str(input_video),
        "--output_path",
        str(output_video),
        "--src_prompt",
        sourcePrompt or "",
        "--tar_prompt",
        targetPrompt,
        "--mask_path",
        str(mask_dir),
        "--target_word",
        targetWord,
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
        "message": f"edit success; prepared {frame_count} mask frames",
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


def prepare_static_mask_sequence(video_path: Path, mask_path: Path, output_dir: Path) -> int:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise ValueError(f"Could not open video: {video_path}")

    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    cap.release()

    if frame_count <= 0 or width <= 0 or height <= 0:
        raise ValueError("Video metadata is invalid.")

    mask = cv2.imread(str(mask_path), cv2.IMREAD_GRAYSCALE)
    if mask is None:
        raise ValueError(f"Could not read mask image: {mask_path}")

    mask = cv2.resize(mask, (width, height), interpolation=cv2.INTER_NEAREST)
    _, mask = cv2.threshold(mask, 127, 255, cv2.THRESH_BINARY)

    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    for index in range(frame_count):
        cv2.imwrite(str(output_dir / f"{index:05d}.png"), mask)

    return frame_count


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
