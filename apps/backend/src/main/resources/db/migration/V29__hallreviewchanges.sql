-- ALTER TABLE reviews
-- DROP CONSTRAINT uq_review_per_enquiry;

ALTER TABLE reviews
ADD CONSTRAINT uq_review_per_customer_hall
UNIQUE (customer_user_id, hall_id);