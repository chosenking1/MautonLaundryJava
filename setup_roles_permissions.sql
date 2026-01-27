-- SQL Script to populate Roles and Permissions tables for Imototo Laundry System

-- Insert Permissions
INSERT INTO permissions ( name, description, resource, action) VALUES
( 'USER_CREATE', 'Create users', 'USER', 'CREATE'),
( 'USER_READ', 'Read users', 'USER', 'READ'),
( 'USER_UPDATE', 'Update users', 'USER', 'UPDATE'),
( 'USER_DELETE', 'Delete users', 'USER', 'DELETE'),
( 'BOOKING_CREATE', 'Create bookings', 'BOOKING', 'CREATE'),
( 'BOOKING_READ', 'Read bookings', 'BOOKING', 'READ'),
( 'BOOKING_UPDATE', 'Update bookings', 'BOOKING', 'UPDATE'),
( 'BOOKING_DELETE', 'Delete bookings', 'BOOKING', 'DELETE'),
( 'PAYMENT_CREATE', 'Create payments', 'PAYMENT', 'CREATE'),
( 'PAYMENT_READ', 'Read payments', 'PAYMENT', 'READ'),
( 'PAYMENT_UPDATE', 'Update payments', 'PAYMENT', 'UPDATE'),
( 'DELIVERY_READ', 'Read deliveries', 'DELIVERY', 'READ'),
( 'DELIVERY_UPDATE', 'Update deliveries', 'DELIVERY', 'UPDATE'),
( 'ANALYTICS_READ', 'Read analytics', 'ANALYTICS', 'READ');

-- Insert Roles
INSERT INTO roles ( name, description) VALUES
( 'ADMIN', 'Administrator with full access'),
('USER', 'Regular customer'),
( 'LAUNDRY_AGENT', 'Laundry processing agent'),
( 'DELIVERY_AGENT', 'Delivery agent');


ALTER TABLE permissions
    ADD CONSTRAINT uc_permissions_name UNIQUE (name);
-- Assign Permissions to ADMIN Role (All permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.name = 'ADMIN';

-- Assign Permissions to USER Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.name = 'USER' 
AND p.name IN ('BOOKING_CREATE', 'BOOKING_READ', 'PAYMENT_READ');

-- Assign Permissions to LAUNDRY_AGENT Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.name = 'LAUNDRY_AGENT' 
AND p.name IN ('BOOKING_READ', 'BOOKING_UPDATE', 'DELIVERY_READ');

-- Assign Permissions to DELIVERY_AGENT Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id 
FROM roles r, permissions p 
WHERE r.name = 'DELIVERY_AGENT' 
AND p.name IN ('DELIVERY_READ', 'DELIVERY_UPDATE', 'BOOKING_READ');

-- Verify the data
SELECT 'Permissions Count' as Info, COUNT(*) as Count FROM permissions
UNION ALL
SELECT 'Roles Count', COUNT(*) FROM roles
UNION ALL
SELECT 'Role-Permission Mappings', COUNT(*) FROM role_permissions;