# Imototo API Endpoints - Complete Reference

**Base URL**: `http://localhost:8079`  
**Authentication**: JWT Bearer Token (except registration and login)

---

## 🔐 Authentication Endpoints

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
  "address": "123 Main Street"
}

Response:
{
  "email": "john@example.com"
}
```

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
  "tokenType": "Bearer"
}
```

### User Logout
```http
POST /api/auth/logout
Authorization: Bearer {token}

Response: "Logged out successfully"
```

### Email Verification
```http
POST /api/auth/send-verification?email=user@example.com

POST /api/auth/verify-email
Content-Type: application/json
{
  "token": "verification_token"
}
```

### Password Reset
```http
POST /api/auth/forgot-password?email=user@example.com

POST /api/auth/reset-password
Content-Type: application/json
{
  "token": "reset_token",
  "newPassword": "newPassword123"
}
```

---

## 👤 User Management Endpoints

### Get User Profile
```http
GET /api/v1/users/getUser/{email}
Authorization: Bearer {token}

Response:
{
  "id": "uuid-string",
  "email": "john@example.com",
  "full_name": "John Doe",
  "address": "123 Main Street",
  "phone_number": "+1234567890",
  "userRole": "USER"
}
```

### Update User Profile
```http
PUT /updateUser
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "id": "user_uuid",
  "firstname": "John",
  "second_name": "Doe",
  "address": "New Address",
  "phone_number": "+1234567890"
}

Response:
{
  "message": "Details Updated Successfully"
}
```

---

## 📋 Booking Management Endpoints

### Create Booking
```http
POST /registerBooking
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "userId": "user_uuid",
  "addressId": "address_uuid",
  "urgency": "NORMAL",
  "serviceDetails": [
    {
      "serviceId": 1,
      "white": true
    },
    {
      "serviceId": 2,
      "white": false
    }
  ]
}

Response:
{
  "id": 1,
  "message": "Booking created successfully"
}
```

### Get Single Booking
```http
GET /viewBooking/{id}
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "type_of_service": "LAUNDRY",
  "service": "[\"Wash\", \"Dry\", \"Fold\"]",
  "date_booked": "2024-01-20T10:00:00",
  "urgency": "NORMAL",
  "laundryStatus": "PENDING",
  "user": {
    "email": "john@example.com",
    "full_name": "John Doe"
  }
}
```

### Get All Bookings
```http
GET /viewAllBooking
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "type_of_service": "LAUNDRY",
    "service": "[\"Wash\", \"Dry\", \"Fold\"]",
    "date_booked": "2024-01-20T10:00:00",
    "urgency": "NORMAL",
    "laundryStatus": "PENDING",
    "deleted": false
  }
]
```

### Update Booking Status
```http
PUT /updateBooking
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "id": 1,
  "laundryStatus": "IN_PROGRESS"
}

Response:
{
  "message": "Booking updated successfully"
}
```

### Delete Booking
```http
DELETE /deleteBooking/{id}
Authorization: Bearer {token}

Response: 200 OK (empty body)
```

---

## 📊 Admin Analytics Endpoints (Admin Only)

### Dashboard Analytics
```http
GET /api/admin/analytics/dashboard
Authorization: Bearer {admin_token}

Response:
{
  "totalUsers": 150,
  "activeUsers": 142,
  "totalBookings": 89,
  "todayBookings": 5,
  "totalRevenue": 15420.50,
  "todayRevenue": 320.00,
  "todayLogins": 12,
  "recentActivities": [
    {
      "id": "uuid",
      "userId": "user_uuid",
      "userEmail": "user@example.com",
      "action": "LOGIN",
      "resource": "AUTH",
      "timestamp": "2024-01-20T10:00:00"
    }
  ]
}
```

### Users by Role Distribution
```http
GET /api/admin/analytics/users-by-role
Authorization: Bearer {admin_token}

Response:
{
  "ADMIN": 5,
  "USER": 120,
  "LAUNDRY_AGENT": 15,
  "DELIVERY_AGENT": 10
}
```

### Monthly Statistics
```http
GET /api/admin/analytics/monthly-stats
Authorization: Bearer {admin_token}

Response:
{
  "monthlyBookings": 45,
  "monthlyRevenue": 5420.50,
  "monthlyNewUsers": 25
}
```

---

## 🚚 Delivery Management Endpoints

### Create Delivery
```http
POST /createDelivery
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "booking_id": 1,
  "userAddress": "123 Main Street",
  "date_booked": "2024-01-20T10:00:00",
  "urgency": "NORMAL"
}

Response:
{
  "id": 1,
  "message": "Delivery details created successfully"
}
```

### Get Delivery by ID
```http
GET /getDelivery/{id}
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "bookingId": 1,
  "userAddress": "123 Main Street",
  "pick_up_date": "2024-01-22T10:00:00",
  "return_date": "2024-01-29T10:00:00",
  "deliveryStatus": "PENDING_PICKUP",
  "urgency": "NORMAL"
}
```

### Update Delivery Status
```http
PUT /updateDeliveryStatus
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "id": 1,
  "deliveryStatus": "PICKED_UP"
}

Response:
{
  "message": "Delivery status updated successfully"
}
```

---

## 💳 Payment Endpoints

