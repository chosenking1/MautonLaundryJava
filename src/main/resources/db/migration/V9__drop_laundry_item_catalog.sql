-- Remove the unused laundry_item_catalog feature.
-- It was a self-contained CRUD island (model/repo/service/controller at
-- /api/v1/laundry-items) never referenced by booking pricing, which is driven
-- entirely by service_pricing. No client app consumed it. The Java code has
-- been deleted; this drops the orphaned table (Hibernate ddl-auto created it;
-- there was no prior migration or seed data).

DROP TABLE IF EXISTS laundry_item_catalog;
