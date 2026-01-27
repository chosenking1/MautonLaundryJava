# Laundry Platform API Endpoints - Complete Reference

**Base URL**: `http://localhost:8079`  
**API Version**: `/api/v1`  
**Authentication**: JWT Bearer Token (except registration and login)

---

## 🔐 Authentication & Authorization

### User Registration
```http
POST /register
Content-Type: application/json

Request:
{
  "firstname": "John",
  "second_name": "Doe", 
  "email": "john@example.com",
  "phone_number": "+1234567890",
  "password": "password123",
  "address": "123 Main Street",
  "role": "CUSTOMER"
}

Response:
{
  "email": "john@example.com"
}
```

**Allowed Registration Roles:**
- `CUSTOMER`
- `LAUNDRYMAN` 
- `DELIVERY_AGENT`

**Note:** ADMIN role cannot be created from frontend

### User Login
```http
POST /api/auth/login
Content-Type: application/json

Request:
{
  "email": "john@example.com",
  "password": "password123"
}

Response:
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "user": {
    "id": "uuid-string",
    "email": "john@example.com",
    "fullName": "John Doe",
    "roles": ["CUSTOMER"]
  }
}
```

### User Logout
```http
POST /api/auth/logout
Authorization: Bearer {token}

Response: "Logged out successfully"
```

---

## 👤 User Management

### Get User Profile
```http
GET /api/v1/users/{userId}
Authorization: Bearer {token}

Response:
{
  "id": "uuid-string",
  "email": "john@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "addresses": [
    {
      "id": 1,
      "label": "Home",
      "line1": "123 Main Street",
      "city": "New York",
      "latitude": 40.7128,
      "longitude": -74.0060
    }
  ],
  "roles": ["CUSTOMER"]
}
```

### Update User Profile
```http
PUT /api/v1/users/{userId}
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "fullName": "John Updated Doe",
  "phoneNumber": "+1234567890"
}

Response:
{
  "message": "Profile updated successfully"
}
```

### Get User Role
```http
GET /api/v1/users/{userId}/role
Authorization: Bearer {token}

Response:
{
  "userId": "uuid-string",
  "currentRole": "CUSTOMER",
  "roleStatus": "ACTIVE"
}
```

---

## 🏠 Address Management

### Add Address
```http
POST /api/v1/users/{userId}/addresses
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "label": "Office",
  "line1": "456 Business Ave",
  "line2": "Suite 100",
  "city": "New York",
  "state": "NY",
  "country": "USA",
  "postalCode": "10001",
  "latitude": 40.7589,
  "longitude": -73.9851
}

Response:
{
  "id": 2,
  "message": "Address added successfully"
}
```

### Get User Addresses
```http
GET /api/v1/users/{userId}/addresses
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "label": "Home",
    "line1": "123 Main Street",
    "city": "New York",
    "latitude": 40.7128,
    "longitude": -74.0060
  }
]
```

---

## 🛠️ Service Management

### Get All Services
```http
GET /api/v1/services
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "name": "Shirt Wash",
    "unit": "item",
    "basePriceColored": 5.00,
    "basePriceWhite": 6.00,
    "isActive": true
  }
]
```

### Get Service by ID
```http
GET /api/v1/services/{serviceId}
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "name": "Shirt Wash",
  "unit": "item", 
  "basePriceColored": 5.00,
  "basePriceWhite": 6.00,
  "isActive": true
}
```

### Create Service (Admin Only)
```http
POST /api/v1/services
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "name": "Trouser Wash",
  "unit": "pair",
  "basePriceColored": 8.00,
  "basePriceWhite": 9.00
}

Response:
{
  "id": 2,
  "message": "Service created successfully"
}
```

---

## 📋 Booking Management

### Create Laundry Booking
```http
POST /api/v1/bookings
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "bookingType": "LAUNDRY",
  "pickupAddressId": 1,
  "express": false,
  "items": [
    {
      "itemId": 1,
      "quantity": 3,
      "colorType": "WHITE"
    },
    {
      "itemId": 2, 
      "quantity": 2,
      "colorType": "COLORED"
    }
  ]
}

Response:
{
  "id": "booking-uuid",
  "trackingNumber": "TRK123456789",
  "totalPrice": 34.00,
  "deliveryFee": 5.00,
  "returnDate": "2024-02-01T10:00:00",
  "status": "CREATED"
}
```

### Create Cleaning Booking
```http
POST /api/v1/bookings
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "bookingType": "CLEANING",
  "pickupAddressId": 1,
  "scheduledDate": "2024-01-25T14:00:00",
  "cleaningType": "DEEP_CLEAN",
  "rooms": 3,
  "estimatedHours": 4
}

Response:
{
  "id": "booking-uuid",
  "trackingNumber": "CLN123456789", 
  "totalPrice": 120.00,
  "scheduledDate": "2024-01-25T14:00:00",
  "status": "CREATED"
}
```

