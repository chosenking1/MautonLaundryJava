# Mauton Laundry Backend API Documentation

This document provides a comprehensive list of all available API endpoints in the Mauton Laundry backend application, including expected request payloads and access permissions.

## Authentication (`/api/auth`)

### User Registration
- **Endpoint:** `POST /api/auth/register`
- **Access:** Public
- **Description:** Registers a new user with a default 'USER' role and sends a verification email. Address fields are optional.
- **Request Body:**
  ```json
  {
    "firstname": "John",
    "second_name": "Doe",
    "email": "john.doe@example.com",
    "password": "securepassword123",
    "phone_number": "+1234567890",
    "street": "123 Main St",
    "streetNumber": 123,
    "city": "Anytown",
    "state": "CA",
    "zip": "12345",
    "country": "USA",
    "latitude": 37.7749,
    "longitude": -122.4194
  }
  ```
  *Note: Only firstname, second_name, email, phone_number, and password are required. Address fields are optional.*
- **Response Body (Success 201 Created):**
  ```json
  {
    "email": "john.doe@example.com",
    "message": "Registration successful. Please check your email for verification."
  }
  ```

### Login
- **Endpoint:** `POST /api/auth/login`
- **Access:** Public
- **Description:** Authenticates a user and returns a JWT access token. The token includes the user's role.
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```
- **Response Body (Success 200 OK):**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer"
  }
  ```
  *(Frontend should decode accessToken to get user role from 'role' claim)*

### Logout
- **Endpoint:** `POST /api/auth/logout`
- **Access:** Authenticated
- **Description:** Logs out the current user by clearing the security context.
- **Request Body:** None

### Send Verification Email
- **Endpoint:** `POST /api/auth/send-verification`
- **Access:** Public
- **Description:** Sends an email verification link to the specified user.
- **Query Parameters:** `email` (String, required)
- **Request Body:** None

### Verify Email
- **Endpoint:** `POST /api/auth/verify-email`
- **Access:** Public
- **Description:** Verifies a user's email using a token received via email.
- **Request Body:**
  ```json
  {
    "token": "verification_token_here"
  }
  ```

### Forgot Password
- **Endpoint:** `POST /api/auth/forgot-password`
- **Access:** Public
- **Description:** Sends a password reset link to the user's email.
- **Query Parameters:** `email` (String, required)
- **Request Body:** None

### Reset Password
- **Endpoint:** `POST /api/auth/reset-password`
- **Access:** Public
- **Description:** Resets a user's password using a token received via email.
- **Request Body:**
  ```json
  {
    "token": "reset_token_here",
    "newPassword": "new_secure_password"
  }
  ```

---

## User Management (`/api/v1/users`)

### Get Current User
- **Endpoint:** `GET /api/v1/users/me`
- **Access:** Authenticated
- **Description:** Retrieves current authenticated user's profile including addresses.
- **Response Body (Success 200 OK):**
  ```json
  {
    "id": "user-uuid",
    "email": "john@example.com",
    "fullName": "John Doe",
    "phoneNumber": "+1234567890",
    "role": "USER",
    "isFirstLogin": false,
    "emailVerified": true,
    "addresses": [
      {
        "id": "addr-uuid",
        "street": "123 Main St",
        "street_number": 123,
        "city": "Anytown",
        "state": "CA",
        "zip": "12345",
        "country": "USA",
        "latitude": 37.7749,
        "longitude": -122.4194,
        "isDefault": true,
        "deleted": false
      }
    ]
  }
  ```

### Update User Profile
- **Endpoint:** `PUT /api/v1/users/profile`
- **Access:** Authenticated
- **Description:** Updates current user's profile (name and phone only). Addresses managed via separate endpoints.
- **Request Body:**
  ```json
  {
    "fullName": "John Doe",
    "phoneNumber": "+1234567890"
  }
  ```
- **Response Body (Success 200 OK):**
  ```json
  {
    "message": "Profile updated successfully"
  }
  ```

---

## Admin Role Management (`/api/v1/admin/role-requests`)

