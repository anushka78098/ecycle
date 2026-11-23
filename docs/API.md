# REST API summary

All endpoints are under `/api`, JSON, `Authorization: Bearer <jwt>` except where marked public. Errors share one shape: `{status, error, message, timestamp, fieldErrors}`. Roles: S=seller, B=buyer, R=resolver, A=admin.

| Area | Endpoint | Who |
|---|---|---|
| Health | `GET /health` | public |
| Auth | `POST /auth/register` `login` `logout` `forgot` `reset` | public (logout: any) |
| Profile | `GET/PUT /profile`, `POST /profile/password` | any |
| Catalogue | `GET /categories` | public |
| Listings | `POST /listings` (S), `GET /listings/mine` (S), `GET /listings?q&categoryId&brand&condition&minPrice&maxPrice&location&functional&pathway&sort&page&size`, `GET /listings/{id}`, `PUT/DELETE /listings/{id}` (S, pending/rejected only), `POST /listings/{id}/images` (S, multipart `files`), `GET /listings/{id}/images/{imageId}`, `GET /listings/compare?ids=`, `GET /listings/{id}/lifecycle` `passport` `auction` | varies |
| Hazard & data | `GET /hazard/rules`, `GET/POST /listings/{id}/hazard`, `POST/GET /listings/{id}/sanitization`, `GET /listings/{id}/sanitization/certificate` | S/A/buyer of the order |
| Offers | `POST /offers` (B), `GET /offers/mine` (B), `GET /offers/received` (S), `POST /offers/{id}/accept\|reject` (S), `/withdraw` (B) | |
| Orders | `POST /orders` (B), `GET /orders/mine?as=buyer\|seller`, `GET /orders/{id}`, `PUT /orders/{id}/address` (B), `POST /orders/{id}/cancel`, `PUT /orders/{id}/shipment` (S), `POST /orders/{id}/confirm-delivery` (B), `GET /orders/{id}/receipt` | parties |
| Payments | `POST /payments/orders/{orderId}/init`, `POST /payments/{id}/verify`, `POST /payments/{id}/mock-confirm` (sandbox only) | B |
| Auctions | `GET /auctions`, `GET /auctions/mine`, `GET /auctions/{id}`, `POST /auctions/{id}/bids` (verified B), `POST /auctions/{id}/accept/{bidId}` (S), `POST /auctions/{id}/cancel` (S) | |
| Passports | `GET /passports/mine`, `GET /passports/{code}`, `GET /passports/{code}/qr?size=` (PNG), `POST /passports/{code}/dismantle` `component-action` `outcome` | owner/admin |
| Wishlist/Reviews | `GET/POST/DELETE /wishlist/{listingId}`, `GET /wishlist/ids`, `POST /reviews`, `GET /reviews/user/{id}` | |
| Matching | `GET /recommendations`, `GET/POST/PUT/DELETE /requirements` | B |
| Chat | `POST /chat`, `GET /chat/thread?listingId&with`, `GET /chat/conversations` | |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `/read-all` | |
| Impact | `GET /impact/mine?as=`, `GET /impact/global` | |
| Dashboards | `GET /dashboard/{SELLER\|BUYER\|RESOLVER\}` | role holder |
| Disputes | `POST /disputes` (B/S), `GET /disputes/mine`, `GET /disputes/{id}`, `POST /disputes/{id}/evidence` (multipart), `GET /disputes/{id}/evidence/{eid}/file`, `POST /disputes/{id}/messages` | parties/staff |
| Resolver | `GET /resolver/disputes?status&priority`, `GET /resolver/history`, `POST /resolver/disputes/{id}/assign\|resolve\|reject`, `PUT /resolver/disputes/{id}/priority` | R, A |
| Admin | `/admin/users`, `/admin/staff`, `/admin/sellers/{id}/verify`, `/admin/buyers/{id}/verify`, `/admin/listings[/pending]`, `/admin/listings/{id}/approve\|reject`, `/admin/categories`, `/admin/orders`, `/admin/audit`, `/admin/config`, `/admin/env-factors`, `/admin/hazard/rules`, `/admin/sanitization/pending`, `/admin/sanitization/{id}/verify`, `/admin/resolvers` | A |
| Recyclers | `GET /recyclers?lat&lng&radiusKm&category`, `GET /recyclers/mine`, `POST /recyclers`, `PUT /recyclers/{id}` | any / verified recycler, A |
| Pickups | `POST /pickups`, `GET /pickups?as=requester\|recycler`, `GET /pickups/{id}`, `GET /pickups/track/{code}`, `POST /pickups/{id}/status` `ping` `verify`, `GET /pickups/{id}/wipe-checklist` | requester / recycler / A |
| Wipe checklist | `GET /listings/{id}/wipe-checklist`, `POST /listings/{id}/wipe-checklist` (S), `GET /listings/{id}/wipe-checklists`, `GET /categories/{id}/wipe-checklist` | S / A |
| Certificates | `GET /certificates`, `GET /certificates/{no}`, `GET /certificates/{no}/pdf`, `GET /certificates/listing/{id}` | holder / issuer / A |
| Public verification | `GET /public/certificates/verify?no&code` (JSON), `GET /public/certificates/{no}/page?code` (HTML, opened by the QR) | **public** (number + code required) |
| Custody | `GET /custody/{assetCode}`, `POST /custody/scan`, `POST /custody/{assetCode}/events` | authenticated (custodian rules enforced) |
| Eco | `GET /eco/dashboard` `history` `badges` `challenges` `leaderboard` `rewards` `redemptions` `estimate`, `PUT /eco/leaderboard/visibility`, `POST /eco/rewards/{id}/redeem` | any |
| Eco admin | `GET/POST/PUT /admin/eco/rewards`, `GET/POST/PUT /admin/eco/challenges` | A |
| Assistant | `POST /assistant/chat`, `GET /assistant/status` `conversations` `conversations/{id}` `actions`, `DELETE /assistant/conversations/{id}`, `POST /assistant/actions/{id}/confirm\|cancel` | any |
| Live | STOMP over WebSocket `/ws`; `CONNECT` header `Authorization: Bearer <jwt>`; subscribe `/user/queue/events`; payload `{type: PICKUP\|NOTIFICATION\|CHAT, payload, at}` | authenticated |

Notification and chat ids are now MongoDB ObjectId strings (previously numbers).


## Google sign-in
| `POST /auth/google` | body `{idToken, role}` (`role` SELLER or BUYER, only used the first time) | public; returns the same `AuthResponse` as `/auth/login` |
| `GET /auth/google/config` | `{enabled}` | public |
