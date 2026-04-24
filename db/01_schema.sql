-- =============================================================
--  LivAna Platform — PostgreSQL 16 Schema
--  Version : 2.0  (Updated after UI flow analysis)
--  Created  : April 2026
-- =============================================================
--  CHANGES FROM v1.0
--  + cities table (3NF fix — city was duplicated in businesses & listings)
--  + amenities table + listing_amenities junction (PG amenities: WiFi, AC etc.)
--  + business_hours table (from business registration UI)
--  + bookings table (PG is a booking, not an order — different lifecycle)
--  + listing_metadata JSONB column (type-specific fields per business type)
--  + business_settings table (notification prefs, payment info from settings UI)
--  + review_category_scores table (category-wise breakdown seen in review UI)
--  REMOVED
--  - city VARCHAR from listings (now derived via businesses.city_id JOIN — 3NF fix)
-- =============================================================

--  TABLE ORDER
--  1.  cities
--  2.  users
--  3.  businesses
--  4.  business_hours
--  5.  business_settings
--  6.  amenities
--  7.  listings
--  8.  listing_amenities
--  9.  orders
--  10. order_items
--  11. bookings
--  12. reviews
--  13. review_category_scores
--  14. notifications
-- =============================================================


-- =============================================================
--  ENUMS
-- =============================================================

CREATE TYPE user_role AS ENUM (
    'STUDENT',
    'BUSINESS_OWNER',
    'ADMIN'
);

CREATE TYPE business_type AS ENUM (
    'PG',
    'MESS',
    'GROCERY'
);

CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'PREPARING',
    'READY',
    'DELIVERED',
    'CANCELLED'
);

CREATE TYPE booking_status AS ENUM (
    'PENDING',       -- student applied, owner has not responded
    'CONFIRMED',     -- owner accepted
    'ACTIVE',        -- student is currently staying
    'COMPLETED',     -- stay ended normally
    'CANCELLED'      -- cancelled by either party
);

CREATE TYPE day_of_week AS ENUM (
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY'
);

CREATE TYPE notification_type AS ENUM (
    'ORDER_PLACED',
    'ORDER_CONFIRMED',
    'ORDER_PREPARING',
    'ORDER_READY',
    'ORDER_DELIVERED',
    'ORDER_CANCELLED',
    'BOOKING_REQUEST',
    'BOOKING_CONFIRMED',
    'BOOKING_CANCELLED',
    'REVIEW_RECEIVED',
    'PROMOTIONAL'
);


-- =============================================================
--  TABLE 1 : cities
--  3NF fix: city was duplicated across businesses and listings.
--  Single source of truth here. All other tables reference city_id.
-- =============================================================

CREATE TABLE cities (
    id         BIGSERIAL     PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL UNIQUE,
    state      VARCHAR(100)  NOT NULL
);

-- Seed with your target launch cities:
-- INSERT INTO cities (name, state) VALUES ('Dehradun', 'Uttarakhand');
-- INSERT INTO cities (name, state) VALUES ('Roorkee',  'Uttarakhand');


-- =============================================================
--  TABLE 2 : users
-- =============================================================

