# E-CYCLE: Gemini chatbot, IntelliJ setup, and deployment

## 0. What changed
The assistant now calls **Google Gemini** (Gemini's OpenAI-compatible endpoint) instead of OpenAI. No new Maven dependency: LangChain4j's OpenAI client is simply pointed at
`https://generativelanguage.googleapis.com/v1beta/openai/`. Files touched: `AssistantConfig.java`, `AppProps.java`, `application.yml`, `.env.example`, `docker-compose.yml`, README/ARCHITECTURE.
If the key is missing or Gemini is unreachable, the rule-based fallback still answers.

## 1. Get a Gemini API key
1. Open https://aistudio.google.com/apikey, sign in, **Create API key**.
2. Keep it secret (never commit it). Model names change; if `gemini-3.8-flash` is rejected, copy a current Flash model id from AI Studio into `ASSISTANT_MODEL`.

## 2. Install prerequisites
- JDK 21 (IntelliJ: File > Project Structure > SDKs > Add JDK > Download JDK > 21, e.g. Temurin)
- Maven 3.9+ (IntelliJ bundles one; fine)
- Docker Desktop (PostgreSQL 16 + MongoDB 7)
- IntelliJ IDEA (Community works; Ultimate adds Spring tooling)

## 3. Open the project in IntelliJ
1. Unzip, then **File > Open** and select the `ecycle` folder (the one with the root `pom.xml`). Choose *Open as Project*, *Trust*.
2. Wait for Maven import (modules: `shared`, `backend`, `frontend`). If not imported: right-click root `pom.xml` > **Add as Maven Project**.
3. **File > Project Structure > Project**: SDK = 21, language level = 21.
4. Settings > Build > Compiler > Annotation Processors: **Enable annotation processing** (the code uses Lombok). Install the Lombok plugin if IntelliJ asks.
5. Maven tool window > `ecycle` > Lifecycle > **install** (or `mvn -DskipTests install`) so `shared` is built first.
   Note: the README says the Spring/JavaFX code was never compiled, so fix any first-build compile errors before going further.

## 4. Start the databases
In IntelliJ's terminal at the project root:
```bash
docker compose up -d db mongo
```

## 5. Run configurations (backend)
Run > Edit Configurations > **+ Spring Boot** (or *Application*) with:
- Main class: `com.ecycle.backend.EcycleApplication`, module `ecycle-backend`
- Active profiles: `dev` (demo data, mock payments, ephemeral JWT secret)
- Environment variables (paste in the "Environment variables" field):
```
ASSISTANT_PROVIDER=gemini;ASSISTANT_API_KEY=YOUR_GEMINI_KEY;ASSISTANT_MODEL=gemini-3.8-flash
```
Run it. The log should show `AI assistant: LangChain4j with model gemini-3.8-flash at https://generativelanguage.googleapis.com/v1beta/openai/`.
If it says "rule-based fallback", the key was not picked up.

## 6. Run the desktop client
Add a **Maven** run configuration: command line `-pl frontend -am javafx:run`, or run `scripts/run-frontend.bat`/`.sh`.
Log in with `buyer@ecycle.local` / `Buyer@123`, open **Eco-Assistant** and ask, e.g., "How many eco-points do I have?". The reply mode label should read `AI` (not `RULES`).

## 7. Test the chatbot
- `mvn -pl backend test` (no Docker needed for unit tests); `mvn -pl backend verify` adds the Testcontainers integration test.
- Quick key check outside the app:
```bash
curl https://generativelanguage.googleapis.com/v1beta/openai/chat/completions \
  -H "Authorization: Bearer $GEMINI_API_KEY" -H "Content-Type: application/json" \
  -d '{"model":"gemini-3.8-flash","messages":[{"role":"user","content":"hi"}]}'
```
A 400/404 about the model means pick another model id. A 429 means free-tier rate limits.

## 8. Build for release
```bash
mvn clean verify
# backend/target/ecycle-backend-1.0.0.jar   (API)
# frontend/target/ecycle-desktop.jar        (desktop client, built for the OS you build on)
```

## 9. Deploy
The system has two parts: the **API** (deployed to a server) and the **JavaFX desktop client** (distributed to users, pointing at your API).

### 9a. Managed databases
- PostgreSQL 16: Neon, Supabase, Railway, Render, or AWS RDS. Note host, db, user, password.
- MongoDB: MongoDB Atlas (free M0 tier). Create a user, allow your server's IP, copy the `mongodb+srv://...` URI.

### 9b. Deploy the API with Docker (Render / Railway / Fly.io / any VPS)
The repo already has `backend/Dockerfile` (build from repo root: `docker build -f backend/Dockerfile .`).
Set these environment variables on the host (do NOT set `SEED_DEMO`):
```
SPRING_PROFILES_ACTIVE=            (leave empty / not "dev")
DB_URL=jdbc:postgresql://HOST:5432/DBNAME?sslmode=require
DB_USER=...  DB_PASSWORD=...
MONGO_URI=mongodb+srv://USER:PASS@cluster.mongodb.net/ecycle
JWT_SECRET=<32+ random chars>       (required outside dev)
CERT_SIGNING_SECRET=<another random string>
ADMIN_EMAIL=you@example.com  ADMIN_PASSWORD=<strong>
PUBLIC_BASE_URL=https://your-api.example.com
ASSISTANT_PROVIDER=gemini
ASSISTANT_API_KEY=<Gemini key>      (store as a secret)
ASSISTANT_MODEL=gemini-3.8-flash
PAYMENT_PROVIDER=mock               (or razorpay with RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET)
```
Persistent disk: mount a volume at `/app/data/uploads` (listing images/evidence), or uploaded files vanish on redeploy.
Flyway creates the tables automatically on first start. Check the logs for "Started EcycleApplication".

Render example: New > Web Service > connect your Git repo > Runtime **Docker**, Dockerfile path `backend/Dockerfile`, root dir `.`, add the env vars, add a disk at `/app/data/uploads`.

Single-VPS alternative: copy the repo, create `.env` (from `.env.example` plus `JWT_SECRET`), run `docker compose --profile app up -d --build`, and put Caddy or nginx with HTTPS in front of port 8080.

### 9c. Point the desktop client at production
```bash
ECYCLE_API=https://your-api.example.com java -jar frontend/target/ecycle-desktop.jar
```
Give users the jar plus that variable (or a small launcher script). Build the jar on each OS you want to support, since JavaFX natives are OS-specific.

### 9d. Production checklist
- Use HTTPS only; rotate the Gemini key if it was ever pasted into chat or committed.
- Keep `SEED_DEMO=false`; change the admin password.
- The assistant is rate limited per user (`ASSISTANT_PER_MINUTE`, default 15). The Gemini free tier has its own quotas; enable billing in Google AI Studio for real traffic.
- Back up PostgreSQL and MongoDB.
