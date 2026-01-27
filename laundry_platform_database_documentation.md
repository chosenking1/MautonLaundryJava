# Database Documentation – Laundry Service Platform

## 1. Overview

This document defines the **database structure**, relationships, and data rules for the Laundry Service Platform.

The database is designed to support:
- Customers, laundrymen, delivery agents, and admins
- Booking lifecycle for laundry and cleaning
- Pricing rules (express, delivery fees, free delivery threshold)
- Audit logging
- Real-time tracking
- Role-based access control (DB-driven)

> **Note:** The backend logic and API design are covered in the Backend Documentation.

---

## 2. Database Platform & Strategy

### 2.1 Recommended Database

**PostgreSQL** is recommended because:
- Strong UUID support
- JSONB support for audit snapshots
- Better concurrency and scalability
- Optional PostGIS support later for advanced geospatial queries

MySQL can be used early, but PostgreSQL is more suited for this architecture.

### 2.2 Schema Migration Strategy

- **Development:** Hibernate `ddl-auto=update` (only in dev)
- **Production:** Use Flyway or Liquibase migrations
- **No auto-DDL in production** to prevent accidental schema drift

---

## 3. Key Design Principles

### 3.1 ID Strategy

| Entity | Recommended ID Type | Reason |
|--------|----------------------|--------|
| Users | **UUID** | Secure, hard to guess, external exposure |
| Bookings | **UUID (preferred)** | Prevents enumeration and ID guessing |
| Other Entities | **Auto-increment (BIGINT)** | Efficient and simple |

### 3.2 Snapshot Principle

Booking data must not change after creation. This means:
- Prices are copied into booking tables
- Delivery fee and express fee are stored at booking time
- Admin price changes do not affect past bookings

---

## 4. Tables & Relationships

### 4.1 USERS

| Column | Type | Notes |
|--------|------|------|
| id | UUID (PK) | Primary key |
| email | VARCHAR(255) UNIQUE | Login |
| password | VARCHAR | Hashed (BCrypt) |
| full_name | VARCHAR | |
| phone_number | VARCHAR | |
| deleted | BOOLEAN | Soft delete |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

---

### 4.2 ADDRESSES

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| user_id | UUID FK | Owner |
| label | VARCHAR | "Home", "Office" |
| line1 | VARCHAR | |
| line2 | VARCHAR | |
| city | VARCHAR | |
| state | VARCHAR | |
| country | VARCHAR | |
| postal_code | VARCHAR | |
| latitude | DECIMAL | For maps |
| longitude | DECIMAL | For maps |
| created_at | TIMESTAMP | |

**Relationships:**
- One user can have multiple addresses
- Bookings reference one pickup address

---

### 4.3 ROLES

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| name | VARCHAR | CUSTOMER / LAUNDRYMAN / DELIVERY_AGENT / ADMIN |
| created_at | TIMESTAMP | |

---

### 4.4 USER_ROLES (Many-to-Many)

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| user_id | UUID FK | |
| role_id | BIGINT FK | |

---

### 4.5 ROLE_CHANGE_REQUESTS (Maker-Checker)

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| user_id | UUID FK | User being upgraded |
| requested_role_id | BIGINT FK | Role requested |
| requested_by_admin_id | UUID FK | Maker admin |
| approved_by_admin_id | UUID FK | Checker admin |
| status | VARCHAR | PENDING / APPROVED / REJECTED |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

---

### 4.6 LAUNDRY_ITEM_CATALOG

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| name | VARCHAR | e.g., trousers |
| unit | VARCHAR | “pair”, “item” |
| base_price_colored | DECIMAL | Price for colored |
| base_price_white | DECIMAL | Price for white |
| is_active | BOOLEAN | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

---

### 4.7 PRICING_CONFIG

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| key | VARCHAR | EXPRESS_FEE, DELIVERY_FEE, FREE_DELIVERY_THRESHOLD |
| value | JSONB | Stores amount or percentage |
| effective_from | TIMESTAMP | |
| created_at | TIMESTAMP | |

---

### 4.8 BOOKINGS

| Column | Type | Notes |
|--------|------|------|
| id | UUID (PK) | Recommended |
| user_id | UUID FK | Customer |
| booking_type | VARCHAR | LAUNDRY / CLEANING |
| status | VARCHAR | CREATED / PICKED_UP / DELIVERED etc |
| pickup_address_id | BIGINT FK | |
| return_date | TIMESTAMP | Calculated (pickup + 7 days or express) |
| express | BOOLEAN | |
| delivery_fee | DECIMAL | Snapshot at booking time |
| total_price | DECIMAL | Snapshot at booking time |
| tracking_number | VARCHAR | Unique tracking code |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

---

### 4.9 BOOKING_LAUNDRY_ITEMS (Snapshot)

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| booking_id | UUID FK | |
| item_id | BIGINT FK | Catalog item |
| quantity | INT | |
| color_type | VARCHAR | WHITE / COLORED |
| unit_price | DECIMAL | Snapshot price |
| total_price | DECIMAL | quantity * unit_price |

