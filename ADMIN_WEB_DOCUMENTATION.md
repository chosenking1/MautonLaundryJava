# MautonLaundry Admin Web Application Documentation

## Application Overview

MautonLaundry is a comprehensive laundry service management system that provides complete business operations from user registration to payment processing. The admin web application provides administrators with full control over the system operations, user management, and business analytics.

## Core Business Workflow

### 1. User Management Flow
```
User Registration → Email Verification → Role Assignment → Account Activation
```

### 2. Booking Management Flow
```
Customer Creates Booking → Admin Assigns Agents → Pickup → Processing → Delivery → Payment
```

### 3. Service Management Flow
```
Admin Creates Services → Sets Pricing → Manages Catalog → Updates Availability
```

---

## Admin Functionalities & Endpoints

### 🔐 Authentication & Authorization

#### Admin Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@mautonlaundry.com",
  "password": "adminPassword"
}

Response:
{
  "accessToken": "jwt_token_here",
  "tokenType": "Bearer"
}
```

#### Admin Logout
```http
POST /api/auth/logout
Authorization: Bearer {admin_token}
```

---

### 👥 User Management

#### Get All Users
```http
GET /api/admin/users?page=0&size=20&role=USER
Authorization: Bearer {admin_token}

Response:
{
  "content": [
    {
      "id": "user-uuid",
      "email": "user@example.com",
      "fullName": "John Doe",
      "userRole": "USER",
      "emailVerified": true,
      "createdAt": "2024-01-20T10:00:00",
      "deleted": false
    }
  ],
  "totalElements": 150,
  "totalPages": 8
}
```

#### Get User Details
```http
GET /api/v1/users/{userId}
Authorization: Bearer {admin_token}

Response:
{
  "id": "user-uuid",
  "email": "user@example.com",
  "fullName": "John Doe",
  "phoneNumber": "+1234567890",
  "userRole": "USER",
  "emailVerified": true,
  "addresses": [...],
  "createdAt": "2024-01-20T10:00:00"
}
```

#### Update User Role
```http
PUT /api/admin/users/{userId}/role
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "newRole": "LAUNDRYMAN"
}

Response:
{
  "message": "User role updated successfully"
}
```

#### Deactivate/Activate User
```http
PATCH /api/admin/users/{userId}/status
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "active": false
}
```

---

### 📋 Booking Management

#### Get All Bookings
```http
GET /api/admin/bookings?status=PENDING&page=0&size=20
Authorization: Bearer {admin_token}

Response:
{
  "content": [
    {
      "id": "booking-uuid",
      "trackingNumber": "TRK123456789",
      "customerName": "John Doe",
      "customerEmail": "john@example.com",
      "bookingType": "LAUNDRY",
      "status": "PENDING",
      "totalPrice": 45.00,
      "createdAt": "2024-01-20T10:00:00",
      "pickupAddress": "123 Main St, City"
    }
  ],
  "totalElements": 89,
  "totalPages": 5
}
```

#### Get Booking Details
```http
GET /api/v1/bookings/{bookingId}
Authorization: Bearer {admin_token}

Response:
{
  "id": "booking-uuid",
  "trackingNumber": "TRK123456789",
  "customer": {
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+1234567890"
  },
  "bookingType": "LAUNDRY",
  "status": "WASHING",
  "items": [
    {
      "itemName": "Shirt Wash",
      "quantity": 3,
      "colorType": "WHITE",
      "unitPrice": 6.00,
      "totalPrice": 18.00
    }
  ],
  "totalPrice": 45.00,
  "deliveryFee": 5.00,
  "express": false,
  "pickupAddress": {...},
  "assignedAgents": {
    "laundryman": "agent@example.com",
    "deliveryAgent": "delivery@example.com"
  },
  "statusHistory": [...]
}
```

#### Assign Laundryman
```http
POST /api/admin/bookings/{bookingId}/assign-laundryman
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "laundrymanId": "agent-uuid"
}
```

#### Assign Delivery Agent
```http
POST /api/admin/bookings/{bookingId}/assign-delivery
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "deliveryAgentId": "agent-uuid"
}
```

#### Update Booking Status
```http
PATCH /api/v1/bookings/{bookingId}/status
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "status": "PICKED_UP",
  "notes": "Items collected successfully"
}
```

---

### 🛠️ Service & Pricing Management

#### Get All Services
```http
GET /api/v1/services
Authorization: Bearer {admin_token}

Response: [
  {
    "id": 1,
    "name": "Shirt Wash",
    "basePriceColored": 5.00,
    "basePriceWhite": 6.00,
    "isActive": true,
    "createdAt": "2024-01-15T10:00:00"
  }
]
```

#### Create New Service
```http
POST /api/v1/services
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Premium Suit Cleaning",
  "basePriceColored": 25.00,
  "basePriceWhite": 30.00,
  "description": "Professional suit cleaning service"
}

Response:
{
  "id": 5,
  "message": "Service created successfully"
}
```

#### Update Service
```http
PUT /api/v1/services/{serviceId}
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Updated Service Name",
  "basePriceColored": 28.00,
  "basePriceWhite": 32.00,
  "isActive": true
}
```

#### Delete Service
```http
DELETE /api/v1/services/{serviceId}
Authorization: Bearer {admin_token}
```

#### Get Pricing Configuration
```http
GET /api/v1/pricing/config
Authorization: Bearer {admin_token}

