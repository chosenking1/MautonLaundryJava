# Mobile API Reference for Customer App (Flutter)

This document provides the essential API endpoints, request payloads, and expected responses for building the Mauton Laundry customer-facing mobile application.

**Base URL:** `https://your-api-domain.com`

---

## 1. Authentication

### 1.1. User Registration

Creates a new customer account and triggers a verification email.

- **Endpoint:** `POST /api/auth/register`
- **Access:** Public

**Request Body:**
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

> **Note:** Only `firstname`, `second_name`, `email`, `password`, and `phone_number` are required. All address fields are optional and can be added later.

**Success Response (201 Created):**
```json
{
  "email": "john.doe@example.com",
  "message": "Registration successful. Please check your email for verification."
}
```

### 1.2. User Login

Authenticates the user and returns a JWT for session management.

- **Endpoint:** `POST /api/auth/login`
- **Access:** Public

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "securepassword123"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer"
}
```
> **Note for Frontend:** The `accessToken` is a JWT. Decode it to get user details like `sub` (email) and `role`. Store it securely and include it in the `Authorization` header for all subsequent authenticated requests: `Authorization: Bearer <token>`.

### 1.3. Forgot Password

Initiates the password reset process by sending an email to the user.

- **Endpoint:** `POST /api/auth/forgot-password`
- **Access:** Public

**Request (Query Parameter):**
- `email`: The user's email address.
- Example: `/api/auth/forgot-password?email=john.doe@example.com`

**Success Response (200 OK):**
```json
"Password reset email sent"
```

### 1.4. Reset Password

Sets a new password using the token from the reset email.

- **Endpoint:** `POST /api/auth/reset-password`
- **Access:** Public

**Request Body:**
```json
{
  "token": "the_token_from_the_email_link",
  "newPassword": "a_new_strong_password"
}
```

**Success Response (200 OK):**
```json
"Password reset successfully"
```

---

## 2. User Profile & Data

### 2.1. View Current User Profile

Fetches the profile details of the currently logged-in user including addresses.

- **Endpoint:** `GET /api/v1/users/me`
- **Access:** Authenticated

**Success Response (200 OK):**
```json
{
  "id": "user-uuid-123",
  "email": "john.doe@example.com",
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

### 2.2. Update User Profile

Updates the profile details of the currently logged-in user (name and phone only).

- **Endpoint:** `PUT /api/v1/users/profile`
- **Access:** Authenticated

**Request Body:**
```json
{
  "fullName": "Johnathan Doe",
  "phoneNumber": "+1987654321"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Profile updated successfully"
}
```

---

## 3. Address Management

### 3.1. List User's Addresses

Retrieves all saved addresses for the logged-in user.

- **Endpoint:** `GET /api/v1/addresses`
- **Access:** Authenticated

**Success Response (200 OK):**
```json
[
  {
    "id": "address-uuid-456",
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
  },
  {
    "id": "address-uuid-789",
    "street": "456 Business Rd",
    "street_number": 456,
    "city": "Anytown",
    "state": "CA",
    "zip": "12346",
    "country": "USA",
    "latitude": null,
    "longitude": null,
    "isDefault": false,
    "deleted": false
  }
]
```

### 3.2. Add a New Address

Saves a new address for the logged-in user.

- **Endpoint:** `POST /api/v1/addresses`
- **Access:** Authenticated

**Request Body:**
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
> **Note:** `latitude` and `longitude` are optional fields.

**Success Response (201 Created):** The newly created `Address` object.

### 3.3. Update an Address

Updates a specific, existing address.

- **Endpoint:** `PUT /api/v1/addresses/{addressId}`
- **Access:** Authenticated

**Request Body:**
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

**Success Response (200 OK):** The updated `Address` object.

### 3.4. Delete an Address

Removes an address from the user's profile (soft delete).

- **Endpoint:** `DELETE /api/v1/addresses/{addressId}`
- **Access:** Authenticated

**Success Response (204 No Content):** No response body.

---

## 4. Services & Booking

### 4.1. Get Available Laundry Items

Fetches the list of all items available for laundry services.

- **Endpoint:** `GET /api/v1/laundry-items`
- **Access:** Authenticated (`LAUNDRY_ITEM_READ` permission)

**Success Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "T-Shirt",
    "unit": "PCS",
    "basePriceColored": 5.00,
    "basePriceWhite": 7.00
  },
  {
    "id": 2,
    "name": "Trousers",
    "unit": "PCS",
    "basePriceColored": 8.00,
    "basePriceWhite": 10.00
  }
]
```

### 4.2. Calculate Order Price

Calculates the estimated total price for a potential order before booking.

- **Endpoint:** `POST /api/v1/pricing/calculate`
- **Access:** Public

**Request Body:**
```json
{
  "items": [
    {
      "itemId": 1,
      "quantity": 5,
      "colorType": "COLORED"
    },
    {
      "itemId": 2,
      "quantity": 2,
      "colorType": "WHITE"
    }
  ],
  "express": true,
  "deliveryDistance": 5.0
}
```

**Success Response (200 OK):**
```json
{
  "totalPrice": 55.00
}
```

### 4.3. Create a Booking

Submits a new laundry order.

- **Endpoint:** `POST /api/v1/bookings`
- **Access:** Authenticated (`BOOKING_CREATE` permission)

**Request Body:**
```json
{
  "bookingType": "LAUNDRY",
  "pickupAddressId": "address-uuid-456",
  "express": true,
  "items": [
    {
      "itemId": 1,
      "quantity": 5,
      "colorType": "COLORED"
    },
    {
      "itemId": 2,
      "quantity": 2,
      "colorType": "WHITE"
    }
  ]
}
```

**Success Response (201 Created):**
```json
{
  "id": "booking-uuid-abc",
  "trackingNumber": "TRK167589234",
  "totalPrice": 55.00,
  "returnDate": "2026-02-03T18:30:00",
  "status": "CREATED"
}
```

---

## 5. Order Tracking

### 5.1. List User's Bookings

Fetches a list of all past and current bookings for the logged-in user.

- **Endpoint:** `GET /api/v1/bookings`
- **Access:** Authenticated (`BOOKING_READ` permission)
- **Note:** This endpoint might be paginated in the future.

**Success Response (200 OK):** A list of `BookingDetailsResponse` objects.
```json
[
  {
    "id": "booking-uuid-abc",
    "trackingNumber": "TRK167589234",
    "status": "DELIVERED",
    "totalPrice": 55.00,
    "createdAt": "2026-01-27T18:30:00"
  },
  {
    "id": "booking-uuid-def",
    "trackingNumber": "TRK167598712",
    "status": "WASHING",
    "totalPrice": 34.00,
    "createdAt": "2026-02-10T11:00:00"
  }
]
```

### 5.2. Get Booking Details

Fetches the detailed information and status for a single booking.

- **Endpoint:** `GET /api/v1/bookings/{bookingId}`
- **Access:** Authenticated (`BOOKING_READ` permission)

**Success Response (200 OK):** A single `BookingDetailsResponse` object.
```json
{
  "id": "booking-uuid-def",
  "trackingNumber": "TRK167598712",
  "bookingType": "LAUNDRY",
  "status": "WASHING",
  "totalPrice": 34.00,
  "deliveryFee": 5.00,
  "express": false,
  "returnDate": "2026-02-17T11:00:00",
  "createdAt": "2026-02-10T11:00:00",
  "pickupAddress": {
    "label": "Home",
    "line1": "123 Main St",
    "city": "Anytown"
  }
}
```