---

### 4.10 DELIVERY_ASSIGNMENTS

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| booking_id | UUID FK | |
| delivery_agent_id | UUID FK | |
| status | VARCHAR | ASSIGNED / IN_TRANSIT / COMPLETED |
| created_at | TIMESTAMP | |

---

### 4.11 LAUNDRYMAN_ASSIGNMENTS

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| booking_id | UUID FK | |
| laundryman_id | UUID FK | |
| status | VARCHAR | ASSIGNED / RECEIVED / COMPLETED |
| created_at | TIMESTAMP | |

---

### 4.12 LOCATION_TRACKING

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| booking_id | UUID FK | |
| user_id | UUID FK | Delivery agent or laundryman |
| latitude | DECIMAL | |
| longitude | DECIMAL | |
| recorded_at | TIMESTAMP | |

---

### 4.13 AUDIT_LOGS

| Column | Type | Notes |
|--------|------|------|
| id | BIGINT (PK) | Auto-increment |
| actor_user_id | UUID FK | |
| action | VARCHAR | UPDATE_BOOKING, CHANGE_PRICE, etc |
| target_type | VARCHAR | BOOKING, USER, ROLE |
| target_id | VARCHAR | UUID or BIGINT |
| old_value | JSONB | Snapshot |
| new_value | JSONB | Snapshot |
| created_at | TIMESTAMP | |

---

## 5. Relationship Summary (ER Summary)

- Users (UUID) **1→N** Addresses
- Users **N→M** Roles via USER_ROLES
- Booking **1→N** Booking_Laundry_Items
- Booking **1→1** Pickup Address
- Booking **1→1** Delivery Assignment
- Booking **1→1** Laundryman Assignment
- Booking **1→N** Tracking updates
- Audit logs **1→N** actor user

---

## 6. ER Diagram (Textual)

```
USERS (UUID) 1---N ADDRESSES
USERS 1---N USER_ROLES N---1 ROLES
USERS 1---N ROLE_CHANGE_REQUESTS
USERS 1---N BOOKINGS
BOOKINGS 1---N BOOKING_LAUNDRY_ITEMS
BOOKINGS 1---1 DELIVERY_ASSIGNMENTS
BOOKINGS 1---1 LAUNDRYMAN_ASSIGNMENTS
BOOKINGS 1---N LOCATION_TRACKING
USERS 1---N AUDIT_LOGS
```

---

## 7. Normalization Analysis

### 7.1 Normal Forms Applied

#### 1NF (Atomicity)
- All tables store atomic values (no repeated groups)
- Example: `Booking_Laundry_Items` stores one row per item

#### 2NF (Full Functional Dependency)
- Composite keys are avoided
- Each non-key attribute depends on the full primary key

#### 3NF (No Transitive Dependency)
- Prices are stored in catalog tables, but booking uses snapshot tables
- No dependency chain like `booking -> item -> price` for historical records

### 7.2 Why Snapshot Tables are Necessary

If pricing changes in the catalog, historical bookings must remain unchanged. Therefore:
- `booking_laundry_items` stores unit_price and total_price at booking time
- `booking` stores total_price and delivery_fee snapshots

This prevents disputes and preserves audit integrity.

---

## 8. Indexing & Performance

### Recommended Indexes

- USERS: `(email)` unique index
- BOOKINGS: `(user_id)`, `(status)`, `(created_at)`
- TRACKING: `(booking_id, recorded_at)`
- AUDIT_LOGS: `(actor_user_id, created_at)`

### Scaling Strategy

- Start with a single database instance
- Add read replicas for reporting
- Use Redis for caching pricing configs and static catalogs
- Consider partitioning tracking data by date for scale

---

## 9. JPA Entity Skeletons (Example)

### User Entity (Simplified)
```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @Type(type = "pg-uuid")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String phoneNumber;
    private Boolean deleted = false;

    @OneToMany(mappedBy = "user")
    private List<Address> addresses;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;
}
```

---

### Booking Entity (Simplified)
```java
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @Type(type = "pg-uuid")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private BookingType bookingType;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @ManyToOne
    @JoinColumn(name = "pickup_address_id")
    private Address pickupAddress;

    private Boolean express;
    private BigDecimal deliveryFee;
    private BigDecimal totalPrice;
    private LocalDateTime returnDate;

    @OneToMany(mappedBy = "booking")
    private List<BookingLaundryItem> items;
}
```

---

## 10. Notes & Decisions Needed

### UUID vs Auto-increment
- **Use UUID for Users and Bookings (recommended)**
- Use auto-increment for other entities

### Booking ID
- Recommended: UUID
- If you choose auto-increment, ensure IDs are not publicly exposed

---

**End of Database Documentation**

