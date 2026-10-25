# AI Interior Design Factory

A multi-agent interior-design studio. You describe a room. Eleven agents then work it through together: requirements, room specification, layout, geometry, furniture, lighting, materials/BOQ, visuals, QA, and a **SketchUp Exporter** that produces a native `.skp`. A simple web UI shows the agents messaging each other and every task they perform, live.

- **Backend:** Java 21, Spring Boot 3.5, Spring AI 1.0 (Hugging Face adapter), Maven
- **Frontend:** React 19 + TypeScript + Vite
- **Storage:** none, by design. Sessions live in memory, and the only files written are exports.
- **Configuration:** everything is in `backend/src/main/resources/application.properties`

## Quick start

Prerequisites: JDK 21+, Maven 3.9+, Node 20+.

**Option A: one process (UI bundled into the jar)**

```bash
cd frontend && npm install && npm run build:embed     # builds the UI into backend/src/main/resources/static
cd ../backend && mvn spring-boot:run                    # http://127.0.0.1:8080
```

**Option B: development (hot reload)**

```bash
cd backend && mvn spring-boot:run                       # API on 127.0.0.1:8080
cd frontend && npm install && npm run dev               # UI on http://127.0.0.1:5173 (proxies /api)
```

Open the UI and click **Run the agents**. An example brief is preloaded. Watch the network graph and conversation as the agents work. When the Supervisor asks for your approval, choose **Approve & export**, **Request changes** (e.g. "remove the bookshelf, add a console table"), switch to the other layout, or **Reject**.

API docs: http://127.0.0.1:8080/swagger-ui.html

## The agents

| Agent | Does | Talks to |
|---|---|---|
| **Supervisor** | Fixed dependency graph, task status, bounded retries, human approval gate. No LLM decides the workflow. | everyone, you |
| **Requirements** | Required / preferred / optional / must-not-change, facts vs. assumptions, conflicts | AI provider |
| **Room Planning** | Room shell from your measurements; lists the *minimum* missing measurements and pauses instead of guessing | – |
| **Interior Design** | Concept + 2 layout alternatives, trade-offs; records structural ideas as proposals (never applied) | Furniture, Geometry, AI |
| **Furniture** | Real sizes, clearances, standard vs. custom pieces | – |
| **Geometry** | Canonical mm geometry; validates bounds, intersections, clearances, door swings, windows. Uses no AI. | – |
| **Lighting** | Lux-based ambient/task/accent plan, CCT, missing electrical/ceiling info | Geometry |
| **Materials & BOQ** | Material schedule from real quantities; indicative BOQ (material, labour, install, hardware, wastage, transport, contingency, tax) | Geometry, AI |
| **Rendering** | Camera plan, image prompt, 2D plan, concept board (MOCK labelled) | image provider |
| **Quality Assurance** | Machine-readable QA report; independently re-validates geometry; verifies every exported file | Geometry |
| **SketchUp Exporter** | Builds and saves a native `.skp` through SketchUp's Ruby API, then verifies it | Geometry, SketchUp |

Each agent declares the tools it may use (`AgentTool`), and the runtime enforces them. Agents have no direct filesystem, shell or network access. Every message is recorded on the session and handled on its own virtual thread.

## SketchUp export: how it works and what is verified

SketchUp's `.skp` format is proprietary and SketchUp has no Linux version, so the factory never writes `.skp` bytes itself and never renames another format to `.skp`. Instead:

1. The exporter checks that the geometry is **approved** and **re-validated** by the Geometry agent.
2. It writes `geometry.json`, `build_manifest.json` (solid boxes; walls are pre-split around doors and windows), `model.obj`, `floor_plan.svg`, `boq.csv` and **`build_model.rb`** to `factory.export.output-dir/<session>/v<version>/`.
3. **If `factory.sketchup.executable` is set**, it launches `SketchUp -RubyStartup build_model.rb`. The script creates real SketchUp groups, tags and materials in millimetres, calls `Model#save`, and writes `sketchup_result.json`.
   **If not set**, open SketchUp yourself, go to *Window → Ruby Console*, and run `load '<path>/build_model.rb'`. Then press **Verify .skp** in the UI.
4. Verification: the file exists, starts with the SketchUp file signature, and the model bounds SketchUp reports match the geometry (±2 mm).

```properties
# Windows
factory.sketchup.executable=C:/Program Files/SketchUp/SketchUp 2024/SketchUp.exe
# macOS
factory.sketchup.executable=/Applications/SketchUp 2024/SketchUp.app/Contents/MacOS/SketchUp
```

