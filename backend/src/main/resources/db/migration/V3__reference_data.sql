INSERT INTO roles(name) VALUES ('SELLER'),('BUYER'),('RESOLVER'),('ADMIN');

-- name, description, repairability_base, component_recovery_base, life_months, default_weight_kg, dismantlable
INSERT INTO product_categories(name,description,repairability_base,component_recovery_base,life_months,default_weight_kg,dismantlable) VALUES
 ('Smartphones','Mobile phones',55,45,48,0.2,false),
 ('Laptops','Notebook computers',70,70,72,2.0,true),
 ('Tablets','Tablet computers',50,45,60,0.5,false),
 ('Desktop computers','Towers and all-in-ones',85,80,96,8.0,true),
 ('Monitors','Displays and TVs',60,50,84,4.0,false),
 ('Batteries','Battery packs and cells',10,60,36,0.4,false),
 ('Circuit boards','Bare or populated PCBs',35,90,120,0.3,false),
 ('Chargers','Chargers and adapters',40,30,60,0.2,false),
 ('Printers','Printers and scanners',65,45,96,6.0,true),
 ('Electronic components','Loose parts: RAM, SSD, GPUs, modules',50,85,120,0.1,false),
 ('Other electronic waste','Anything else',30,30,60,1.0,false);

-- Conversion factors are ESTIMATES (kg CO2e avoided per kg of product handled). Admins can edit them; see docs/FORMULAS.md.
INSERT INTO environmental_factors(category_id,carbon_reuse_per_kg,carbon_repair_per_kg,carbon_refurbish_per_kg,carbon_recycle_per_kg,metals_recovery_pct,source_note)
SELECT id, v.reuse, v.repair, v.refurb, v.recycle, v.metals, 'Default planning estimates; replace with audited values (e.g. EPA WARM / ecoinvent) before reporting externally.'
FROM product_categories c JOIN (VALUES
 ('Smartphones',60.0,45.0,50.0,12.0,18.0),('Laptops',35.0,28.0,30.0,9.0,22.0),('Tablets',40.0,30.0,33.0,10.0,20.0),
 ('Desktop computers',18.0,14.0,15.0,5.0,28.0),('Monitors',8.0,6.0,7.0,2.5,12.0),('Batteries',2.0,1.0,1.5,6.0,35.0),
 ('Circuit boards',15.0,10.0,12.0,25.0,45.0),('Chargers',9.0,6.0,7.0,3.0,30.0),('Printers',6.0,4.5,5.0,2.0,15.0),
 ('Electronic components',30.0,22.0,25.0,14.0,40.0),('Other electronic waste',5.0,3.5,4.0,2.0,15.0)
) AS v(name,reuse,repair,refurb,recycle,metals) ON v.name=c.name;

-- Circular Value Score / Circular Bid Score configuration (weights sum to 1.0 within each group)
INSERT INTO score_config(config_key,config_value,description) VALUES
 ('w.functionality',0.25,'Circular Value Score weight: functionality'),
 ('w.condition',0.20,'Circular Value Score weight: physical condition'),
 ('w.repairability',0.15,'Circular Value Score weight: repairability'),
 ('w.reusability',0.15,'Circular Value Score weight: reusability'),
 ('w.component',0.10,'Circular Value Score weight: component recovery potential'),
 ('w.life',0.15,'Circular Value Score weight: remaining useful life'),
 ('t.reuse.overall',75,'REUSE if overall >= this'),
 ('t.reuse.functionality',85,'REUSE requires functionality >= this'),
 ('t.refurbish.overall',60,'REFURBISH if overall >= this'),
 ('t.refurbish.condition',50,'REFURBISH requires condition >= this'),
 ('t.refurbish.repair',50,'REFURBISH requires repairability >= this'),
 ('t.repair.overall',45,'REPAIR if overall >= this'),
 ('t.repair.repair',55,'REPAIR requires repairability >= this'),
 ('t.recycle.component',25,'RECYCLE if component recovery >= this, otherwise DISPOSE'),
 ('bid.w.price',0.40,'Circular Bid Score weight: offered price'),
 ('bid.w.verification',0.20,'Circular Bid Score weight: buyer verification'),
 ('bid.w.recovery',0.20,'Circular Bid Score weight: recovery capability'),
 ('bid.w.environment',0.20,'Circular Bid Score weight: environmental performance'),
 ('auction.default_hours',72,'Default reverse auction duration in hours');

-- Hazard rules. Points are summed; level thresholds: <15 LOW, <35 MODERATE, <60 HIGH, else CRITICAL. A blocking rule forces at least HIGH.
INSERT INTO hazard_rules(code,description,points,blocks_shipping,requires_sanitization,handling,specialist_type) VALUES
 ('BATTERY_SWELLING','Battery is swollen, bulging or deformed',40,true,false,'Do not charge, puncture, press or ship. Isolate in a non-flammable container with sand or vermiculite, away from combustibles. Hand over to a certified lithium-battery handler.','Certified battery recycler'),
 ('OVERHEATING','Device overheats, smells burnt or shuts down from heat',25,false,false,'Keep unplugged and ventilated. Do not charge until inspected. Have the battery and charging circuit checked by a technician.','Repair technician or battery recycler'),
 ('LIQUID_DAMAGE','Liquid ingress or corrosion visible',20,false,false,'Power off, remove battery if safely possible, do not power on. Dry and inspect for corrosion before any resale.','Repair technician'),
 ('PHYSICAL_DAMAGE','Cracked casing or screen, exposed internals',10,false,false,'Wrap sharp edges and protect against short circuits. Package in rigid padding.','Standard handler with care'),
 ('HAZARDOUS_COMPONENTS','Contains CRT, mercury lamps, leaking capacitors or other regulated hazardous parts',30,true,false,'Do not dismantle outdoors or at home. Use an authorised e-waste facility with regulated hazardous-material handling.','Authorised hazardous e-waste recycler'),
 ('STORAGE_DEVICE_PRESENT','Device contains storage (SSD/HDD/flash/phone memory) that may hold personal data',5,false,true,'Verified data sanitization is required before any resale or reuse. Use a recognised erase tool (e.g. NIST SP 800-88 purge/destroy) and record the tool report.','Data sanitization provider');
