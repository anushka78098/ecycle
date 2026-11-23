-- E-CYCLE schema (generated from the domain model; see docs/ER-DIAGRAM.md)


CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  phone VARCHAR(20),
  status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  failed_logins INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id BIGINT NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

CREATE TABLE seller_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) UNIQUE,
  business_name VARCHAR(150),
  address VARCHAR(250),
  city VARCHAR(80),
  verification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  verified_at TIMESTAMPTZ,
  trust_score NUMERIC(9,3) NOT NULL DEFAULT 50
);

CREATE TABLE buyer_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) UNIQUE,
  organization VARCHAR(150),
  buyer_type VARCHAR(40) NOT NULL DEFAULT 'INDIVIDUAL',
  city VARCHAR(80),
  verified BOOLEAN NOT NULL DEFAULT false,
  recovery_capability INT NOT NULL DEFAULT 0,
  environmental_rating INT NOT NULL DEFAULT 0
);

CREATE TABLE product_categories (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(80) NOT NULL UNIQUE,
  description VARCHAR(300),
  repairability_base INT NOT NULL DEFAULT 50,
  component_recovery_base INT NOT NULL DEFAULT 40,
  life_months INT NOT NULL DEFAULT 60,
  default_weight_kg NUMERIC(9,3) NOT NULL DEFAULT 1,
  dismantlable BOOLEAN NOT NULL DEFAULT false,
  active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE listings (
  id BIGSERIAL PRIMARY KEY,
  seller_id BIGINT NOT NULL REFERENCES users(id),
  category_id BIGINT NOT NULL REFERENCES product_categories(id),
  name VARCHAR(150) NOT NULL,
  brand VARCHAR(80),
  model VARCHAR(80),
  age_months INT NOT NULL DEFAULT 0,
  physical_condition VARCHAR(40) NOT NULL,
  functional_status VARCHAR(40) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  expected_price NUMERIC(12,2) NOT NULL,
  description TEXT,
  pickup_city VARCHAR(80) NOT NULL,
  pickup_address VARCHAR(250),
  weight_kg NUMERIC(9,3) NOT NULL DEFAULT 1,
  pathway VARCHAR(40),
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING_APPROVAL',
  rejection_reason VARCHAR(300),
  score_function NUMERIC(9,3),
  score_condition NUMERIC(9,3),
  score_repair NUMERIC(9,3),
  score_reuse NUMERIC(9,3),
  score_component NUMERIC(9,3),
  score_life NUMERIC(9,3),
  circular_score NUMERIC(9,3),
  recommended_pathway VARCHAR(40),
  score_explanation TEXT,
  hazard_level VARCHAR(40) NOT NULL DEFAULT 'LOW',
  shipping_blocked BOOLEAN NOT NULL DEFAULT false,
  specialist_required VARCHAR(100),
  requires_sanitization BOOLEAN NOT NULL DEFAULT false,
  sanitization_verified BOOLEAN NOT NULL DEFAULT false,
  auction_enabled BOOLEAN NOT NULL DEFAULT false,
  auction_hours INT,
  auction_min_price NUMERIC(12,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE listing_images (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  file_path VARCHAR(300) NOT NULL,
  original_name VARCHAR(200),
  content_type VARCHAR(60) NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE offers (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  buyer_id BIGINT NOT NULL REFERENCES users(id),
  amount NUMERIC(12,2) NOT NULL,
  message VARCHAR(500),
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  buyer_id BIGINT NOT NULL REFERENCES users(id),
  seller_id BIGINT NOT NULL REFERENCES users(id),
  offer_id BIGINT REFERENCES offers(id),
  bid_id BIGINT,
  quantity INT NOT NULL DEFAULT 1,
  amount NUMERIC(12,2) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING_PAYMENT',
  shipping_address VARCHAR(300),
  cancel_reason VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  provider VARCHAR(30) NOT NULL,
  provider_order_id VARCHAR(80),
  provider_payment_id VARCHAR(80),
  amount NUMERIC(12,2) NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  receipt_no VARCHAR(40) UNIQUE,
  verified_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shipments (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id) UNIQUE,
  carrier VARCHAR(80),
  tracking_no VARCHAR(80),
  status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  specialist_handling BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refunds (
  id BIGSERIAL PRIMARY KEY,
  payment_id BIGINT NOT NULL REFERENCES payments(id),
  order_id BIGINT NOT NULL REFERENCES orders(id),
  dispute_id BIGINT,
  amount NUMERIC(12,2) NOT NULL,
  reason VARCHAR(300),
  status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
  provider_refund_id VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE disputes (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  raised_by BIGINT NOT NULL REFERENCES users(id),
  against_user BIGINT NOT NULL REFERENCES users(id),
  resolver_id BIGINT REFERENCES users(id),
  category VARCHAR(40) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
  priority VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);

CREATE TABLE dispute_assignments (
  id BIGSERIAL PRIMARY KEY,
  dispute_id BIGINT NOT NULL REFERENCES disputes(id),
  resolver_id BIGINT NOT NULL REFERENCES users(id),
  assigned_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dispute_evidence (
  id BIGSERIAL PRIMARY KEY,
  dispute_id BIGINT NOT NULL REFERENCES disputes(id),
  submitted_by BIGINT NOT NULL REFERENCES users(id),
  category VARCHAR(40) NOT NULL,
  description VARCHAR(500),
  file_path VARCHAR(300) NOT NULL,
  original_name VARCHAR(200),
  content_type VARCHAR(60),
  sha256 VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dispute_messages (
  id BIGSERIAL PRIMARY KEY,
  dispute_id BIGINT NOT NULL REFERENCES disputes(id),
  sender_id BIGINT NOT NULL REFERENCES users(id),
  visibility VARCHAR(40) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dispute_resolutions (
  id BIGSERIAL PRIMARY KEY,
  dispute_id BIGINT NOT NULL REFERENCES disputes(id) UNIQUE,
  resolver_id BIGINT NOT NULL REFERENCES users(id),
  resolution_type VARCHAR(40) NOT NULL,
  refund_amount NUMERIC(12,2),
  decision_text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  reviewer_id BIGINT NOT NULL REFERENCES users(id),
  reviewee_id BIGINT NOT NULL REFERENCES users(id),
  rating INT NOT NULL,
  comment VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wishlists (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(160) NOT NULL,
  body VARCHAR(600),
  type VARCHAR(40),
  read_flag BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE environmental_factors (
  id BIGSERIAL PRIMARY KEY,
  category_id BIGINT NOT NULL REFERENCES product_categories(id) UNIQUE,
  carbon_reuse_per_kg NUMERIC(9,3) NOT NULL DEFAULT 0,
  carbon_repair_per_kg NUMERIC(9,3) NOT NULL DEFAULT 0,
  carbon_refurbish_per_kg NUMERIC(9,3) NOT NULL DEFAULT 0,
  carbon_recycle_per_kg NUMERIC(9,3) NOT NULL DEFAULT 0,
  metals_recovery_pct NUMERIC(9,3) NOT NULL DEFAULT 0,
  source_note VARCHAR(400),
  updated_by BIGINT REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE environmental_impact (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  order_id BIGINT REFERENCES orders(id),
  seller_id BIGINT NOT NULL REFERENCES users(id),
  buyer_id BIGINT REFERENCES users(id),
  pathway VARCHAR(40) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  weight_kg NUMERIC(9,3) NOT NULL,
  ewaste_diverted_kg NUMERIC(9,3) NOT NULL,
  carbon_avoided_kg NUMERIC(9,3) NOT NULL,
  materials_recovered_kg NUMERIC(9,3) NOT NULL,
  measured BOOLEAN NOT NULL DEFAULT false,
  methodology TEXT NOT NULL,
  inputs TEXT NOT NULL,
  recorded_by BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_lifecycle (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  stage VARCHAR(40) NOT NULL,
  pathway VARCHAR(40),
  actor_id BIGINT REFERENCES users(id),
  note VARCHAR(400),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE buyer_requirements (
  id BIGSERIAL PRIMARY KEY,
  buyer_id BIGINT NOT NULL REFERENCES users(id),
  category_id BIGINT REFERENCES product_categories(id),
  max_budget NUMERIC(12,2),
  city VARCHAR(80),
  min_condition VARCHAR(40),
  intended_use VARCHAR(300),
  required_components VARCHAR(300),
  preferred_pathway VARCHAR(40),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(60),
  entity_id BIGINT,
  detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE passports (
  id BIGSERIAL PRIMARY KEY,
  passport_code VARCHAR(40) NOT NULL UNIQUE,
  listing_id BIGINT REFERENCES listings(id) UNIQUE,
  parent_id BIGINT REFERENCES passports(id),
  component_type VARCHAR(60),
  name VARCHAR(150) NOT NULL,
  stage VARCHAR(40) NOT NULL,
  current_owner_id BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE passport_events (
  id BIGSERIAL PRIMARY KEY,
  passport_id BIGINT NOT NULL REFERENCES passports(id),
  event_type VARCHAR(40) NOT NULL,
  actor_id BIGINT REFERENCES users(id),
  detail VARCHAR(500),
  value_amount NUMERIC(12,2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auctions (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id) UNIQUE,
  ends_at TIMESTAMPTZ NOT NULL,
  min_price NUMERIC(12,2),
  status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
  winning_bid_id BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bids (
  id BIGSERIAL PRIMARY KEY,
  auction_id BIGINT NOT NULL REFERENCES auctions(id),
  bidder_id BIGINT NOT NULL REFERENCES users(id),
  price NUMERIC(12,2) NOT NULL,
  pathway VARCHAR(40) NOT NULL,
  recovery_capability INT NOT NULL,
  environmental_performance INT NOT NULL,
  recovery_plan VARCHAR(500),
  circular_bid_score NUMERIC(9,3) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE score_config (
  id BIGSERIAL PRIMARY KEY,
  config_key VARCHAR(60) NOT NULL UNIQUE,
  config_value NUMERIC(9,3) NOT NULL,
  description VARCHAR(300)
);

CREATE TABLE hazard_rules (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(60) NOT NULL UNIQUE,
  description VARCHAR(300) NOT NULL,
  points INT NOT NULL,
  blocks_shipping BOOLEAN NOT NULL DEFAULT false,
  requires_sanitization BOOLEAN NOT NULL DEFAULT false,
  handling VARCHAR(500) NOT NULL,
  specialist_type VARCHAR(100),
  enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE hazard_inspections (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  inspector_id BIGINT REFERENCES users(id),
  answers TEXT NOT NULL,
  total_points INT NOT NULL,
  level VARCHAR(40) NOT NULL,
  explanation TEXT NOT NULL,
  handling TEXT,
  blocked BOOLEAN NOT NULL DEFAULT false,
  specialist VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sanitization_records (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  submitted_by BIGINT NOT NULL REFERENCES users(id),
  method VARCHAR(80) NOT NULL,
  tool_name VARCHAR(120),
  evidence_ref VARCHAR(300),
  status VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
  verified_by BIGINT REFERENCES users(id),
  verified_at TIMESTAMPTZ,
  certificate_no VARCHAR(40) UNIQUE,
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE token_revocations (
  id BIGSERIAL PRIMARY KEY,
  jti VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  sender_id BIGINT NOT NULL REFERENCES users(id),
  recipient_id BIGINT NOT NULL REFERENCES users(id),
  body VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- Indexes
CREATE INDEX idx_listings_seller_id ON listings(seller_id);
CREATE INDEX idx_listings_category_id ON listings(category_id);
CREATE INDEX idx_listings_brand ON listings(brand);
CREATE INDEX idx_listings_pickup_city ON listings(pickup_city);
CREATE INDEX idx_listings_status ON listings(status);
CREATE INDEX idx_listing_images_listing_id ON listing_images(listing_id);
CREATE INDEX idx_offers_listing_id ON offers(listing_id);
CREATE INDEX idx_offers_buyer_id ON offers(buyer_id);
CREATE INDEX idx_orders_listing_id ON orders(listing_id);
CREATE INDEX idx_orders_buyer_id ON orders(buyer_id);
CREATE INDEX idx_orders_seller_id ON orders(seller_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_order_id ON refunds(order_id);
CREATE INDEX idx_disputes_order_id ON disputes(order_id);
CREATE INDEX idx_disputes_raised_by ON disputes(raised_by);
CREATE INDEX idx_disputes_resolver_id ON disputes(resolver_id);
CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_dispute_assignments_dispute_id ON dispute_assignments(dispute_id);
CREATE INDEX idx_dispute_evidence_dispute_id ON dispute_evidence(dispute_id);
CREATE INDEX idx_dispute_messages_dispute_id ON dispute_messages(dispute_id);
CREATE INDEX idx_dispute_resolutions_resolver_id ON dispute_resolutions(resolver_id);
CREATE INDEX idx_reviews_order_id ON reviews(order_id);
CREATE INDEX idx_reviews_reviewee_id ON reviews(reviewee_id);
CREATE INDEX idx_wishlists_user_id ON wishlists(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_environmental_impact_listing_id ON environmental_impact(listing_id);
CREATE INDEX idx_environmental_impact_seller_id ON environmental_impact(seller_id);
CREATE INDEX idx_environmental_impact_buyer_id ON environmental_impact(buyer_id);
CREATE INDEX idx_product_lifecycle_listing_id ON product_lifecycle(listing_id);
CREATE INDEX idx_buyer_requirements_buyer_id ON buyer_requirements(buyer_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_passports_parent_id ON passports(parent_id);
CREATE INDEX idx_passports_current_owner_id ON passports(current_owner_id);
CREATE INDEX idx_passport_events_passport_id ON passport_events(passport_id);
CREATE INDEX idx_auctions_ends_at ON auctions(ends_at);
CREATE INDEX idx_auctions_status ON auctions(status);
CREATE INDEX idx_bids_auction_id ON bids(auction_id);
CREATE INDEX idx_bids_bidder_id ON bids(bidder_id);
CREATE INDEX idx_hazard_inspections_listing_id ON hazard_inspections(listing_id);
CREATE INDEX idx_sanitization_records_listing_id ON sanitization_records(listing_id);
CREATE INDEX idx_sanitization_records_status ON sanitization_records(status);
CREATE INDEX idx_chat_messages_listing_id ON chat_messages(listing_id);
CREATE INDEX idx_chat_messages_recipient_id ON chat_messages(recipient_id);
CREATE INDEX idx_bids_auction_price ON bids(auction_id, price DESC);
CREATE INDEX idx_listings_category_status ON listings(category_id, status);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);



-- Integrity constraints
ALTER TABLE listings ADD CONSTRAINT ck_listing_qty CHECK(quantity>0), ADD CONSTRAINT ck_listing_price CHECK(expected_price>=0);
ALTER TABLE offers ADD CONSTRAINT ck_offer_amount CHECK(amount>0);
ALTER TABLE orders ADD CONSTRAINT ck_order_amount CHECK(amount>=0);
CREATE UNIQUE INDEX uq_orders_active_listing ON orders(listing_id) WHERE status NOT IN ('CANCELLED','REFUNDED');
CREATE UNIQUE INDEX uq_payments_provider_payment ON payments(provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE UNIQUE INDEX uq_disputes_active_order ON disputes(order_id) WHERE status NOT IN ('RESOLVED','REJECTED');
ALTER TABLE reviews ADD CONSTRAINT uq_review UNIQUE(order_id, reviewer_id), ADD CONSTRAINT ck_review_rating CHECK(rating BETWEEN 1 AND 5);
ALTER TABLE wishlists ADD CONSTRAINT uq_wishlist UNIQUE(user_id, listing_id);
ALTER TABLE bids ADD CONSTRAINT ck_bid_price CHECK(price>0), ADD CONSTRAINT ck_bid_rec CHECK(recovery_capability BETWEEN 0 AND 100), ADD CONSTRAINT ck_bid_env CHECK(environmental_performance BETWEEN 0 AND 100);
