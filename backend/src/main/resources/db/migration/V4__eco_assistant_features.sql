-- V4: recycler map and pickups, QR chain of custody, certificates, sanitization checklists, eco-points gamification.
-- NOTE: chat messages, notifications and chatbot history now live in MongoDB. The legacy PostgreSQL tables chat_messages and notifications
-- from V1 are kept untouched (read once by LegacyMongoMigrator on first start, then no longer written).

-- ===================== recycler locations & pickups =====================
CREATE TABLE recycler_locations (
  id BIGSERIAL PRIMARY KEY,
  recycler_id BIGINT NOT NULL REFERENCES users(id),
  facility_name VARCHAR(150) NOT NULL,
  address VARCHAR(250) NOT NULL,
  city VARCHAR(80) NOT NULL,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  phone VARCHAR(30),
  opening_hours VARCHAR(150),
  accepted_categories VARCHAR(300),
  certifications VARCHAR(200),
  service_radius_km INT NOT NULL DEFAULT 25,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_loc_lat CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_loc_lon CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_loc_radius CHECK (service_radius_km BETWEEN 1 AND 500)
);
CREATE INDEX idx_recycler_locations_recycler ON recycler_locations(recycler_id);
CREATE INDEX idx_recycler_locations_geo ON recycler_locations(latitude, longitude) WHERE active;

CREATE TABLE pickups (
  id BIGSERIAL PRIMARY KEY,
  tracking_code VARCHAR(20) NOT NULL UNIQUE,
  requester_id BIGINT NOT NULL REFERENCES users(id),
  recycler_id BIGINT NOT NULL REFERENCES users(id),
  location_id BIGINT NOT NULL REFERENCES recycler_locations(id),
  listing_id BIGINT REFERENCES listings(id),
  passport_code VARCHAR(40) REFERENCES passports(passport_code),
  category_id BIGINT REFERENCES product_categories(id),
  items_description VARCHAR(500) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  est_weight_kg NUMERIC(9,3) NOT NULL,
  actual_weight_kg NUMERIC(9,3),
  data_bearing BOOLEAN NOT NULL DEFAULT false,
  hazardous BOOLEAN NOT NULL DEFAULT false,
  pickup_address VARCHAR(300) NOT NULL,
  pickup_lat DOUBLE PRECISION,
  pickup_lng DOUBLE PRECISION,
  contact_phone VARCHAR(30),
  preferred_at TIMESTAMPTZ NOT NULL,
  scheduled_at TIMESTAMPTZ,
  status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
  status_note VARCHAR(300),
  last_lat DOUBLE PRECISION,
  last_lng DOUBLE PRECISION,
  last_ping_at TIMESTAMPTZ,
  est_carbon_kg NUMERIC(12,3) NOT NULL DEFAULT 0,
  treatment VARCHAR(80),
  verification_note VARCHAR(500),
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_pickup_qty CHECK (quantity > 0),
  CONSTRAINT ck_pickup_weight CHECK (est_weight_kg > 0)
);
CREATE INDEX idx_pickups_requester ON pickups(requester_id);
CREATE INDEX idx_pickups_recycler ON pickups(recycler_id, status);
CREATE INDEX idx_pickups_status ON pickups(status);
CREATE UNIQUE INDEX uq_pickups_active_listing ON pickups(listing_id) WHERE listing_id IS NOT NULL AND status NOT IN ('COMPLETED','REJECTED','CANCELLED');