### Create Payment
```http
POST /createPayment
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "booking_id": 1,
  "amount": 25.50,
  "paymentMethod": "CARD",
  "transactionId": "txn_123456"
}

Response:
{
  "id": 1,
  "status": "SUCCESS",
  "message": "Payment processed successfully"
}
```

### Get Payment Details
```http
GET /getPayment/{id}
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "booking": {
    "id": 1,
    "type_of_service": "LAUNDRY"
  },
  "amount": 25.50,
  "paymentMethod": "CARD",
  "status": "SUCCESS",
  "transactionId": "txn_123456",
  "paymentDate": "2024-01-20T10:00:00"
}
```

---

## 🛠 Service Management Endpoints

### Get Available Services
```http
GET /api/services
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "name": "Wash & Fold",
    "category": "LAUNDRY",
    "price": 15.00,
    "white": 2.00,
    "description": "Basic wash and fold service"
  }
]
```

### Get Service by ID
```http
GET /api/services/{id}
Authorization: Bearer {token}

Response:
{
  "id": 1,
  "name": "Wash & Fold",
  "category": "LAUNDRY",
  "price": 15.00,
  "white": 2.00,
  "description": "Basic wash and fold service"
}
```

### Get Services by Category
```http
GET /api/services/category/{category}
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "name": "Wash & Fold",
    "category": "LAUNDRY",
    "price": 15.00,
    "white": 2.00,
    "description": "Basic wash and fold service"
  }
]
```

### Create Service (Admin Only)
```http
POST /api/services
Authorization: Bearer {admin_token}
Content-Type: application/json

Request:
{
  "name": "Express Wash",
  "category": "LAUNDRY",
  "price": 20.00,
  "white": 3.00,
  "description": "Fast wash service"
}

Response:
{
  "id": 2,
  "name": "Express Wash",
  "category": "LAUNDRY",
  "price": 20.00,
  "white": 3.00,
  "description": "Fast wash service"
}
```

### Delete Service (Admin Only)
```http
DELETE /api/services/{id}
Authorization: Bearer {admin_token}

Response: 204 No Content
```

---

## 📱 Mobile App Specific Endpoints

### Get User Dashboard Data
```http
GET /api/v1/users/getUserDashboard/{email}
Authorization: Bearer {token}

Response:
{
  "user": {
    "id": "uuid",
    "email": "john@example.com",
    "full_name": "John Doe",
    "userRole": "USER"
  },
  "activeBookings": 2,
  "completedBookings": 15,
  "totalSpent": 450.00,
  "recentBookings": [...]
}
```

### Get Notifications
```http
GET /getNotifications/{userId}
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "title": "Booking Confirmed",
    "message": "Your laundry booking has been confirmed",
    "type": "BOOKING_UPDATE",
    "read": false,
    "timestamp": "2024-01-20T10:00:00"
  }
]
```

---

## 🔍 User Permissions Endpoint

### Get User Permissions
```http
GET /api/user/permissions
Authorization: Bearer {token}

Response:
{
  "userRole": "USER",
  "permissions": [
    "BOOKING_CREATE",
    "BOOKING_VIEW",
    "PAYMENT_VIEW"
  ],
  "pages": {
    "dashboard": true,
    "book_service": true,
    "my_bookings": true,
    "booking_history": true,
    "payment_history": true,
    "track_delivery": true,
    "profile": true,
    "notifications": true,
    "settings": true
  }
}
```

---

## 🔍 Search & Filter Endpoints

### Search Bookings
```http
GET /searchBookings?status=PENDING&date_from=2024-01-01&date_to=2024-01-31
Authorization: Bearer {token}

Response: [
  {
    "id": 1,
    "type_of_service": "LAUNDRY",
    "laundryStatus": "PENDING",
    "date_booked": "2024-01-20T10:00:00"
  }
]
```

---

## 📋 Enums & Constants

### Service Types
- `CLEANING`
- `LAUNDRY`

### Urgency Types
- `NORMAL` (7 days processing)
- `EXPRESS` (3 days processing)

### Laundry Status
- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

### Delivery Status
- `PENDING_PICKUP`
- `PICKED_UP`
- `IN_TRANSIT`
- `DELIVERED`

### Payment Methods
- `CARD`
- `CASH`
- `TRANSFER`

### Payment Status
- `PENDING`
- `SUCCESS`
- `FAILED`
- `REFUNDED`

### User Roles
- `ADMIN`
- `USER`
- `LAUNDRY_AGENT`
- `DELIVERY_AGENT`

---

## 🚨 Error Responses

### Common Error Format
```json
{
  "timestamp": "2024-01-20T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/endpoint"
}
```

### HTTP Status Codes
- `200` - Success
- `201` - Created
- `400` - Bad Request
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `500` - Internal Server Error

---

## 🔒 Authentication Headers

All protected endpoints require:
```
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

## 📝 Notes for Frontend Developers

1. **Always include Authorization header** for protected endpoints
2. **Handle token expiration** - redirect to login on 401 errors
3. **Role-based UI** - check user role before showing features
4. **Error handling** - display user-friendly error messages
5. **Loading states** - show loading indicators during API calls
6. **Offline support** - cache essential data locally
7. **Input validation** - validate data before sending to API

This comprehensive API reference covers all endpoints available in the Imototo laundry management system.