### Get User Bookings
```http
GET /api/v1/users/{userId}/bookings?status=ACTIVE&page=0&size=10
Authorization: Bearer {token}

Response:
{
  "content": [
    {
      "id": "booking-uuid",
      "bookingType": "LAUNDRY",
      "status": "PICKED_UP",
      "trackingNumber": "TRK123456789",
      "totalPrice": 34.00,
      "returnDate": "2024-02-01T10:00:00",
      "createdAt": "2024-01-25T10:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Get Booking Details
```http
GET /api/v1/bookings/{bookingId}
Authorization: Bearer {token}

Response:
{
  "id": "booking-uuid",
  "bookingType": "LAUNDRY",
  "status": "WASHING",
  "trackingNumber": "TRK123456789",
  "totalPrice": 34.00,
  "deliveryFee": 5.00,
  "express": false,
  "returnDate": "2024-02-01T10:00:00",
  "pickupAddress": {
    "label": "Home",
    "line1": "123 Main Street",
    "city": "New York"
  },
  "items": [
    {
      "itemName": "Shirt Wash",
      "quantity": 3,
      "colorType": "WHITE",
      "unitPrice": 6.00,
      "totalPrice": 18.00
    }
  ],
  "statusHistory": [
    {
      "status": "CREATED",
      "timestamp": "2024-01-25T10:00:00"
    },
    {
      "status": "PICKED_UP", 
      "timestamp": "2024-01-25T14:00:00"
    }
  ]
}
```

### Update Booking Status (Agent Only)
```http
PATCH /api/v1/bookings/{bookingId}/status
Authorization: Bearer {agent_token}
Content-Type: application/json

Request:
{
  "status": "WASHING",
  "notes": "Items received and processing started"
}

Response:
{
  "message": "Status updated successfully"
}
```

---

## 🚚 Delivery & Assignment Management

### Assign Delivery Agent (Admin Only)
```http
POST /api/v1/bookings/{bookingId}/assign-delivery
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "deliveryAgentId": "agent-uuid"
}

Response:
{
  "message": "Delivery agent assigned successfully"
}
```

### Assign Laundryman (Admin Only)
```http
POST /api/v1/bookings/{bookingId}/assign-laundryman
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "laundrymanId": "laundryman-uuid"
}

Response:
{
  "message": "Laundryman assigned successfully"
}
```

### Get Agent Assignments
```http
GET /api/v1/agents/{agentId}/assignments?status=ASSIGNED
Authorization: Bearer {token}

Response: [
  {
    "bookingId": "booking-uuid",
    "trackingNumber": "TRK123456789",
    "status": "ASSIGNED",
    "pickupAddress": {
      "line1": "123 Main Street",
      "city": "New York"
    },
    "assignedAt": "2024-01-25T10:00:00"
  }
]
```

---

## 📍 Location Tracking

### Update Location (Agent Only)
```http
POST /api/v1/bookings/{bookingId}/location
Authorization: Bearer {agent_token}
Content-Type: application/json

Request:
{
  "latitude": 40.7128,
  "longitude": -74.0060
}

Response:
{
  "message": "Location updated successfully"
}
```

### Get Booking Location
```http
GET /api/v1/bookings/{bookingId}/location
Authorization: Bearer {token}

Response:
{
  "latitude": 40.7128,
  "longitude": -74.0060,
  "lastUpdated": "2024-01-25T15:30:00",
  "agentName": "John Delivery"
}
```

---

## 💰 Pricing & Configuration

### Get Pricing Config
```http
GET /api/v1/pricing/config
Authorization: Bearer {token}

Response:
{
  "expressFee": 10.00,
  "deliveryFee": 5.00,
  "freeDeliveryThreshold": 50.00
}
```

### Calculate Booking Price
```http
POST /api/v1/pricing/calculate
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "items": [
    {
      "itemId": 1,
      "quantity": 3,
      "colorType": "WHITE"
    }
  ],
  "express": true,
  "deliveryDistance": 5.2
}

Response:
{
  "itemsTotal": 18.00,
  "expressFee": 10.00,
  "deliveryFee": 5.00,
  "totalPrice": 33.00
}
```

### Update Pricing Config (Admin Only)
```http
PUT /api/v1/pricing/config
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "expressFee": 12.00,
  "deliveryFee": 6.00,
  "freeDeliveryThreshold": 60.00
}

Response:
{
  "message": "Pricing configuration updated successfully"
}
```

---

## 👑 Admin Role Management

### Create Role Change Request (Admin Only)
```http
POST /api/v1/admin/role-requests
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "userId": "user-uuid",
  "requestedRole": "LAUNDRYMAN"
}

Response:
{
  "requestId": 123,
  "message": "Role change request created successfully"
}
```

### Get Pending Role Requests (Admin Only)
```http
GET /api/v1/admin/role-requests?status=PENDING
Authorization: Bearer {admin_token}

Response: [
  {
    "requestId": 123,
    "userId": "user-uuid",
    "userEmail": "john@example.com",
    "currentRole": "CUSTOMER",
    "requestedRole": "LAUNDRYMAN",
    "requestedBy": "admin1@example.com",
    "createdAt": "2024-01-25T10:00:00"
  }
]
```

### Approve/Reject Role Request (Admin Only)
```http
PATCH /api/v1/admin/role-requests/{requestId}
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "status": "APPROVED",
  "notes": "User has required experience"
}