CREATE TABLE pickup_events (
  id BIGSERIAL PRIMARY KEY,
  pickup_id BIGINT NOT NULL REFERENCES pickups(id),
  status VARCHAR(30) NOT NULL,
  actor_id BIGINT REFERENCES users(id),
  note VARCHAR(300),
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pickup_events_pickup ON pickup_events(pickup_id);

-- ===================== QR chain of custody (tamper-evident) =====================
CREATE TABLE custody_events (
  id BIGSERIAL PRIMARY KEY,
  asset_code VARCHAR(40) NOT NULL REFERENCES passports(passport_code),
  seq INT NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  actor_id BIGINT,
  actor_role VARCHAR(30),
  custodian_id BIGINT,
  location VARCHAR(200),
  detail VARCHAR(500),
  occurred_at TIMESTAMPTZ NOT NULL,
  prev_hash VARCHAR(64) NOT NULL,
  hash VARCHAR(64) NOT NULL UNIQUE,
  CONSTRAINT uq_custody_asset_seq UNIQUE (asset_code, seq),
  CONSTRAINT ck_custody_seq CHECK (seq >= 1)
);
CREATE INDEX idx_custody_asset ON custody_events(asset_code, seq);
CREATE INDEX idx_custody_custodian ON custody_events(custodian_id);

-- ===================== certificates =====================
CREATE TABLE certificates (
  id BIGSERIAL PRIMARY KEY,
  certificate_no VARCHAR(40) NOT NULL UNIQUE,
  type VARCHAR(30) NOT NULL,
  verification_code VARCHAR(20) NOT NULL,
  listing_id BIGINT REFERENCES listings(id),
  pickup_id BIGINT REFERENCES pickups(id),
  sanitization_record_id BIGINT REFERENCES sanitization_records(id),
  passport_code VARCHAR(40),
  holder_id BIGINT NOT NULL REFERENCES users(id),
  issued_by BIGINT NOT NULL REFERENCES users(id),
  payload TEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  signature VARCHAR(64) NOT NULL,
  custody_seq INT,
  custody_head_hash VARCHAR(64),
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_certificates_holder ON certificates(holder_id);
CREATE INDEX idx_certificates_listing ON certificates(listing_id);
CREATE UNIQUE INDEX uq_cert_sanitization ON certificates(sanitization_record_id) WHERE sanitization_record_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cert_pickup_type ON certificates(pickup_id, type) WHERE pickup_id IS NOT NULL;

-- ===================== data-wiping checklists =====================
CREATE TABLE sanitization_checklists (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT REFERENCES listings(id),
  record_id BIGINT REFERENCES sanitization_records(id),
  pickup_id BIGINT REFERENCES pickups(id),
  completed_by BIGINT NOT NULL REFERENCES users(id),
  completed_role VARCHAR(30) NOT NULL,
  method VARCHAR(80) NOT NULL,
  device_serial VARCHAR(80),
  items TEXT NOT NULL,
  all_mandatory_done BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_checklists_listing ON sanitization_checklists(listing_id);
CREATE INDEX idx_checklists_pickup ON sanitization_checklists(pickup_id);

-- ===================== eco-points & gamification =====================
CREATE TABLE eco_accounts (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  balance INT NOT NULL DEFAULT 0,
  lifetime_points INT NOT NULL DEFAULT 0,
  show_on_leaderboard BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_eco_balance CHECK (balance >= 0)
);

CREATE TABLE eco_ledger (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  delta INT NOT NULL,
  activity VARCHAR(40) NOT NULL,
  ref_type VARCHAR(30),
  ref_id BIGINT,
  weight_kg NUMERIC(12,3) NOT NULL DEFAULT 0,
  carbon_kg NUMERIC(12,3) NOT NULL DEFAULT 0,
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_eco_ledger_user ON eco_ledger(user_id, created_at);
CREATE UNIQUE INDEX uq_eco_ledger_ref ON eco_ledger(user_id, activity, ref_type, ref_id) WHERE ref_id IS NOT NULL;

CREATE TABLE badges (
  code VARCHAR(40) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(300) NOT NULL,
  criteria VARCHAR(30) NOT NULL,
  threshold NUMERIC(12,3) NOT NULL,
  icon VARCHAR(40),
  sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE user_badges (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  badge_code VARCHAR(40) NOT NULL REFERENCES badges(code),
  awarded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_user_badge UNIQUE (user_id, badge_code)
);

CREATE TABLE challenges (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(300) NOT NULL,
  metric VARCHAR(30) NOT NULL,
  target NUMERIC(12,3) NOT NULL,
  reward_points INT NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ends_at TIMESTAMPTZ,
  active BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT ck_challenge_target CHECK (target > 0),
  CONSTRAINT ck_challenge_reward CHECK (reward_points >= 0)
);
CREATE TABLE challenge_completions (
  id BIGSERIAL PRIMARY KEY,
  challenge_id BIGINT NOT NULL REFERENCES challenges(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  bonus_points INT NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_challenge_user UNIQUE (challenge_id, user_id)
);

CREATE TABLE rewards (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(300) NOT NULL,
  cost_points INT NOT NULL,
  stock INT,
  active BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT ck_reward_cost CHECK (cost_points > 0),
  CONSTRAINT ck_reward_stock CHECK (stock IS NULL OR stock >= 0)
);
CREATE TABLE reward_redemptions (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  reward_id BIGINT NOT NULL REFERENCES rewards(id),
  points_spent INT NOT NULL,
  voucher_code VARCHAR(30) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_redemptions_user ON reward_redemptions(user_id);

-- ===================== append-only guarantees (same trigger function as V2) =====================
CREATE TRIGGER trg_immutable_custody BEFORE UPDATE OR DELETE ON custody_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_certificates BEFORE UPDATE OR DELETE ON certificates FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_pickup_events BEFORE UPDATE OR DELETE ON pickup_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_checklists BEFORE UPDATE OR DELETE ON sanitization_checklists FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_eco_ledger BEFORE UPDATE OR DELETE ON eco_ledger FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ===================== reference data =====================
INSERT INTO score_config(config_key, config_value, description) VALUES
 ('eco.pts.per_kg.reuse', 12, 'Eco-points per kg when a product is reused'),
 ('eco.pts.per_kg.repair', 10, 'Eco-points per kg when a product is repaired'),
 ('eco.pts.per_kg.refurbish', 9, 'Eco-points per kg when a product is refurbished'),
 ('eco.pts.per_kg.recycle', 6, 'Eco-points per kg when a product is recycled'),
 ('eco.pts.pickup_completed', 40, 'Base eco-points for a recycler-verified pickup (plus per-kg points)'),
 ('eco.pts.hazard_bonus', 20, 'Bonus eco-points when a hazardous item goes to a specialist recycler'),
 ('eco.pts.sanitization', 25, 'Eco-points for a verified data-sanitization certificate'),
 ('eco.pts.recycler_share', 0.5, 'Fraction of the pickup points also credited to the recycler');

INSERT INTO badges(code, name, description, criteria, threshold, icon, sort_order) VALUES
 ('FIRST_PICKUP', 'First Pickup', 'Completed your first verified recycling pickup', 'PICKUPS_COMPLETED', 1, 'truck', 1),
 ('PICKUP_PRO', 'Pickup Pro', 'Five verified recycling pickups', 'PICKUPS_COMPLETED', 5, 'truck', 2),
 ('SECURE_WIPER', 'Secure Wiper', 'Earned a verified data-sanitization certificate', 'SANITIZATIONS', 1, 'shield', 3),
 ('CARBON_10', 'Carbon Cutter', 'Avoided 10 kg CO2e', 'CARBON_KG', 10, 'leaf', 4),
 ('CARBON_100', 'Climate Hero', 'Avoided 100 kg CO2e', 'CARBON_KG', 100, 'leaf', 5),
 ('WEIGHT_25', 'Heavy Lifter', 'Diverted 25 kg of e-waste', 'WEIGHT_KG', 25, 'weight', 6),
 ('CIRCULAR_3', 'Circular Thinker', 'Three reuse / repair / refurbish / recycle outcomes', 'CIRCULAR_ACTIONS', 3, 'recycle', 7),
 ('POINTS_500', 'Eco Enthusiast', 'Earned 500 lifetime eco-points', 'LIFETIME_POINTS', 500, 'star', 8),
 ('CHALLENGER', 'Challenger', 'Completed a challenge', 'CHALLENGES', 1, 'trophy', 9);

INSERT INTO challenges(code, title, description, metric, target, reward_points) VALUES
 ('CH_FIRST_PICKUP', 'Kick-start', 'Complete one verified recycling pickup', 'PICKUPS_COMPLETED', 1, 50),
 ('CH_CARBON_25', 'Carbon saver', 'Avoid 25 kg CO2e through your e-waste', 'CARBON_KG', 25, 100),
 ('CH_WEIGHT_20', 'Weight watcher', 'Divert 20 kg of e-waste from landfill', 'WEIGHT_KG', 20, 100),
 ('CH_CIRCULAR_3', 'Circular streak', 'Record three reuse, repair, refurbish or recycle outcomes', 'CIRCULAR_ACTIONS', 3, 75);

INSERT INTO rewards(code, title, description, cost_points, stock) VALUES
 ('PLANT_TREE', 'Plant a tree', 'We fund the planting of one tree in your name', 250, NULL),
 ('FREE_PICKUP', 'Free pickup credit', 'Waives pickup handling fees on your next recycling pickup', 150, NULL),
 ('PRIORITY_SLOT', 'Priority pickup slot', 'Request priority scheduling on one pickup', 200, NULL),
 ('REPAIR_VOUCHER', 'Repair voucher (INR 100)', 'INR 100 off at a participating repair shop', 500, 100),
 ('ECO_TOTE', 'Eco tote bag', 'Recycled-material tote bag, collected at a partner facility', 300, 50);