**What has been tested, and what hasn't:**
- Tested in CI-style runs: manifest generation, OBJ re-verification, the `.skp` verifier (accepts the signature, rejects a renamed OBJ, rejects wrong bounds), and the whole launch → wait → verify path. The Ruby builder was run against a **stub** of the SketchUp Ruby API (`tools/sketchup/test/`). The stub emulates SketchUp's downward-facing ground faces, and the bounds the builder reported matched the geometry exactly.
- **Not tested here:** a run inside real SketchUp, because no SketchUp is available in the build environment. Please try it on your machine. If the Welcome screen blocks `-RubyStartup` in your SketchUp version, use the Ruby Console method. If your version writes a different file header, verification will report `FAILED` and tell you why.

## Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `factory.ai.provider` | `mock` (env `AI_PROVIDER`) | `mock` (offline, deterministic) or `huggingface` |
| `factory.huggingface.token` | env `HF_TOKEN` | Never logged and never returned by the API |
| `factory.huggingface.base-url` | `https://router.huggingface.co` | OpenAI-compatible router |
| `factory.huggingface.chat-model` | env `HF_CHAT_MODEL` | Must be a chat model your provider serves |
| `factory.huggingface.json-mode` | `true` | Set `false` if the provider rejects `response_format` |
| `factory.ai.max-attempts` / `request-timeout` | `3` / `60s` | Bounded retries for transient errors |
| `factory.agents.step-delay` | `700ms` (env `AGENT_STEP_DELAY`) | Pause between messages so you can watch the agents |
| `factory.agents.require-approval-before-export` | `true` | Human approval gate |
| `factory.agents.max-task-attempts` | `2` | Task-level retries (timeouts, retryable AI errors) |
| `factory.export.output-dir` | `./exports` (env `EXPORT_DIR`) | The only place files are written |
| `factory.sketchup.executable` | empty (env `SKETCHUP_EXECUTABLE`) | Enables automatic `.skp` creation |
| `factory.sketchup.timeout` | `5m` | How long to wait for SketchUp |

### Switching to Hugging Face

```bash
export AI_PROVIDER=huggingface
export HF_TOKEN=hf_xxx                                  # a token with Inference Providers permission
export HF_CHAT_MODEL=meta-llama/Llama-3.1-8B-Instruct   # any chat model your account can use
cd backend && mvn spring-boot:run
```

Free Hugging Face usage is limited and may require credits; availability and rate limits depend on the provider and model. Errors are mapped explicitly: authentication (401/403), quota (402), rate limit (429), unsupported model (404/400), outage (5xx), timeout, and invalid response. Only the last four are retried. If the provider fails, the agents continue with rule-based output and say so in the conversation. Only short **text** (brief, style, palette, room type) is sent to the provider, never photos, files or the whole project.

## Principles enforced in code

- **Measurements are never invented.** Each one carries a source (`MEASURED`, `DRAWING`, `AI_ESTIMATED`, `USER_ASSUMPTION`, `UNKNOWN`). If something is missing, the session pauses in `WAITING_FOR_INPUT` and lists exactly what is needed.
- **Structure is fixed.** Walls, doors, windows and must-keep items are copied unchanged. QA checks this (`STRUCT-01`, `KEEP-01`), and AI structural suggestions are recorded but never applied.
- **Images are not geometry.** Concept visuals are labelled and never used for dimensions. The mock board is stamped **MOCK**.
- **Costs are estimates.** Every BOQ line has a data source and a confidence, and the BOQ says it is not a vendor quote. Budget comparison is skipped when currencies differ.
- **Revisions are versioned** (`v1`, `v2`, …). Superseded tasks are cancelled rather than deleted, and you can see them on the task board.

**Coordinates:** millimetres, origin at the inner south-west floor corner, +X along the SOUTH wall (room length), +Y along the WEST wall (room width), +Z up. An opening's offset is measured from the right-hand corner when you stand inside the room facing that wall.

## Tests

```bash
cd backend && mvn test        # 71 tests: units, validator, layout, BOQ, AI gateway + HF adapter (mock HTTP), SKP verifier, full workflows
cd frontend && npm test       # component and helper tests
```

No test makes a network call. `SketchUpLaunchIntegrationTest` uses the Ruby stub and is skipped when Ruby is not installed.

## Not in this version

These were left out deliberately, following your direction to keep it simple and storage-free:
- Persistence (sessions are lost on restart), photo/PDF upload and floor-plan parsing.
- Blender scene generation, ComfyUI, and Hugging Face image generation. The `ImageProvider` interface is there, but only the mock concept board is implemented.
- Multi-room projects. One session = one room.

## Layout

```
backend/   Spring Boot app (agents/, ai/, geometry/, design/, materials/, lighting/, export/, session/)
frontend/  React UI (network graph, task board, conversation, approval, outputs)
tools/sketchup/test/  SketchUp API stub + fake launcher used by tests
```