### Create Role Change Request (Maker)
- **Endpoint:** `POST /api/v1/admin/role-requests`
- **Access:** `ROLE_CHANGE_CREATE` permission
- **Description:** Creates a request to change a user's role.
- **Request Body:**
  ```json
  {
    "userId": "uuid-of-user",
    "requestedRole": "LAUNDRYMAN"
  }
  ```
- **Response Body (Success 200 OK):** `RoleChangeRequest` object.

### Update Request Status (Checker)
- **Endpoint:** `PATCH /api/v1/admin/role-requests/{requestId}`
- **Access:** `ROLE_CHANGE_UPDATE` permission (must be different from Maker)
- **Description:** Approves or rejects a pending role change request.
- **Request Body:**
  ```json
  {
    "status": "APPROVED"
  }
  ```
  *Status values: `APPROVED`, `REJECTED`*
- **Response Body (Success 200 OK):** `RoleChangeRequest` object.

### Get Pending Requests
- **Endpoint:** `GET /api/v1/admin/role-requests/pending`
- **Access:** `ROLE_CHANGE_READ` permission
- **Description:** Retrieves all pending role change requests.
- **Response Body (Success 200 OK):** List of `RoleChangeRequest` objects.

---

## Admin Dashboard (`/api/admin`)

### Dashboard Stats
- **Endpoint:** `GET /api/admin/dashboard`
- **Access:** `ADMIN_DASHBOARD_READ` permission
- **Description:** Retrieves various statistics for the admin dashboard (total users, active users, etc.).
- **Response Body (Success 200 OK):** `AdminDashboardResponse` object.

---

## Addresses (`/api/v1/addresses`)

### Create Address
- **Endpoint:** `POST /api/v1/addresses`
- **Access:** Authenticated
- **Description:** Creates a new address for the current user.
- **Request Body:**
  ```json
  {
    "street": "789 Corporate Blvd",
    "streetNumber": 789,
    "city": "Metropolis",
    "state": "CA",
    "zip": "90210",
    "country": "USA",
    "latitude": 34.0522,
    "longitude": -118.2437
  }
  ```
  *Note: latitude and longitude are optional*
- **Response Body (Success 201 Created):** `Address` object.

### Get User Addresses
- **Endpoint:** `GET /api/v1/addresses`
- **Access:** Authenticated
- **Description:** Retrieves all addresses associated with the current user.
- **Response Body (Success 200 OK):** List of `Address` objects.