CREATE TABLE users (
    id           BIGSERIAL      PRIMARY KEY,
    google_id    VARCHAR(100)   NOT NULL UNIQUE,
    email        VARCHAR(255)   NOT NULL UNIQUE,
    full_name    VARCHAR(100)   NOT NULL,
    phone        VARCHAR(15),
    avatar_url   TEXT,
    city_id      BIGINT         REFERENCES cities(id),   -- student's current city
    role         user_role      NOT NULL DEFAULT 'STUDENT',
    is_active    BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Notes:
--   google_id is the true identity anchor — email alone can change.
--   city_id pre-filters listings on first open (student location from registration).
--   is_active = FALSE for soft bans — never hard delete users.


-- =============================================================
--  TABLE 3 : businesses
-- =============================================================

CREATE TABLE businesses (
    id             BIGSERIAL       PRIMARY KEY,
    owner_id       BIGINT          NOT NULL REFERENCES users(id),
    city_id        BIGINT          NOT NULL REFERENCES cities(id),
    name           VARCHAR(150)    NOT NULL,
    type           business_type   NOT NULL,
    description    TEXT,
    address        TEXT            NOT NULL,
    latitude       DECIMAL(9, 6),
    longitude      DECIMAL(9, 6),
    cover_url      TEXT,
    phone          VARCHAR(15),
    rating         DECIMAL(2, 1)   NOT NULL DEFAULT 0.0,
    total_reviews  INT             NOT NULL DEFAULT 0,
    is_open        BOOLEAN         NOT NULL DEFAULT TRUE,
    is_verified    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Notes:
--   city_id FK replaces raw VARCHAR city (3NF fix).
--   rating + total_reviews are intentional denormalisations for read performance.
--   Recalculate both atomically in a transaction on every review INSERT/DELETE.
--   Formula: rating = ROUND(AVG(rating),1), total_reviews = COUNT(*) WHERE business_id = X
--   is_verified = TRUE only after ADMIN approval.


-- =============================================================
--  TABLE 4 : business_hours
--  Seen in business registration UI — owners set hours per day.
-- =============================================================

CREATE TABLE business_hours (
    id            BIGSERIAL     PRIMARY KEY,
    business_id   BIGINT        NOT NULL REFERENCES businesses(id),
    day           day_of_week   NOT NULL,
    open_time     TIME,                        -- NULL when is_closed = TRUE
    close_time    TIME,                        -- NULL when is_closed = TRUE
    is_closed     BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_business_day UNIQUE (business_id, day)
);

-- Notes:
--   7 rows per business (one per day of week).
--   is_closed = TRUE for weekly off (e.g. Sundays closed).
--   Enforce: if is_closed = TRUE then open_time and close_time must be NULL.


-- =============================================================
--  TABLE 5 : business_settings
--  From the Settings screen in Business Flow.
--  Notification toggles + payment onboarding info.
-- =============================================================

CREATE TABLE business_settings (
    id                       BIGSERIAL    PRIMARY KEY,
    business_id              BIGINT       NOT NULL UNIQUE REFERENCES businesses(id),
    notify_new_order         BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_new_review        BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_booking_request   BOOLEAN      NOT NULL DEFAULT TRUE,
    razorpay_account_id      VARCHAR(100),        -- nullable until payment onboarded
    payment_onboarded        BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Notes:
--   UNIQUE on business_id — exactly one settings row per business.
--   Auto-create this row with defaults when a business is first registered.
--   Never store full payment credentials here — only the account reference ID.


-- =============================================================
--  TABLE 6 : amenities
--  Master list of amenities shown as checkboxes in PG listing UI.
--  e.g. WiFi, AC, Geyser, Laundry, Parking, CCTV
-- =============================================================

CREATE TABLE amenities (
    id          BIGSERIAL     PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    icon_name   VARCHAR(100)                    -- icon key for frontend renderer
);


-- =============================================================
--  TABLE 7 : listings
--  Single table for PG rooms, mess plans, and grocery items.
--  listing_metadata (JSONB) carries type-specific fields.
-- =============================================================

CREATE TABLE listings (
    id                BIGSERIAL        PRIMARY KEY,
    business_id       BIGINT           NOT NULL REFERENCES businesses(id),
    title             VARCHAR(200)     NOT NULL,
    description       TEXT,
    price             DECIMAL(10, 2)   NOT NULL,
    images            TEXT[],                         -- array of Cloudflare R2 URLs
    category          VARCHAR(100),
    is_available      BOOLEAN          NOT NULL DEFAULT TRUE,
    is_active         BOOLEAN          NOT NULL DEFAULT TRUE,
    listing_metadata  JSONB            NOT NULL DEFAULT '{}',
    created_at        TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- listing_metadata structure by business type:
--
--   PG listing:
--   {
--     "room_type"          : "SINGLE" | "DOUBLE" | "TRIPLE",
--     "gender_preference"  : "MALE" | "FEMALE" | "ANY",
--     "security_deposit"   : 5000,
--     "notice_period_days" : 30,
--     "total_rooms"        : 10,
--     "available_rooms"    : 3
--   }
--
--   MESS listing (meal plan):
--   {
--     "plan_duration" : "MONTHLY" | "WEEKLY",
--     "meal_types"    : ["BREAKFAST", "LUNCH", "DINNER"],
--     "is_veg_only"   : true
--   }
--
--   GROCERY listing:
--   {
--     "unit"           : "kg" | "piece" | "pack" | "litre",
--     "stock_quantity" : 50
--   }

-- Notes:
--   city column REMOVED — derive city by joining businesses.city_id (3NF fix).
--   is_available = owner toggled out of stock temporarily.
--   is_active    = soft deleted / permanently removed by owner.


-- =============================================================
--  TABLE 8 : listing_amenities
--  Junction table: many-to-many between listings and amenities.
--  Only populated for PG type listings.
-- =============================================================

CREATE TABLE listing_amenities (
    listing_id    BIGINT   NOT NULL REFERENCES listings(id),
    amenity_id    BIGINT   NOT NULL REFERENCES amenities(id),

    PRIMARY KEY (listing_id, amenity_id)
);


-- =============================================================
--  TABLE 9 : orders
--  For MESS and GROCERY purchases only.
--  PG accommodations use the bookings table (Table 11).
-- =============================================================

CREATE TABLE orders (
    id                BIGSERIAL       PRIMARY KEY,
    student_id        BIGINT          NOT NULL REFERENCES users(id),
    business_id       BIGINT          NOT NULL REFERENCES businesses(id),
    status            order_status    NOT NULL DEFAULT 'PENDING',
    total_amount      DECIMAL(10, 2)  NOT NULL,
    delivery_address  TEXT,
    note              VARCHAR(500),
    cancelled_by      BIGINT          REFERENCES users(id),
    cancel_reason     VARCHAR(500),
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);


-- =============================================================
--  TABLE 10 : order_items
-- =============================================================

CREATE TABLE order_items (
    id                BIGSERIAL        PRIMARY KEY,
    order_id          BIGINT           NOT NULL REFERENCES orders(id),
    listing_id        BIGINT           NOT NULL REFERENCES listings(id),
    title_snapshot    VARCHAR(200)     NOT NULL,       -- item name at time of order
    price_snapshot    DECIMAL(10, 2)   NOT NULL,       -- price at time of order
    quantity          INT              NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at        TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- Notes:
--   Snapshots are CRITICAL — owner price/name changes must not affect old orders.
--   Order items are immutable after creation — no updated_at column.


-- =============================================================
--  TABLE 11 : bookings
--  PG accommodation only. Different lifecycle from orders:
--  - Duration in months, not minutes
--  - One room per booking (no booking_items table needed)
--  - Has check_in / check_out dates
--  - Security deposit tracked separately
-- =============================================================

CREATE TABLE bookings (
    id                     BIGSERIAL        PRIMARY KEY,
    student_id             BIGINT           NOT NULL REFERENCES users(id),
    listing_id             BIGINT           NOT NULL REFERENCES listings(id),
    business_id            BIGINT           NOT NULL REFERENCES businesses(id),
    status                 booking_status   NOT NULL DEFAULT 'PENDING',
    monthly_rent_snapshot  DECIMAL(10, 2)   NOT NULL,
    security_deposit       DECIMAL(10, 2),
    check_in_date          DATE             NOT NULL,
    duration_months        INT              NOT NULL DEFAULT 1 CHECK (duration_months > 0),
    check_out_date         DATE,
    note                   VARCHAR(500),
    cancelled_by           BIGINT           REFERENCES users(id),
    cancel_reason          VARCHAR(500),
    created_at             TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- Notes:
--   monthly_rent_snapshot captures rent at booking time.
--   check_out_date = check_in_date + duration_months (compute in app layer).
--   Enforce in app: one student can only have one ACTIVE booking at a time.


-- =============================================================
--  TABLE 12 : reviews
-- =============================================================

CREATE TABLE reviews (
    id             BIGSERIAL    PRIMARY KEY,
    student_id     BIGINT       NOT NULL REFERENCES users(id),
    business_id    BIGINT       NOT NULL REFERENCES businesses(id),
    rating         SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_one_review_per_business UNIQUE (student_id, business_id)
);

-- Notes:
--   UNIQUE enforced at DB level — not just application layer.
--   On INSERT/DELETE: recalculate businesses.rating and businesses.total_reviews
--   in the same transaction.


-- =============================================================
--  TABLE 13 : review_category_scores
--  From UI: review screen shows per-category scores.
--  Categories differ by business type.
-- =============================================================

CREATE TABLE review_category_scores (
    id          BIGSERIAL      PRIMARY KEY,
    review_id   BIGINT         NOT NULL REFERENCES reviews(id),
    category    VARCHAR(50)    NOT NULL,
    score       SMALLINT       NOT NULL CHECK (score BETWEEN 1 AND 5),

    CONSTRAINT uq_review_category UNIQUE (review_id, category)
);

-- Category examples by business type:
--   MESS    : "Food", "Hygiene", "Service", "Value"
--   PG      : "Cleanliness", "Security", "Owner Behaviour", "Value"
--   GROCERY : "Freshness", "Packaging", "Service"


-- =============================================================
--  TABLE 14 : notifications
-- =============================================================

CREATE TABLE notifications (
    id           BIGSERIAL           PRIMARY KEY,
    user_id      BIGINT              NOT NULL REFERENCES users(id),
    type         notification_type   NOT NULL,
    payload      JSONB               NOT NULL,
    is_read      BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- JSONB payload by type:
--
--   ORDER_PLACED (to BUSINESS_OWNER)
--   { "order_id": 2214, "student_name": "Arjun Mehta", "amount": 185.00 }
--
--   ORDER_CONFIRMED / PREPARING / READY / DELIVERED (to STUDENT)
--   { "order_id": 2214, "business_name": "Maa Ki Rasoi", "status": "CONFIRMED" }
--
--   ORDER_CANCELLED (to STUDENT and BUSINESS_OWNER)
--   { "order_id": 2209, "cancelled_by": "STUDENT", "reason": "Ordered by mistake" }
--
--   BOOKING_REQUEST (to BUSINESS_OWNER)
--   { "booking_id": 55, "student_name": "Riya", "check_in": "2026-05-01", "months": 3 }
--
--   BOOKING_CONFIRMED / CANCELLED (to STUDENT)
--   { "booking_id": 55, "pg_name": "Shiv PG", "status": "CONFIRMED" }
--
--   REVIEW_RECEIVED (to BUSINESS_OWNER)
--   { "reviewer_name": "Anjali", "rating": 5, "business_id": 12 }
--
--   PROMOTIONAL (to targeted STUDENTs)
--   { "title": "Today's Special", "message": "Thali at ₹99", "business_id": 5 }


-- =============================================================
--  INDEXES
-- =============================================================

-- cities
CREATE INDEX idx_cities_name
    ON cities (name);

-- users
CREATE INDEX idx_users_email      ON users (email);
CREATE INDEX idx_users_google_id  ON users (google_id);
CREATE INDEX idx_users_city       ON users (city_id);

-- businesses
CREATE INDEX idx_businesses_owner         ON businesses (owner_id);
CREATE INDEX idx_businesses_type_city     ON businesses (type, city_id);
CREATE INDEX idx_businesses_city          ON businesses (city_id);

-- business_hours
CREATE INDEX idx_business_hours_business  ON business_hours (business_id);

-- listings
CREATE INDEX idx_listings_business        ON listings (business_id);
CREATE INDEX idx_listings_active          ON listings (is_active, is_available);
CREATE INDEX idx_listings_price           ON listings (price);
CREATE INDEX idx_listings_cursor          ON listings (id ASC, is_active);   -- cursor pagination

-- listing_amenities
CREATE INDEX idx_listing_amenities_listing  ON listing_amenities (listing_id);
CREATE INDEX idx_listing_amenities_amenity  ON listing_amenities (amenity_id);

-- orders
CREATE INDEX idx_orders_student           ON orders (student_id, created_at DESC);
CREATE INDEX idx_orders_business_status   ON orders (business_id, status);
CREATE INDEX idx_orders_status            ON orders (status);

-- order_items
CREATE INDEX idx_order_items_order        ON order_items (order_id);

-- bookings
CREATE INDEX idx_bookings_student         ON bookings (student_id, created_at DESC);
CREATE INDEX idx_bookings_business        ON bookings (business_id, status);
CREATE INDEX idx_bookings_listing         ON bookings (listing_id);

-- reviews
CREATE INDEX idx_reviews_business         ON reviews (business_id);
CREATE INDEX idx_reviews_student          ON reviews (student_id);

-- review_category_scores
CREATE INDEX idx_review_categories_review ON review_category_scores (review_id);

-- notifications
CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id, is_read, created_at DESC);


-- =============================================================
--  SEED DATA
-- =============================================================

INSERT INTO amenities (name, icon_name) VALUES
    ('WiFi',           'wifi'),
    ('AC',             'ac'),
    ('Geyser',         'geyser'),
    ('Laundry',        'laundry'),
    ('Parking',        'parking'),
    ('CCTV',           'cctv'),
    ('Meals Included', 'meals'),
    ('Power Backup',   'power'),
    ('Housekeeping',   'housekeeping'),
    ('Gym',            'gym'),
    ('Water Purifier', 'water');


-- =============================================================
--  END OF SCHEMA v2.0
-- =============================================================
