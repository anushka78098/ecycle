-- Append-only guarantees: evidence, audit trail, bids, lifecycle, passport events, inspections, dispute messages and resolutions
-- can never be updated or deleted, not even by application bugs or direct SQL by the application role.
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'Table % is append-only: % is not permitted', TG_TABLE_NAME, TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_immutable_dispute_evidence BEFORE UPDATE OR DELETE ON dispute_evidence FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_dispute_messages BEFORE UPDATE OR DELETE ON dispute_messages FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_dispute_resolutions BEFORE UPDATE OR DELETE ON dispute_resolutions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_audit_logs BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_bids BEFORE UPDATE OR DELETE ON bids FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_lifecycle BEFORE UPDATE OR DELETE ON product_lifecycle FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_passport_events BEFORE UPDATE OR DELETE ON passport_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_hazard_inspections BEFORE UPDATE OR DELETE ON hazard_inspections FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_immutable_env_impact BEFORE UPDATE OR DELETE ON environmental_impact FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
