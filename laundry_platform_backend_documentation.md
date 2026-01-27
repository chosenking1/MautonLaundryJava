# Backend Documentation – Laundry Service Platform

## 1. Overview

This document defines the **backend architecture, responsibilities, services, and system behavior** for the Laundry Service Platform.

The backend acts as:
- The single source of truth
- The enforcer of roles & permissions
- The pricing and booking rules engine
- The integration layer for maps, notifications, and tracking

> **Note:** Database structure is intentionally excluded from this document and will be covered in a separate DB documentation.

---

## 2. High-Level Architecture

### 2.1 Architectural Style

- **Monolithic (Modular) Spring Boot Application**
- Clear separation of concerns:
  - Controllers (API layer)
  - Services (business logic)
  - Domain models
  - Security
  - Integrations

This allows:
- Fast iteration early
- Easy extraction to microservices later if needed

---

### 2.2 Core Backend Modules

1. Authentication & Authorization
2. User & Role Management
3. Booking Management
4. Laundry Operations
5. Cleaning Operations
6. Pricing Engine
7. Logistics & Tracking
8. Notification System
9. Audit & Activity Logging
10. Admin Configuration

---

## 3. Authentication & Authorization

### 3.1 Authentication

- Email + password login
- Password hashing (BCrypt)
- JWT-based authentication
- Refresh token support

### 3.2 Authorization

- Role-based access control (RBAC)
- Permissions resolved from database

Example roles:
- CUSTOMER
- LAUNDRYMAN
- DELIVERY_AGENT
- ADMIN

Authorization rules are:
- Enforced strictly on backend
- Never trusted to frontend

---

## 3.3 Role Registration Rules

### Allowed registration roles (front-end)

Users can register only as:
- CUSTOMER
- LAUNDRYMAN
- DELIVERY_AGENT

**Admin role cannot be created from the front-end.**
Only an admin can promote a user to ADMIN.

### Role update restrictions

- Users may update their profile details but **cannot change their own role**.
- Roles can only be changed through a controlled admin workflow.

---

## 3.4 Role Query Endpoint

**GET /api/v1/users/{userId}/role**

**Accessible by:**
- Admins (all)
- The user themselves (optional)

**Returns:**
- userId
- currentRole
- roleStatus (ACTIVE / PENDING / REJECTED)

---

## 3.5 Role Update (Maker-Checker Workflow)

Role updates require a **maker-checker workflow**.

### Maker (Admin A) creates a role change request

**POST /api/v1/admin/role-requests**

**Payload:**
```json
{
  "userId": 123,
  "requestedRole": "LAUNDRYMAN"
}
```

Rules:
- Admin A cannot request a role change for themselves.
- Admin A cannot approve their own request.

Result:
- A new `RoleChangeRequest` is created with status `PENDING`.

### Checker (Admin B) approves or rejects

**PATCH /api/v1/admin/role-requests/{requestId}**

**Payload:**
```json
{
  "status": "APPROVED"
}
```

Rules:
- Only an admin who is **not the maker** can approve or reject.
- Admin cannot approve their own request.
- Approved request updates the user’s role.

---

## 3.6 Role Change Request Entity

A new entity `RoleChangeRequest` is required with the following fields:

- requestId
- userId
- requestedRole
- requestedByAdminId
- approvedByAdminId
- status (PENDING, APPROVED, REJECTED)
- createdAt
- approvedAt

---

## 3.7 Business Rules Summary (Role Management)

| Action | Who Can Do | Notes |
|--------|------------|------|
| Register | Anyone | Only CUSTOMER / LAUNDRYMAN / DELIVERY_AGENT |
| Update profile | User | All fields except role |
| Get role | Admin + user | Returns current role |
| Request role change | Admin | Maker creates request |
| Approve role change | Different Admin | Checker approves/rejects |
| Self role change | Not allowed | Admin cannot upgrade themselves |

---

