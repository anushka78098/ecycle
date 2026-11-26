# E-CYCLE - Electronic Waste Reuse, Recycling and Circular Marketplace

A Java 21 desktop marketplace for e-waste with **three roles only: Seller, Buyer and Dispute Resolver**. Resolvers also moderate (approve listings, verify users, verify data wiping); there is no separate admin role.

**JavaFX desktop client -> Spring Boot REST + STOMP/WebSocket API -> PostgreSQL (Flyway) + MongoDB**. Java everywhere; no web front end.

## What changed in this version
- **Three roles.** `ADMIN` is removed from the enum, security rules and menus. Migration `V5` turns any existing admin into a resolver and deletes the role. The bootstrap account `admin@ecycle.local` is now a resolver (moderator).
- **Google sign-in** (`POST /api/auth/google`, "Continue with Google" on the login screen). See "Google sign-in" below.
- **Demo data, about 20 per feature**, created through the real services (`DemoDataSeeder`): 63 listings with generated photos (2 photos on the first 20), 20 offers, 40 orders with sandbox payments (5 paid, 5 shipped, 30 delivered), 20 disputes with evidence photos and messages (open, assigned, awaiting evidence, resolved, rejected), 20 reviews, 20 chat threads, 20 wishlist entries, 20 bids across 4 reverse auctions, plus passports, notifications and impact figures that the services produce along the way.
- Demo impact, component-genealogy, and sanitization examples use the actual service workflows. Impact values are explicitly estimates; component records and the pending sanitization evidence are synthetic demo data, not real measurements or proof of a device wipe. When demo accounts already exist, startup idempotently backfills missing outcomes, disputes, and samples instead of skipping all demo data.
- **Menus trimmed** to what is relevant: marketplace, listings, offers, auctions, orders, passports, hazard and data safety, impact, messages, Eco-Assistant, disputes, reviews, and moderation for resolvers. Pickups, certificates, chain of custody, recycler map and the rewards programme are hidden from the menus (backend code is untouched).
- Messaging uses stored conversations and STOMP live push: new messages appear as individual chat bubbles, refresh the inbox, and notify the recipient. Seller listings and reverse auctions use photo-forward cards rather than data tables.

## Verification status
Backend unit tests pass and the JavaFX client compiles with JDK 21. Docker Desktop was unavailable during this check, so the app could not be run against PostgreSQL/MongoDB and the integration tests were not run. Gemini calls also need a valid `ASSISTANT_API_KEY` in `.env`.

## Prerequisites
JDK 21+, Maven 3.9+, Docker (PostgreSQL and the integration tests). Desktop session for the JavaFX client.

## Run it (development)
```bash
# Copy .env.example to .env and set ASSISTANT_API_KEY to your Google AI Studio key.
# terminal 1: PostgreSQL + MongoDB (docker compose) and the API (dev profile: demo data, mock payments, ephemeral JWT secret)
./scripts/run-backend.sh            # Windows: scripts\run-backend.bat
# terminal 2: desktop client
./scripts/run-frontend.sh           # Windows: scripts\run-frontend.bat
```
Equivalent manual commands:
```bash
docker compose up -d db
mvn -pl backend -am -DskipTests install
SPRING_PROFILES_ACTIVE=dev mvn -f backend/pom.xml spring-boot:run
mvn -pl frontend -am -DskipTests install
mvn -f frontend/pom.xml javafx:run       # ECYCLE_API=http://host:8080 to point elsewhere
```
Build everything and the client fat jar: `mvn clean verify` -> `frontend/target/ecycle-desktop.jar` (`java -jar`, built for the OS you build on) and `backend/target/ecycle-backend-1.0.0.jar`.

### Demo accounts (dev profile only; never enable `SEED_DEMO` in production)
| Role | Email | Password |
|---|---|---|
| Seller (verified) | seller@ / seller2@ / seller3@ / seller4@ecycle.local | Seller@123 |
| Buyer (verified) | buyer@ / buyer2@ / buyer3@ / buyer4@ecycle.local | Buyer@123 |
| Buyer (verified specialist recycler) | recycler@ecycle.local | Buyer@123 |
| Dispute resolver | resolver@ / resolver2@ecycle.local | Resolver@123 |
| Resolver (bootstrap moderator) | admin@ecycle.local | Admin@12345 |