Response:
{
  "message": "Role request approved successfully"
}
```

---

## 📊 Admin Analytics & Reports

### Dashboard Analytics (Admin Only)
```http
GET /api/v1/admin/analytics/dashboard
Authorization: Bearer {admin_token}

Response:
{
  "totalUsers": 150,
  "activeBookings": 45,
  "todayRevenue": 1250.00,
  "monthlyRevenue": 15420.50,
  "usersByRole": {
    "CUSTOMER": 120,
    "LAUNDRYMAN": 15,
    "DELIVERY_AGENT": 10,
    "ADMIN": 5
  },
  "bookingsByStatus": {
    "CREATED": 5,
    "PICKED_UP": 12,
    "WASHING": 8,
    "DELIVERED": 20
  }
}
```

### Revenue Reports (Admin Only)
```http
GET /api/v1/admin/reports/revenue?startDate=2024-01-01&endDate=2024-01-31
Authorization: Bearer {admin_token}

Response:
{
  "totalRevenue": 15420.50,
  "totalBookings": 89,
  "averageOrderValue": 173.26,
  "dailyBreakdown": [
    {
      "date": "2024-01-01",
      "revenue": 450.00,
      "bookings": 3
    }
  ]
}
```

---

## 🔍 Search & Filtering

### Search Bookings
```http
GET /api/v1/bookings/search?q=TRK123&status=ACTIVE&startDate=2024-01-01
Authorization: Bearer {token}

Response: [
  {
    "id": "booking-uuid",
    "trackingNumber": "TRK123456789",
    "status": "PICKED_UP",
    "totalPrice": 34.00,
    "createdAt": "2024-01-25T10:00:00"
  }
]
```

### Search Users (Admin Only)
```http
GET /api/v1/admin/users/search?q=john&role=CUSTOMER
Authorization: Bearer {admin_token}

Response: [
  {
    "id": "user-uuid",
    "email": "john@example.com",
    "fullName": "John Doe",
    "roles": ["CUSTOMER"],
    "createdAt": "2024-01-20T10:00:00"
  }
]
```

---

## 📱 Notifications

### Get User Notifications
```http
GET /api/v1/users/{userId}/notifications?unread=true
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "title": "Booking Confirmed",
    "message": "Your laundry booking TRK123456789 has been confirmed",
    "type": "BOOKING_UPDATE",
    "read": false,
    "createdAt": "2024-01-25T10:00:00"
  }
]
```

### Mark Notification as Read
```http
PATCH /api/v1/notifications/{notificationId}/read
Authorization: Bearer {token}

Response:
{
  "message": "Notification marked as read"
}
```

---

## 📋 Audit Logs (Admin Only)

### Get Audit Logs
```http
GET /api/v1/admin/audit-logs?targetType=BOOKING&startDate=2024-01-01
Authorization: Bearer {admin_token}

Response: [
  {
    "id": 1,
    "actorEmail": "admin@example.com",
    "action": "UPDATE_BOOKING_STATUS",
    "targetType": "BOOKING",
    "targetId": "booking-uuid",
    "oldValue": {"status": "CREATED"},
    "newValue": {"status": "PICKED_UP"},
    "createdAt": "2024-01-25T10:00:00"
  }
]
```

---

## 🚨 Error Responses

### Standard Error Format
```json
{
  "timestamp": "2024-01-25T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for field 'email'",
  "path": "/api/v1/users",
  "details": {
    "field": "email",
    "rejectedValue": "invalid-email",
    "message": "Email format is invalid"
  }
}
```

### HTTP Status Codes
- `200` - Success
- `201` - Created
- `204` - No Content (successful delete)
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (invalid/missing token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `409` - Conflict (duplicate resource)
- `500` - Internal Server Error

---

## 🔒 Authentication Headers

All protected endpoints require:
```
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

---

## 📝 Business Rules Summary

### Booking Rules
- Customers can only edit bookings before `PICKED_UP` status
- Express bookings have 3-day return, normal bookings have 7-day return
- Delivery fee waived if order total exceeds free delivery threshold
- Prices are locked at booking time (immune to catalog changes)

### Role Management Rules
- Only CUSTOMER, LAUNDRYMAN, DELIVERY_AGENT can register via frontend
- ADMIN role requires maker-checker approval workflow
- Users cannot change their own roles
- Admins cannot approve their own role change requests

### Assignment Rules
- Only admins can assign agents to bookings
- Agents can only update status of their assigned bookings
- Location tracking only available for active assignments

---

## 🔄 Booking Status Flow

### Laundry Booking States
```
CREATED → CONFIRMED → PICKUP_ASSIGNED → PICKED_UP → 
RECEIVED_BY_LAUNDRYMAN → WASHING → READY_FOR_DELIVERY → 
OUT_FOR_DELIVERY → DELIVERED
```

### Cleaning Booking States
```
CREATED → CONFIRMED → CLEANER_ASSIGNED → IN_PROGRESS → COMPLETED
```

---

This comprehensive API reference covers all endpoints needed for the Laundry Platform frontend development.