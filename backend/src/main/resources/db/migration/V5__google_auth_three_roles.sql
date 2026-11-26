-- Google sign-in: link an account to its Google subject id (never store Google tokens).
ALTER TABLE users ADD COLUMN google_sub VARCHAR(64);
CREATE UNIQUE INDEX uq_users_google_sub ON users(google_sub) WHERE google_sub IS NOT NULL;

-- Three roles only (SELLER, BUYER, RESOLVER): former admins become resolvers, then the ADMIN role is removed.
INSERT INTO user_roles(user_id, role_id)
SELECT DISTINCT ur.user_id, (SELECT id FROM roles WHERE name = 'RESOLVER')
FROM user_roles ur JOIN roles r ON r.id = ur.role_id
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = ur.user_id AND x.role_id = (SELECT id FROM roles WHERE name = 'RESOLVER'));
DELETE FROM user_roles WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');
DELETE FROM roles WHERE name = 'ADMIN';
