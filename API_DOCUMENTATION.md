# Mauton Laundry Backend API Documentation

This document provides a comprehensive list of all available API endpoints in the Mauton Laundry backend application, including expected request payloads.

## Authentication (`/api/auth`)

### Login
- **Endpoint:** `POST /api/auth/login`
- **Access:** Public
- **Description:** Authenticate a user and return a JWT token.
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```

### Logout
- **Endpoint:** `POST /api/auth/logout`
- **Access:** Authenticated
- **Description:** Logout the current user.
- **Request Body:** None

### Send Verification Email
- **Endpoint:** `POST /api/auth/send-verification`
- **Access:** Public
- **Description:** Send an email verification link to the user.
- **Query Parameters:** `email` (String, required)
- **Request Body:** None

### Verify Email
- **Endpoint:** `POST /api/auth/verify-email`
- **Access:** Public
- **Description:** Verify a user's email using a token.
- **Request Body:**
  ```json
  {
    "token": "verification_token_here"
  }
  ```

### Forgot Password
- **Endpoint:** `POST /api/auth/forgot-password`
- **Access:** Public
- **Description:** Send a password reset link to the user's email.
- **Query Parameters:** `email` (String, required)
- **Request Body:** None

### Reset Password
- **Endpoint:** `POST /api/auth/reset-password`
- **Access:** Public
- **Description:** Reset a user's password using a token.
- **Request Body:**
  ```json
  {
    "token": "reset_token_here",
    "newPassword": "new_secure_password"
  }
  ```

---

## User Management (`/api/v1/users`)

### Get User by Email
- **Endpoint:** `GET /api/v1/users/getUser/{email}`
- **Access:** `USER_READ` permission or self
- **Description:** Get user details by email.

### Get User by ID
- **Endpoint:** `GET /api/v1/users/{userId}`
- **Access:** `USER_READ` permission or self
- **Description:** Get user details by ID.

### Get User Role
- **Endpoint:** `GET /api/v1/users/{userId}/role`
- **Access:** `USER_READ` permission or self
- **Description:** Get user role information.

### Update User Profile
- **Endpoint:** `PUT /api/v1/users/{userId}`
- **Access:** `USER_UPDATE` permission or self
- **Description:** Update user profile details.
- **Request Body:**
  ```json
  {
    "fullName": "John Doe",
    "phoneNumber": "+1234567890"
  }
  ```

---

## Admin Role Management (`/api/v1/admin/role-requests`)

### Create Role Change Request (Maker)
- **Endpoint:** `POST /api/v1/admin/role-requests`
- **Access:** `ROLE_CHANGE_CREATE` permission
- **Description:** Create a request to change a user's role.
- **Request Body:**
  ```json
  {
    "userId": "uuid-of-user",
    "requestedRole": "LAUNDRYMAN"
  }
  ```

### Update Request Status (Checker)
- **Endpoint:** `PATCH /api/v1/admin/role-requests/{requestId}`
- **Access:** `ROLE_CHANGE_UPDATE` permission (must be different from Maker)
- **Description:** Approve or reject a role change request.
- **Request Body:**
  ```json
  {
    "status": "APPROVED" 
  }
  ```
  *Status values: APPROVED, REJECTED*

### Get Pending Requests
- **Endpoint:** `GET /api/v1/admin/role-requests/pending`
- **Access:** `ROLE_CHANGE_READ` permission
- **Description:** Get all pending role change requests.

---

## Admin Dashboard (`/api/admin`)

### Dashboard Stats
- **Endpoint:** `GET /api/admin/dashboard`
- **Access:** `ADMIN_DASHBOARD_READ` permission
- **Description:** Get admin dashboard statistics.

---

## Bookings (`/api/v1/bookings`)

### Create Booking
- **Endpoint:** `POST /api/v1/bookings`
- **Access:** `BOOKING_CREATE` permission
- **Description:** Create a new laundry booking.
- **Request Body:**
  ```json
  {
    "bookingType": "LAUNDRY",
    "pickupAddressId": "address-uuid",
    "express": false,
    "items": [
      {
        "itemId": 1,
        "quantity": 2,
        "colorType": "WHITE"
      },
      {
        "itemId": 2,
        "quantity": 1,
        "colorType": "COLORED"
      }
    ]
  }
  ```

### Get Booking
- **Endpoint:** `GET /api/v1/bookings/{bookingId}`
- **Access:** `BOOKING_READ` permission
- **Description:** Get booking details by ID.

### Update Booking Status
- **Endpoint:** `PATCH /api/v1/bookings/{bookingId}/status`
- **Access:** `BOOKING_UPDATE` permission
- **Description:** Update the status of a booking.
- **Request Body:**
  ```json
  {
    "status": "CONFIRMED" 
  }
  ```
  *Status values: PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, etc.*

---

## Pricing (`/api/v1/pricing`)

### Get Pricing Config
- **Endpoint:** `GET /api/v1/pricing/config`
- **Access:** `PRICING_READ` permission
- **Description:** Get current pricing configuration.

### Calculate Price
- **Endpoint:** `POST /api/v1/pricing/calculate`
- **Access:** Public
- **Description:** Calculate the price for a potential booking.
- **Request Body:**
  ```json
  {
    "items": [
      {
        "itemId": 1,
        "quantity": 2,
        "colorType": "WHITE"
      }
    ],
    "express": false,
    "deliveryDistance": 5.0
  }
  ```

### Update Pricing Config
- **Endpoint:** `PUT /api/v1/pricing/config`
- **Access:** `PRICING_UPDATE` permission
- **Description:** Update pricing configuration.
- **Request Body:** (Map of configuration key-values)

---

## Services (`/api/v1/services`)

### Create Service
- **Endpoint:** `POST /api/v1/services`
- **Access:** `SERVICE_CREATE` permission
- **Description:** Create a new service.
- **Request Body:**
  ```json
  {
    "name": "Dry Cleaning",
    "description": "Professional dry cleaning",
    "category": "CLOTHING",
    "price": 10.0,
    "white": 12.0
  }
  ```

### Update Service
- **Endpoint:** `PUT /api/v1/services/{id}`
- **Access:** `SERVICE_UPDATE` permission
- **Description:** Update an existing service.
- **Request Body:**
  ```json
  {
    "service_name": "Dry Cleaning Updated",
    "service_details": "Updated description",
    "type_of_service": "CLOTHING",
    "service_price": 15,
    "service_price_white": 18
  }
  ```

### Get All Services
- **Endpoint:** `GET /api/v1/services`
- **Access:** `SERVICE_READ` permission
- **Description:** Get a list of all available services.

### Get Service by ID
- **Endpoint:** `GET /api/v1/services/{id}`
- **Access:** `SERVICE_READ` permission
- **Description:** Get details of a specific service.

### Get Services by Category
- **Endpoint:** `GET /api/v1/services/category/{category}`
- **Access:** `SERVICE_READ` permission
- **Description:** Get services filtered by category.

### Delete Service
- **Endpoint:** `DELETE /api/v1/services/{id}`
- **Access:** `SERVICE_DELETE` permission
- **Description:** Delete a service.

---

## Delivery Management (`/api/v1/delivery`)

### Register Pickup
- **Endpoint:** `POST /api/v1/delivery/pickup`
- **Access:** `DELIVERY_CREATE` permission
- **Description:** Register a new pickup delivery.
- **Request Body:**
  ```json
  {
    "date_booked": "2023-10-27T10:00:00",
    "userAddress": "123 Main St",
    "agentAddress": "456 Depot Rd",
    "urgency": "HIGH",
    "booking_id": 123
  }
  ```

### Set Laundry Agent Address
- **Endpoint:** `POST /api/v1/delivery/{id}/agent-address`
- **Access:** `DELIVERY_UPDATE` permission
- **Description:** Set the laundry agent address for a delivery.
- **Query Parameters:** `agentAddress` (Long, required)

### View Delivery Status
- **Endpoint:** `GET /api/v1/delivery/{id}`
- **Access:** `DELIVERY_READ` permission
- **Description:** View the status of a specific delivery.

### View All Deliveries
- **Endpoint:** `GET /api/v1/delivery`
- **Access:** `DELIVERY_READ` permission
- **Description:** View all deliveries.

### Update Delivery Status
- **Endpoint:** `PUT /api/v1/delivery`
- **Access:** `DELIVERY_UPDATE` permission
- **Description:** Update the status of a pickup/delivery.
- **Request Body:**
  ```json
  {
    "id": 1,
    "deliveryStatus": "PICKED_UP"
  }
  ```

### Delete Delivery
- **Endpoint:** `DELETE /api/v1/delivery/{id}`
- **Access:** `DELIVERY_DELETE` permission
- **Description:** Delete a delivery record.

---

## Analytics (`/api/v1/analytics`)

### Dashboard Analytics
- **Endpoint:** `GET /api/v1/analytics/dashboard`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Get comprehensive dashboard analytics.

### Users by Role
- **Endpoint:** `GET /api/v1/analytics/users-by-role`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Get a count of users grouped by their role.

### Monthly Stats
- **Endpoint:** `GET /api/v1/analytics/monthly-stats`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Get monthly performance statistics.

---

## User Permissions (`/api/v1/user`)

### Get Permissions
- **Endpoint:** `GET /api/v1/user/permissions`
- **Access:** Authenticated
- **Description:** Get the current user's permissions and accessible pages.

---

## Laundry Item Catalog (`/api/v1/laundry-items`)

### Get All Items
- **Endpoint:** `GET /api/v1/laundry-items`
- **Access:** `LAUNDRY_ITEM_READ` permission
- **Description:** Get all active laundry items.

### Get Item by ID
- **Endpoint:** `GET /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_READ` permission
- **Description:** Get details of a specific laundry item.

### Create Item
- **Endpoint:** `POST /api/v1/laundry-items`
- **Access:** `LAUNDRY_ITEM_CREATE` permission
- **Description:** Create a new laundry item.
- **Request Body:**
  ```json
  {
    "name": "T-Shirt",
    "unit": "PCS",
    "basePriceColored": 5.00,
    "basePriceWhite": 7.00
  }
  ```

### Update Item
- **Endpoint:** `PUT /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_UPDATE` permission
- **Description:** Update an existing laundry item.
- **Request Body:**
  ```json
  {
    "name": "T-Shirt",
    "unit": "PCS",
    "basePriceColored": 5.50,
    "basePriceWhite": 7.50
  }
  ```

### Deactivate Item
- **Endpoint:** `DELETE /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_DELETE` permission
- **Description:** Deactivate (soft delete) a laundry item.