## 4. User & Role Management

### Responsibilities

- User registration & onboarding
- Role assignment
- Account activation / suspension
- Soft deletes

### Rules

- One user may have multiple roles (future-proof)
- Role permissions are configurable
- Admin-only access to role management

---

## 5. Booking Management

### 5.1 Booking Types

- LAUNDRY
- CLEANING

Each booking:
- Has a unique tracking number
- Has a lifecycle state
- Is immutable after pickup

---

### 5.2 Booking Lifecycle (Laundry)

1. CREATED
2. CONFIRMED
3. PICKUP_ASSIGNED
4. PICKED_UP
5. RECEIVED_BY_LAUNDRYMAN
6. WASHING
7. READY_FOR_DELIVERY
8. OUT_FOR_DELIVERY
9. DELIVERED

State rules:
- Customer edits allowed **only before PICKED_UP**
- State transitions validated by backend

---

### 5.3 Booking Lifecycle (Cleaning)

1. CREATED
2. CONFIRMED
3. CLEANER_ASSIGNED
4. IN_PROGRESS
5. COMPLETED

Optional:
- Recurring bookings (future feature)

---

## 6. Laundry Operations Module

### Responsibilities

- Assign laundrymen to bookings
- Track laundry item quantities
- Update laundry status
- Validate item counts against booking

Laundrymen can:
- View assigned bookings
- Update laundry states

Laundrymen cannot:
- Modify prices
- Modify booking items

---

## 7. Cleaning Operations Module

### Responsibilities

- Assign cleaning agencies
- Handle scheduling
- Support one-time bookings
- Support recurring schedules (future)

Rules:
- Cleaner assignment is internal
- Customer does not select specific cleaners

---

## 8. Pricing Engine

### 8.1 Pricing Inputs

- Laundry item type
- Quantity
- Colour type (White / Coloured)
- Express service flag
- Delivery distance
- Free delivery threshold

### 8.2 Pricing Rules

- All prices resolved server-side
- Express pricing configurable by admin
- Delivery fee waived if threshold met
- Price snapshot stored at booking time

Pricing must be:
- Deterministic
- Auditable
- Immutable after booking confirmation

---

## 9. Logistics & Tracking

### 9.1 Pickup & Delivery Assignment

- Assign delivery agents
- Track pickup & drop-off
- Support re-assignment

### 9.2 Location Tracking

- Latitude & longitude storage
- Live location updates
- ETA calculations

Location updates:
- Rate-limited
- Stored temporarily

---

## 10. Notification System

### Supported Channels

- Email
- SMS
- In-app notifications

### Trigger Events

- Booking created
- Pickup assigned
- Pickup completed
- Laundry status updates
- Out for delivery
- Delivered

Backend responsibilities:
- Event publishing
- Retry handling
- Channel fallback

---

## 11. Audit & Activity Logging

### What to Audit

- Booking changes
- Price changes
- Role & permission updates
- Status transitions
- Admin actions

Audit logs must include:
- Actor
- Action
- Timestamp
- Old vs new values

Audit logs are:
- Immutable
- Queryable by admin

---

## 12. Admin Configuration

Admins can configure:
- Laundry item catalog
- Pricing rules
- Express fees
- Delivery rules
- Free delivery thresholds

Rules:
- Changes apply only to new bookings
- All changes audited

---

## 13. API Design Guidelines

- RESTful endpoints
- Versioned APIs (/api/v1)
- Clear error codes
- Consistent response formats

---

## 14. Error Handling

- Centralized exception handling
- Meaningful error messages
- No internal stack traces exposed

---

## 15. Security Considerations

- Input validation
- Rate limiting
- CSRF protection (where applicable)
- Secure secrets management

---

## 16. Scalability Considerations

- Stateless services
- Horizontal scaling
- Async processing for notifications
- Future microservice extraction points:
  - Notifications
  - Tracking

---

**End of Backend Documentation**