Response:
{
  "expressFee": 10.00,
  "deliveryFee": 5.00,
  "freeDeliveryThreshold": 50.00
}
```

#### Update Pricing Configuration
```http
PUT /api/v1/pricing/config
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "expressFee": 12.00,
  "deliveryFee": 6.00,
  "freeDeliveryThreshold": 60.00
}
```

---

### 📊 Analytics & Reports

#### Dashboard Analytics
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
  "monthlyRevenue": 5420.50,
  "todayLogins": 12,
  "usersByRole": {
    "USER": 120,
    "LAUNDRYMAN": 15,
    "DELIVERY_AGENT": 10,
    "ADMIN": 5
  },
  "bookingsByStatus": {
    "PENDING": 5,
    "IN_PROGRESS": 12,
    "COMPLETED": 67,
    "CANCELLED": 5
  },
  "recentActivities": [
    {
      "id": "activity-uuid",
      "userId": "user-uuid",
      "userEmail": "user@example.com",
      "action": "LOGIN",
      "resource": "AUTH",
      "timestamp": "2024-01-20T10:00:00"
    }
  ]
}
```

#### Revenue Reports
```http
GET /api/admin/reports/revenue?startDate=2024-01-01&endDate=2024-01-31
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
  ],
  "monthlyGrowth": 15.5
}
```

#### User Statistics
```http
GET /api/admin/analytics/users-by-role
Authorization: Bearer {admin_token}

Response:
{
  "USER": 120,
  "LAUNDRYMAN": 15,
  "DELIVERY_AGENT": 10,
  "ADMIN": 5
}
```

---

### 🚚 Agent Management

#### Get All Agents
```http
GET /api/admin/agents?role=LAUNDRYMAN&page=0&size=20
Authorization: Bearer {admin_token}

Response:
{
  "content": [
    {
      "id": "agent-uuid",
      "email": "agent@example.com",
      "fullName": "Agent Name",
      "userRole": "LAUNDRYMAN",
      "activeAssignments": 3,
      "totalCompleted": 45,
      "rating": 4.8,
      "isAvailable": true
    }
  ]
}
```

#### Get Agent Assignments
```http
GET /api/admin/agents/{agentId}/assignments?status=ACTIVE
Authorization: Bearer {admin_token}

Response: [
  {
    "bookingId": "booking-uuid",
    "trackingNumber": "TRK123456789",
    "customerName": "John Doe",
    "status": "ASSIGNED",
    "assignedAt": "2024-01-20T10:00:00",
    "dueDate": "2024-01-22T18:00:00"
  }
]
```

---

### 🔍 Audit & Monitoring

#### Get Audit Logs
```http
GET /api/admin/audit-logs?action=UPDATE_BOOKING_STATUS&startDate=2024-01-01&page=0&size=50
Authorization: Bearer {admin_token}

Response:
{
  "content": [
    {
      "id": 1,
      "actorEmail": "admin@example.com",
      "action": "UPDATE_BOOKING_STATUS",
      "targetType": "BOOKING",
      "targetId": "booking-uuid",
      "oldValue": {"status": "PENDING"},
      "newValue": {"status": "ASSIGNED"},
      "timestamp": "2024-01-20T10:00:00",
      "ipAddress": "192.168.1.100"
    }
  ]
}
```

#### System Health Check
```http
GET /api/admin/system/health
Authorization: Bearer {admin_token}

Response:
{
  "status": "UP",
  "database": "UP",
  "emailService": "UP",
  "paymentGateway": "UP",
  "lastBackup": "2024-01-20T02:00:00",
  "activeUsers": 45,
  "systemLoad": "Normal"
}
```

---

## Admin Web Application Features

### 1. Dashboard
- Real-time business metrics
- Revenue charts and graphs
- User activity monitoring
- System health status
- Quick action buttons

### 2. User Management
- User list with search/filter
- User profile management
- Role assignment
- Account activation/deactivation
- Email verification status

### 3. Booking Management
- Booking queue management
- Status tracking
- Agent assignment
- Customer communication
- Booking history

### 4. Service Management
- Service catalog management
- Pricing configuration
- Service availability
- Performance metrics

### 5. Agent Management
- Agent performance tracking
- Assignment management
- Availability status
- Rating system

### 6. Reports & Analytics
- Revenue reports
- User analytics
- Booking statistics
- Performance metrics
- Export functionality

### 7. System Administration
- Audit logs
- System configuration
- Backup management
- Security settings

---

## Authentication & Security

### Required Headers
```
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

### Admin Role Requirements
All admin endpoints require `ADMIN` role in JWT token.

### Rate Limiting
- 100 requests per minute per IP
- 1000 requests per hour per authenticated user

### Security Features
- JWT token expiration (24 hours)
- Role-based access control
- Audit logging
- Input validation
- SQL injection protection

---

## Error Handling

### Standard Error Response
```json
{
  "timestamp": "2024-01-20T10:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied - Admin role required",
  "path": "/api/admin/users"
}
```

### Common HTTP Status Codes
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (invalid token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `500` - Internal Server Error

---

This documentation provides the foundation for building a comprehensive admin web application with full control over the MautonLaundry system operations.