### Live marketplace demo
1. Sign in as a seller, create a listing, then choose **Upload photos** on its listing card. The listing becomes public in the marketplace after a resolver approves it; uploaded photos are shown on its marketplace card and product page.
2. Sign in as a buyer, open that product and choose **Message seller**. The seller receives a live notification; the conversation appears in **Messages**, and replies arrive as separate chat bubbles without refreshing.
3. To demo auctions, enable **Sell via reverse auction** when creating the listing. After approval, it appears in **Reverse auctions**; a verified buyer can place a bid, and the seller can accept a bid to create an order.

Suggested 5-minute demo: buyer buys the monitor (sandbox payment) -> seller ships -> buyer confirms delivery -> passport ownership moves -> open **Impact** to show the estimated CO₂ dashboard -> buyer raises a dispute and uploads evidence -> resolver claims it and issues a partial refund. For the existing synthetic sanitization sample, review the clearly labeled pending demo record as a resolver; do not treat it as proof of a real wipe or issue a real certificate from it. Then explore the demo component genealogy, recycler handling for the swollen-battery phone, and bids on the PCB lot.

## Google sign-in
1. Google Cloud Console -> APIs & Services -> OAuth consent screen (External, add yourself as a test user), then Credentials -> Create credentials -> **OAuth client ID** -> application type **Desktop app**.
2. Backend: set `GOOGLE_CLIENT_ID=<client id>` (it verifies the ID token's audience, issuer and verified e-mail).
3. Desktop client, in the shell that starts it: `ECYCLE_GOOGLE_CLIENT_ID=<client id>` and `ECYCLE_GOOGLE_CLIENT_SECRET=<client secret>` (PowerShell: `$env:ECYCLE_GOOGLE_CLIENT_ID="..."`).
4. On the login screen choose Buyer or Seller next to "New here? Join as", then click **Continue with Google**. Your browser opens, you pick the account, and the app signs in. First-time users get an account with that role; existing e-mails are linked to their Google account on first use. Resolvers can sign in with Google but are never created through it.
The flow is OAuth 2.0 authorization code + PKCE with a loopback redirect, so no Google password ever reaches the app. A Desktop client secret is not treated as confidential by Google.

## Configuration (environment variables; see `.env.example`)
`GOOGLE_CLIENT_ID ECYCLE_GOOGLE_CLIENT_ID ECYCLE_GOOGLE_CLIENT_SECRET DB_URL DB_USER DB_PASSWORD MONGO_URI JWT_SECRET CERT_SIGNING_SECRET PUBLIC_BASE_URL REQUIRE_WIPE_CHECKLIST ASSISTANT_PROVIDER ASSISTANT_API_KEY ASSISTANT_BASE_URL ASSISTANT_MODEL ECYCLE_TILE_URL(client) JWT_SECRET (>=32 chars, required outside dev) ADMIN_EMAIL ADMIN_PASSWORD FILE_STORAGE_DIR PAYMENT_PROVIDER (mock|razorpay) RAZORPAY_KEY_ID RAZORPAY_KEY_SECRET MAIL_ENABLED MAIL_HOST MAIL_PORT MAIL_USER MAIL_PASSWORD MAIL_FROM RATE_LIMIT_PER_MINUTE`. No secret is stored in the repository. Password-reset codes are only logged in dev mode when mail is disabled.

### AI assistant
The assistant uses **LangChain4j** (`AiServices` + `@Tool` function calling + persistent `ChatMemory`) against **Google Gemini** (through Gemini's OpenAI-compatible endpoint): set `ASSISTANT_PROVIDER=gemini` and `ASSISTANT_API_KEY` to a key from https://aistudio.google.com/apikey (model via `ASSISTANT_MODEL`, default `gemini-3.1-flash-lite`). The backend reads the project-root `.env`; copy `.env.example` to `.env` and add your key before starting it. Without a key, the UI clearly reports rule-based mode; if Gemini is unreachable, the reply is marked as a fallback. OpenAI still works with `ASSISTANT_PROVIDER=openai`, or `ASSISTANT_BASE_URL=http://localhost:11434/v1` with `ASSISTANT_MODEL=llama3.1` for a local Ollama (`docker compose --profile ai up -d`). Assistant transcripts and model memory are persisted in MongoDB, and are listed in the app's conversation history. Tools run as the signed-in user; **booking, cancelling and redeeming only create a proposal that the user confirms with a button** (the model cannot confirm), proposals expire after 10 minutes, and requests are rate-limited per user.

### Docker
`docker compose up -d` starts PostgreSQL and MongoDB; `docker compose --profile app up -d --build` also runs the API in a container (needs `JWT_SECRET` in `.env`); `--profile ai` adds Ollama. The JavaFX client runs on the desktop (`scripts/run-frontend.sh`).

### Payments
`mock` (default): sandbox adapter; the client asks the backend to simulate the gateway callback, and the backend runs the **same** signature verification as for real payments. `razorpay`: set test-mode keys (`rzp_test_...`); the desktop client opens Razorpay Checkout in a WebView and sends only payment id + signature to `/api/payments/{id}/verify`. A browser/WebView "success" is never trusted.

## Database
PostgreSQL keeps users, authentication, products, orders, pickups, disputes, certificates, eco-points and chain-of-custody records. **MongoDB** keeps chat messages, chatbot conversation history/memory/pending actions and notifications (Spring Data MongoDB; JPA and Mongo repositories are scanned from separate packages). `LegacyMongoMigrator` copies existing chat/notification rows from PostgreSQL to MongoDB once, on first start; the old tables are left untouched. Real-time delivery uses Spring WebSocket/STOMP at `/ws` (JWT in the CONNECT frame; users subscribe only to `/user/queue/events`; clients cannot publish).
Flyway migrations (`backend/src/main/resources/db/migration`): `V1` schema (36 tables incl. all 25 requested), `V2` append-only triggers, `V3` reference data (roles, categories, environmental factors, scoring config, hazard rules), `V4` pickups, recycler locations, custody events, certificates, wipe checklists, eco-points ledger, badges, challenges, rewards (+ append-only triggers and seed data). Hibernate runs in `validate` mode, so entity/schema drift fails fast at startup. ER diagram: `docs/ER-DIAGRAM.md`.

## Tests
```bash
mvn -pl backend test        # unit tests (JUnit 5 + Mockito), no Docker needed
mvn -pl backend verify      # + Testcontainers integration test (needs Docker)
./scripts/verify-engines.sh # engines only, JDK only
```
Coverage: registration, authentication/lockout, role authorization, listing creation/approval, offers, order creation/shipping/cancellation, payment verification (valid, forged, replay, wrong user), environmental impact, Circular Value Score, recommendations, dispute creation/evidence/assignment/resolution/privacy, unauthorized access, passport/genealogy, auctions, hazard rules and sanitization certificates, DB immutability.

## Feature map
| Requirement | Where |
|---|---|
| Seller / Buyer / Resolver / Admin modules | `frontend/.../view`, `backend/.../controller`, role menus in `navigation/Menus.java` |
| Circular lifecycle timeline | `product_lifecycle`, `LifecycleService`, `component/Timeline` (Product dialog > Lifecycle) |
| Circular Value Score + explanation | `engine/CircularValueEngine`, `ScoringService`, Product dialog > Score |
| Smart matching | `engine/MatchingEngine`, `RecommendationService`, "Recommended for you" |
| Environmental impact (estimates vs measured, audited inputs) | `engine/EnvironmentalCalculator`, `EnvironmentalService`, Impact views |
| Evidence-based disputes | `DisputeService`, immutable `dispute_evidence`, `DisputeViews` |
| Secure transactions, refunds, receipts | `OrderService`, `PaymentService`, `RefundService`, gateways |
| **A. Digital passport and genealogy** | `PassportService` (QR via ZXing), `PassportViews`, `GenealogyTree`, Swing label printing |
| **B. Reverse auction and Circular Bid Score** | `AuctionService`, `AuctionScheduler`, `BidScoreEngine`, `AuctionViews` |
| **C. Hazard and data-destruction risk engine** | `HazardEngine`, `HazardService`, `PurchaseRules`, admin rules + sanitization queue |
| **1. AI Eco-Assistant (LangChain4j, function calling)** | `assistant/` (`EcoTools`, `AssistantActions`, `AssistantService`, `RuleBasedAssistant`, `MongoChatMemoryStore`), `AssistantController`, `view/AssistantView` |
| **2. Data sanitization + PDF certificates** | `engine/WipeChecklists`, `SanitizationWorkflowService` (extends the existing sanitization module), `CertificateService`, `engine/SimplePdf` + `CertificateRenderer`, public `/api/public/certificates/**`, `WipeDialog`, `CertificateViews` |
| **3. Recycler map + pickup tracking** | `RecyclerService`, `PickupService`, `engine/PickupFlow`, `WebSocketConfig`, `LiveService`, `component/RecyclerMap` (OSM tiles), `RecyclerViews`, `PickupViews`, `api/LiveClient` |
| **4. Eco-points and gamification** | `EcoService`, `engine/EcoEngine`, `EcoController`, `EcoViews` (dashboard, leaderboard, rewards, admin catalogue/challenges); reuses `EnvironmentalCalculator` |
| **5. QR chain of custody** | `CustodyService`, `engine/HashChain`, `CustodyController`, `CustodyViews` (QR image scan via ZXing); reuses passports and their QR codes |

## How the new features reuse existing modules
- **Passports/QR:** every custody chain is keyed by the passport code; `PassportService.addEvent` mirrors into the chain, so existing flows (creation, ownership transfer, dismantling) extend the chain with no duplicate model.
- **Sanitization:** the checklist creates the same `sanitization_records` row the admin already verifies; verification publishes an audit event, and `DomainEventBridge` issues the DSC certificate (same number) and awards points. The legacy free-text endpoint still works unless `REQUIRE_WIPE_CHECKLIST=true`.
- **Environmental impact:** carbon figures come from `EnvironmentalCalculator`/factors; a completed pickup records an impact for linked listings and eco-points are awarded from the impact event (idempotent ledger, so nothing is paid twice).
- **Certificates** are HMAC-SHA256 signed (key from `CERT_SIGNING_SECRET`), contain a content hash, and anchor the custody chain head so tail truncation is detectable. Public verification needs both number and verification code and returns no personal data.

## Extras added beyond the brief
Account lockout after 5 failures; JWT revocation on logout; password reset with expiring one-time codes; seller trust score computed from reviews; in-app notification centre with polling bell and optional email; listing-scoped buyer/seller chat; anonymised bidder labels for other buyers; hazard re-assessment with append-only inspection history; printable receipts and certificates; product comparison; soft-withdrawal that preserves history; one-active-order and one-active-dispute database guards; configurable scoring weights editable from the admin UI.

## Known limitations and assumptions
- Custody-chain tamper evidence is detective, not preventive: a database superuser could rewrite the whole chain; the head hash anchored in certificates (and the append-only triggers) limit this but are not a blockchain.
- The recycler map needs internet for OpenStreetMap tiles (`ECYCLE_TILE_URL` can point at your own tile server; respect the tile usage policy). Markers and lists work offline.
- Carbon figures, tree/car equivalents and eco-point values are estimates from configurable factors (`eco.pts.*` in `score_config`), not audited numbers.
- Chatbot answers from an LLM can be wrong; account data always comes from tools, and actions need confirmation.
- A listing is sold as one **lot**: `expected_price` is for the whole lot and orders always take the whole lot.
- Environmental factors seeded in `V3` are planning placeholders, not audited figures.
- Rate limiting is in-memory (single instance). Files are stored on local disk (`FILE_STORAGE_DIR`); use object storage when scaling out.
- FXML is used for login, registration, password reset and the shell; data-heavy screens are built in Java through reusable components to keep them data-driven.
- The JavaFX client reads responses as JSON trees (`JsonNode`) rather than re-using the shared response records.
- No refresh tokens: sessions last `JWT_EXPIRY_MINUTES` (default 480) then require sign-in.

## Layout
```
pom.xml            parent (Java 21, Spring Boot 3.3.5 BOM, JavaFX 21)
shared/            enums + DTO records
backend/           Spring Boot API, Flyway migrations, engines, tests
frontend/          JavaFX client (FXML, CSS, charts, Swing interop)
docs/              ARCHITECTURE, ER-DIAGRAM, API, FORMULAS
docker-compose.yml PostgreSQL + MongoDB (+ api / ollama profiles)
scripts/           run scripts, engine smoke check
```
