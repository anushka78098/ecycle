# Architecture

```
JavaFX desktop client  --HTTPS/JSON (Java HttpClient, JWT bearer)-->  Spring Boot REST API  --JPA/Hibernate-->  PostgreSQL (Flyway)
 frontend/             --WebSocket + STOMP (/ws, JWT in CONNECT)-->   backend/             --Spring Data MongoDB-->  MongoDB
                                                                     (assistant: LangChain4j -> Google Gemini (OpenAI-compatible endpoint), optional)
```

## Modules
- `shared` - enums and the DTO contract (Java records with Jakarta Validation) used by both sides.
- `backend` - `controller` (REST) -> `service` (business rules, transactions) -> `repository` (Spring Data JPA) -> `entity`. Pure rule engines live in `engine` (no framework imports). `config` holds security/JWT/rate limiting; `exception` the uniform error contract; `scheduler` the auction closer.
- `frontend` - `view` (screens + FXML controllers), `component` (reusable UI: `ResourceView`, `FormDialog`, charts, timeline, genealogy tree, Swing print), `api` (the only network code), `navigation` (shell and role menus), `util` (async, alerts, formatting), `theme` (CSS in resources).

## Key decisions
- **No database access from the client.** The client only calls `ApiClient`; controllers contain UI logic only.
- **UI thread safety.** Every request runs through `Async.run` (daemon pool + JavaFX `Task`); results are applied on the FX thread. Swing code (`SwingPrint`) is created on the EDT and printing runs on its own thread.
- **Swing is used where it is the right tool:** `java.awt.print` rendering of passport QR labels, receipts and sanitization certificates, hosted in JavaFX through `SwingNode`. `WebView` is used only for Razorpay's hosted checkout.
- **Authentication.** Stateless JWT (HS256, secret from `JWT_SECRET`), BCrypt cost 12, 5-failure lockout, token revocation on logout, roles re-read from the database on every request (suspension is immediate). CSRF is disabled deliberately: bearer tokens in headers, no cookies.
- **Authorization** is server-side twice: URL rules in `SecurityConfig` plus `@PreAuthorize` and ownership checks in services. The client hides buttons but never decides.
- **Integrity in the database.** Triggers make evidence, audit log, bids, lifecycle, passport events, inspections, impact records and dispute messages/resolutions append-only. Partial unique indexes prevent double sale and duplicate active disputes. Money is `NUMERIC(12,2)`/`BigDecimal`.
- **Payments.** `PaymentGateway` interface with `MockPaymentGateway` (sandbox, default) and `RazorpayPaymentGateway`. The amount is read from the server-side order, the HMAC-SHA256 signature is verified with a constant-time compare on the server, failed attempts are recorded, and the payment-id column is unique so a payment cannot be replayed onto another order.
- **Dispute separation of duties.** Resolvers decide; refunds are executed by `RefundService` as a system action after the decision is recorded. Resolver DTOs expose names and city only (no email, phone or address) and no endpoint lets a resolver edit payment data.
- **Hazard routing.** `PurchaseRules` is the single gate used by buy-now, offers and bids: hazardous items go only to verified RECYCLER buyers and ship only as `SPECIALIST_PICKUP`; unsanitized storage devices cannot be resold.

## Lifecycle
LISTED -> APPROVED -> PURCHASED -> DELIVERED -> (DISMANTLED) -> REUSED / REPAIRED / REFURBISHED / RECYCLED / DISPOSED, with HAZARD_HOLD recorded for blocked items. Each transition writes a `product_lifecycle` row (stage, pathway, actor, timestamp) and mirrors into the product's passport.

## Upgrade: assistant, certificates, pickups, eco-points, custody
- **Two stores, one rule:** anything transactional or evidentiary (users, orders, pickups, certificates, eco-points ledger, custody chain) is PostgreSQL; high-volume conversational data (chat, assistant history/memory, notifications) is MongoDB. Mongo writes for notifications/live pushes are best-effort and never roll back a PostgreSQL transaction.
- **Event bridge:** `AuditService` publishes `AuditRecorded`; `DomainEventBridge` reacts (`IMPACT_RECORDED` -> eco-points, `SANITIZATION_VERIFIED` -> certificate + points), so existing services were not rewritten and keep their constructors/tests. Certificate issuance runs in the same transaction as verification.
- **Custody hash chain:** per-asset sequence, canonical length-prefixed fields, millisecond timestamps, `hash = SHA-256(prevHash || fields)`; rows are append-only via triggers, `UNIQUE(asset_code, seq)` plus a row lock on the passport serialise appends. Verification recomputes the chain; certificates store the head (seq + hash) to detect tail truncation.
- **Pickup flow:** `PickupFlow` is the only definition of legal transitions (REQUESTED -> ACCEPTED -> EN_ROUTE -> PICKED_UP -> AT_FACILITY -> COMPLETED, with REJECTED/CANCELLED exits) per actor. Each change writes `pickup_events`, mirrors into custody, notifies and pushes `PICKUP` events over STOMP. Completion (recycler only, after scanning the asset) records measured weight, wipe checklist for data-bearing devices, certificates and points in one transaction.
- **Assistant safety:** tools are constructed per request with the authenticated `AuthUser`; mutating tools only store a `PendingAction` (TTL 10 min); only the REST confirm endpoint (a UI button) executes it, through the same services and authorization as normal API calls. Tool and user text is treated as data in the system prompt.
- **WebSocket security:** CONNECT requires a valid, non-revoked JWT of an active user; SUBSCRIBE only to `/user/queue/**`; client SEND is rejected.
