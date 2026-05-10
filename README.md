# FlowStudio

FlowStudio is a classroom-ready AI video editing demo: a native HTML/CSS/JS frontend, a Java Spring Boot orchestration backend, and an optional AutoDL FastAPI wrapper around `edit.py`.

## Frontend

Open `index.html` directly for visual inspection, or run it through the Java backend for real task submission.

The studio supports:

- MP4/MOV/WebM video selection and local preview.
- Optional source prompt and required target prompt.
- PNG/JPG mask upload.
- Rectangle mask drawing on the first video frame.
- Multipart submission to `POST /api/tasks/edit`.
- Task polling, result video preview, and result download.

## Java Backend

The backend lives in `backend/` and uses Spring Boot 3 + Java 17.

Run in mock mode for classroom rehearsal:

```bash
cd backend
mvn spring-boot:run
```

Mock mode is enabled by default. It copies the input video to `runtime/tasks/{taskId}/result.mp4`, so the full frontend -> Java -> result display flow works without AutoDL.

Run with AutoDL:

```bash
cd backend
FLOWSTUDIO_MOCK_RUNNER=false AUTODL_BASE_URL=http://YOUR_AUTODL_HOST:8000 mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

For the GitHub Pages frontend, set the backend URL in the browser console before submitting a task:

```js
localStorage.setItem("FLOWSTUDIO_API_BASE", "https://YOUR_JAVA_BACKEND_DOMAIN")
location.reload()
```

Note: this machine currently does not have `mvn` installed. Install Maven or import `backend/pom.xml` in an IDE to run the backend.

## AutoDL Runner

Copy `autodl/flowstudio_autodl_api.py` and `autodl/requirements.txt` next to your AutoDL `edit.py`.

Install and start:

```bash
pip install -r requirements.txt
python flowstudio_autodl_api.py --host 0.0.0.0 --port 8000
```

The wrapper expects `edit.py` to accept:

```bash
python edit.py --video input.mp4 --mask mask.png --target_prompt "..." --source_prompt "..." --output result.mp4
```

If your real `edit.py` uses different parameter names, only update `autodl/flowstudio_autodl_api.py`; the Java and frontend contract can stay unchanged.

## API Contract

- `POST /api/tasks/edit`
  - multipart fields: `projectName?`, `sourcePrompt?`, `targetPrompt`, `video`, `mask`
  - returns task detail with `taskId` and `status`
- `GET /api/tasks/{taskId}`
  - returns project info, prompts, status, result URL, and error message
- `GET /api/tasks/{taskId}/status`
  - same shape as task detail
- `GET /api/tasks/{taskId}/result`
  - returns `{ taskId, resultUrl }` when ready
- `GET /api/health`
  - returns backend status and AutoDL configuration state

## Demo Script

1. Start the backend in mock mode.
2. Open `http://localhost:8080`.
3. Upload a short MP4.
4. Enter a target prompt.
5. Draw a rectangle mask on the first frame.
6. Click `Create Task`.
7. Watch the status move through `PENDING/RUNNING/SUCCESS`.
8. Play or download the result video.
