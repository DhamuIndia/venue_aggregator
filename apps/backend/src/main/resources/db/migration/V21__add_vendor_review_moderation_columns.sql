ALTER TABLE vendor_reviews
ADD COLUMN moderation_reason VARCHAR(500);

ALTER TABLE vendor_reviews
ADD COLUMN moderated_by_admin_id BIGINT;

ALTER TABLE vendor_reviews
ADD COLUMN moderated_at TIMESTAMP;

ALTER TABLE vendor_reviews
ADD CONSTRAINT fk_vendor_review_admin
FOREIGN KEY (moderated_by_admin_id)
REFERENCES admins(id);