### Get Address by ID
- **Endpoint:** `GET /api/v1/addresses/{addressId}`
- **Access:** Authenticated
- **Description:** Retrieves a specific address by its ID (user's own addresses only).
- **Response Body (Success 200 OK):** `Address` object.

### Update Address
- **Endpoint:** `PUT /api/v1/addresses/{addressId}`
- **Access:** Authenticated
- **Description:** Updates an existing address (user's own addresses only).
- **Request Body:**
  ```json
  {
    "street": "789 Corporate Boulevard",
    "streetNumber": 789,
    "city": "Metropolis",
    "state": "CA",
    "zip": "90210",
    "country": "USA",
    "latitude": 34.0522,
    "longitude": -118.2437
  }
  ```
- **Response Body (Success 200 OK):** `Address` object.

### Delete Address
- **Endpoint:** `DELETE /api/v1/addresses/{addressId}`
- **Access:** Authenticated
- **Description:** Soft deletes an address (user's own addresses only).
- **Response Body (Success 204 No Content):** None.

---

## Bookings (`/api/v1/bookings`)

### Create Booking
- **Endpoint:** `POST /api/v1/bookings`
- **Access:** `BOOKING_CREATE` permission
- **Description:** Creates a new laundry booking.
- **Request Body:**
  ```json
  {
    "bookingType": "LAUNDRY",
    "pickupAddressId": "uuid-of-address",
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
- **Response Body (Success 201 Created):** `CreateBookingResponse` object.

### Get Booking
- **Endpoint:** `GET /api/v1/bookings/{bookingId}`
- **Access:** `BOOKING_READ` permission
- **Description:** Retrieves booking details by ID.
- **Response Body (Success 200 OK):** `BookingDetailsResponse` object.

### Update Booking Status
- **Endpoint:** `PATCH /api/v1/bookings/{bookingId}/status`
- **Access:** `BOOKING_UPDATE` permission
- **Description:** Updates the status of a booking.
- **Request Body:**
  ```json
  {
    "status": "CONFIRMED"
  }
  ```
  *Status values: `CREATED`, `CONFIRMED`, `PICKUP_ASSIGNED`, `PICKED_UP`, `RECEIVED_BY_LAUNDRYMAN`, `WASHING`, `READY_FOR_DELIVERY`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED`*
- **Response Body (Success 200 OK):**
  ```json
  {
    "message": "Status updated successfully"
  }
  ```

---

## Pricing (`/api/v1/pricing`)

### Get Pricing Config
- **Endpoint:** `GET /api/v1/pricing/config`
- **Access:** `PRICING_READ` permission
- **Description:** Retrieves the current pricing configuration.
- **Response Body (Success 200 OK):** Map of String to BigDecimal.

### Calculate Price
- **Endpoint:** `POST /api/v1/pricing/calculate`
- **Access:** Public
- **Description:** Calculates the total price for a potential booking based on items, express service, and delivery distance.
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
- **Response Body (Success 200 OK):**
  ```json
  {
    "totalPrice": 123.45
  }
  ```

### Update Pricing Config
- **Endpoint:** `PUT /api/v1/pricing/config`
- **Access:** `PRICING_UPDATE` permission
- **Description:** Updates the pricing configuration.
- **Request Body:** Map of String to Object (e.g., `{"expressFee": 10.0, "deliveryFee": 5.0}`).
- **Response Body (Success 200 OK):**
  ```json
  {
    "message": "Pricing configuration updated successfully"
  }
  ```

---

## Services (`/api/v1/services`)

### Create Service
- **Endpoint:** `POST /api/v1/services`
- **Access:** `SERVICE_CREATE` permission
- **Description:** Creates a new laundry service offering.
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
- **Response Body (Success 201 Created):** `ServiceResponse` object.

### Update Service
- **Endpoint:** `PUT /api/v1/services/{id}`
- **Access:** `SERVICE_UPDATE` permission
- **Description:** Updates an existing laundry service offering.
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
- **Response Body (Success 200 OK):** `ServiceResponse` object.

### Get All Services
- **Endpoint:** `GET /api/v1/services`
- **Access:** `SERVICE_READ` permission
- **Description:** Retrieves a list of all available laundry services.
- **Response Body (Success 200 OK):** List of `ServiceResponse` objects.

### Get Service by ID
- **Endpoint:** `GET /api/v1/services/{id}`
- **Access:** `SERVICE_READ` permission
- **Description:** Retrieves details of a specific laundry service.
- **Response Body (Success 200 OK):** `ServiceResponse` object.

### Get Services by Category
- **Endpoint:** `GET /api/v1/services/category/{category}`
- **Access:** `SERVICE_READ` permission
- **Description:** Retrieves laundry services filtered by category.
- **Response Body (Success 200 OK):** List of `ServiceResponse` objects.

### Delete Service
- **Endpoint:** `DELETE /api/v1/services/{id}`
- **Access:** `SERVICE_DELETE` permission
- **Description:** Deletes a laundry service.
- **Response Body (Success 204 No Content):** None.

---

## Delivery Management (`/api/v1/delivery`)

### Register Pickup
- **Endpoint:** `POST /api/v1/delivery/pickup`
- **Access:** `DELIVERY_CREATE` permission
- **Description:** Registers a new pickup delivery request.
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
- **Response Body (Success 200 OK):** `CreateDeliveryResponse` object.

### Set Laundry Agent Address
- **Endpoint:** `POST /api/v1/delivery/{id}/agent-address`
- **Access:** `DELIVERY_UPDATE` permission
- **Description:** Sets the laundry agent's address for a specific delivery.
- **Query Parameters:** `agentAddress` (Long, required)
- **Response Body (Success 200 OK):**
  ```json
  {
    "message": "Laundry agent address updated successfully."
  }
  ```

### View Delivery Status
- **Endpoint:** `GET /api/v1/delivery/{id}`
- **Access:** `DELIVERY_READ` permission
- **Description:** Retrieves the status of a specific delivery.
- **Response Body (Success 200 OK):** `PickupStatusResponse` object.

### View All Deliveries
- **Endpoint:** `GET /api/v1/delivery`
- **Access:** `DELIVERY_READ` permission
- **Description:** Retrieves a list of all deliveries.
- **Response Body (Success 200 OK):** Collection of `DeliveryManagement` objects.

### Update Delivery Status
- **Endpoint:** `PUT /api/v1/delivery`
- **Access:** `DELIVERY_UPDATE` permission
- **Description:** Updates the status of a pickup or delivery.
- **Request Body:**
  ```json
  {
    "id": 1,
    "deliveryStatus": "PICKED_UP"
  }
  ```
- **Response Body (Success 200 OK):** `PickupStatusUpdateResponse` object.

### Delete Delivery
- **Endpoint:** `DELETE /api/v1/delivery/{id}`
- **Access:** `DELIVERY_DELETE` permission
- **Description:** Deletes a delivery record.
- **Response Body (Success 200 OK):** None.

---

## Analytics (`/api/v1/analytics`)

### Dashboard Analytics
- **Endpoint:** `GET /api/v1/analytics/dashboard`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Retrieves comprehensive dashboard analytics.
- **Response Body (Success 200 OK):** Map of String to Object.

### Users by Role
- **Endpoint:** `GET /api/v1/analytics/users-by-role`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Retrieves a count of users grouped by their role.
- **Response Body (Success 200 OK):** Map of String to Long.

### Monthly Stats
- **Endpoint:** `GET /api/v1/analytics/monthly-stats`
- **Access:** `ANALYTICS_READ` permission
- **Description:** Retrieves monthly performance statistics.
- **Response Body (Success 200 OK):** Map of String to Object.

---

## User Permissions (`/api/v1/user`)

### Get Permissions
- **Endpoint:** `GET /api/v1/user/permissions`
- **Access:** Authenticated
- **Description:** Retrieves the current user's permissions and accessible pages based on their role.
- **Response Body (Success 200 OK):** Map of String to Object.

---

## Laundry Item Catalog (`/api/v1/laundry-items`)

### Get All Items
- **Endpoint:** `GET /api/v1/laundry-items`
- **Access:** `LAUNDRY_ITEM_READ` permission
- **Description:** Retrieves all active laundry items in the catalog.
- **Response Body (Success 200 OK):** List of `LaundryItemCatalog` objects.

### Get Item by ID
- **Endpoint:** `GET /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_READ` permission
- **Description:** Retrieves details of a specific laundry item.
- **Response Body (Success 200 OK):** `LaundryItemCatalog` object.

### Create Item
- **Endpoint:** `POST /api/v1/laundry-items`
- **Access:** `LAUNDRY_ITEM_CREATE` permission
- **Description:** Creates a new laundry item in the catalog.
- **Request Body:**
  ```json
  {
    "name": "T-Shirt",
    "unit": "PCS",
    "basePriceColored": 5.00,
    "basePriceWhite": 7.00
  }
  ```
- **Response Body (Success 201 Created):** `LaundryItemCatalog` object.

### Update Item
- **Endpoint:** `PUT /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_UPDATE` permission
- **Description:** Updates an existing laundry item.
- **Request Body:**
  ```json
  {
    "name": "T-Shirt",
    "unit": "PCS",
    "basePriceColored": 5.50,
    "basePriceWhite": 7.50
  }
  ```
- **Response Body (Success 200 OK):** `LaundryItemCatalog` object.

### Deactivate Item
- **Endpoint:** `DELETE /api/v1/laundry-items/{id}`
- **Access:** `LAUNDRY_ITEM_DELETE` permission
- **Description:** Deactivates (soft deletes) a laundry item.
- **Response Body (Success 200 OK):**
  ```json
  {
    "message": "Item deactivated successfully"
  }
  ```
