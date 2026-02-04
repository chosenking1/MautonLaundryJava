# API Documentation - MautonLaundry Platform

## Services API

### Create Service
**POST** `/api/v1/services`

**Authorization:** Requires `SERVICE_CREATE` permission (Admin only)

**Request Body:**
```json
{
  "name": "string (required)",
  "description": "string (optional)",
  "category": "string (required)",
  "price": "number (required)",
  "white": "number (required)"
}
```

**Field Descriptions:**
- `name`: Service name (e.g., "Shirt Washing")
- `description`: Optional service description
- `category`: Service category - must be one of: `LAUNDRY`, `CLEANING`, `DRY_CLEANING`, `IRONING`
- `price`: Price for colored items
- `white`: Price for white items

**Example Request:**
```json
{
  "name": "Shirt Washing",
  "description": "Professional shirt washing service",
  "category": "LAUNDRY",
  "price": 15.00,
  "white": 12.00
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Shirt Washing",
  "description": "Professional shirt washing service",
  "category": "LAUNDRY",
  "price": 15.00,
  "white": 12.00
}
```

**Error Responses:**
- `400 Bad Request`: Invalid input (missing required fields, invalid category)
- `401 Unauthorized`: Not authenticated
- `403 Forbidden`: Insufficient permissions

### Get All Services
**GET** `/api/v1/services`

**Authorization:** Requires `SERVICE_READ` permission

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Shirt Washing",
    "description": "Professional shirt washing service",
    "category": "LAUNDRY",
    "price": 15.00,
    "white": 12.00
  }
]
```

### Get Service by ID
**GET** `/api/v1/services/{id}`

**Authorization:** Requires `SERVICE_READ` permission

**Response (200 OK):**
```json
{
  "id": 1,
  "name": "Shirt Washing",
  "description": "Professional shirt washing service",
  "category": "LAUNDRY",
  "price": 15.00,
  "white": 12.00
}
```

**Error Responses:**
- `404 Not Found`: Service not found

### Update Service
**PUT** `/api/v1/services/{id}`

**Authorization:** Requires `SERVICE_UPDATE` permission (Admin only)

**Request Body:** Same as Create Service

### Delete Service
**DELETE** `/api/v1/services/{id}`

**Authorization:** Requires `SERVICE_DELETE` permission (Admin only)

**Response:** `204 No Content`

### Get Services by Category
**GET** `/api/v1/services/category/{category}`

**Authorization:** Requires `SERVICE_READ` permission

**Path Parameters:**
- `category`: One of `LAUNDRY`, `CLEANING`, `DRY_CLEANING`, `IRONING`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Shirt Washing",
    "description": "Professional shirt washing service",
    "category": "LAUNDRY",
    "price": 15.00,
    "white": 12.00
  }
]
```

## Valid Service Categories

The following categories are supported:
- `LAUNDRY`: Regular washing services
- `CLEANING`: House/office cleaning services  
- `DRY_CLEANING`: Dry cleaning services
- `IRONING`: Ironing and pressing services

**Note:** Category field is case-sensitive and must match exactly.