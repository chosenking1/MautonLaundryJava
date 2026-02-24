# Admin API Documentation

## Authentication
All admin endpoints require JWT token with ADMIN role in Authorization header:
```
Authorization: Bearer <jwt_token>
```

## File Upload

### Upload Image
**POST** `/api/v1/admin/upload/image`

**Request:** `multipart/form-data`
```
file: [image file]
```

**Response (200):**
```json
{
  "filename": "a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "path": "uploads/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg"
}
```

## Categories Management

### 1. Create Category
**POST** `/api/v1/admin/categories`

**Request:**
```json
{
  "name": "Dry Cleaning",
  "description": "Professional dry cleaning services for delicate fabrics",
  "imagePath": "uploads/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg"
}
```

**Response (201):**
```json
{
  "id": 1,
  "name": "Dry Cleaning",
  "description": "Professional dry cleaning services for delicate fabrics",
  "imagePath": "uploads/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### 2. Get All Categories
**GET** `/api/v1/admin/categories`

**Response (200):**
```json
[
  {
    "id": 1,
    "name": "Dry Cleaning",
    "description": "Professional dry cleaning services",
    "imagePath": "uploads/dry-cleaning-image.jpg",
    "active": true,
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  },
  {
    "id": 2,
    "name": "Wash & Fold",
    "description": "Regular washing and folding service",
    "imagePath": "uploads/wash-fold-image.jpg",
    "active": true,
    "createdAt": "2024-01-15T11:00:00Z",
    "updatedAt": "2024-01-15T11:00:00Z"
  }
]
```

### 3. Get Category by ID
**GET** `/api/v1/admin/categories/{id}`

**Response (200):**
```json
{
  "id": 1,
  "name": "Dry Cleaning",
  "description": "Professional dry cleaning services for delicate fabrics",
  "imagePath": "uploads/dry-cleaning-image.jpg",
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### 4. Update Category
**PUT** `/api/v1/admin/categories/{id}`

**Request:**
```json
{
  "name": "Premium Dry Cleaning",
  "description": "Premium dry cleaning services for luxury fabrics",
  "imagePath": "uploads/premium-dry-cleaning.jpg"
}
```

**Response (200):**
```json
{
  "id": 1,
  "name": "Premium Dry Cleaning",
  "description": "Premium dry cleaning services for luxury fabrics",
  "imagePath": "uploads/premium-dry-cleaning.jpg",
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T14:20:00Z"
}
```

### 5. Delete Category
**DELETE** `/api/v1/admin/categories/{id}`

**Response (204):** No content

## Services Management

### 1. Create Service
**POST** `/api/v1/admin/services`

**Request:**
```json
{
  "name": "Express Dry Clean",
  "description": "Same-day dry cleaning service",
  "categoryId": 1,
  "imagePath": "uploads/express-service.jpg",
  "estimatedDuration": "PT4H"
}
```

**Response (201):**
```json
{
  "id": 1,
  "name": "Express Dry Clean",
  "description": "Same-day dry cleaning service",
  "category": {
    "id": 1,
    "name": "Dry Cleaning"
  },
  "imagePath": "uploads/express-service.jpg",
  "estimatedDuration": "PT4H",
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

### 2. Get All Services
**GET** `/api/v1/admin/services`

**Response (200):**
```json
[
  {
    "id": 1,
    "name": "Express Dry Clean",
    "description": "Same-day dry cleaning service",
    "category": {
      "id": 1,
      "name": "Dry Cleaning"
    },
    "imagePath": "uploads/express-service.jpg",
    "estimatedDuration": "PT4H",
    "active": true,
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
]
```

### 3. Update Service
**PUT** `/api/v1/admin/services/{id}`

**Request:**
```json
{
  "name": "Premium Express Dry Clean",
  "description": "Premium same-day dry cleaning with pickup",
  "categoryId": 1,
  "imagePath": "uploads/premium-express.jpg",
  "estimatedDuration": "PT3H"
}
```

**Response (200):**
```json
{
  "id": 1,
  "name": "Premium Express Dry Clean",
  "description": "Premium same-day dry cleaning with pickup",
  "category": {
    "id": 1,
    "name": "Dry Cleaning"
  },
  "imagePath": "uploads/premium-express.jpg",
  "estimatedDuration": "PT3H",
  "active": true,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T14:45:00Z"
}
```

### 4. Delete Service
**DELETE** `/api/v1/admin/services/{id}`

**Response (204):** No content

## Service Pricing Management

### 1. Create Service Pricing
**POST** `/api/v1/admin/services/{serviceId}/pricing`

**Request:**
```json
{
  "priceType": "PER_ITEM",
  "itemType": "SHIRT",
  "price": 15.99,
  "unit": "PCS"
}
```

**Response (201):**
```json
{
  "id": 1,
  "priceType": "PER_ITEM",
  "itemType": "SHIRT",
  "price": 15.99,
  "unit": "PCS",
  "active": true
}
```

### 2. Get Service Pricing
**GET** `/api/v1/admin/services/{serviceId}/pricing`

**Response (200):**
```json
[
  {
    "id": 1,
    "priceType": "PER_ITEM",
    "itemType": "SHIRT",
    "price": 15.99,
    "unit": "PCS",
    "active": true
  },
  {
    "id": 2,
    "priceType": "PER_ITEM",
    "itemType": "PANTS",
    "price": 18.99,
    "unit": "PCS",
    "active": true
  },
  {
    "id": 3,
    "priceType": "PER_ITEM",
    "itemType": "WHITE",
    "price": 12.99,
    "unit": "PCS",
    "active": true
  }
]
```

### 3. Update Service Pricing
**PUT** `/api/v1/admin/services/{serviceId}/pricing/{pricingId}`

**Request:**
```json
{
  "priceType": "PER_ITEM",
  "itemType": "SHIRT",
  "price": 17.99,
  "unit": "PCS"
}
```

**Response (200):**
```json
{
  "id": 1,
  "priceType": "PER_ITEM",
  "itemType": "SHIRT",
  "price": 17.99,
  "unit": "PCS",
  "active": true
}
```

### 4. Delete Service Pricing
**DELETE** `/api/v1/admin/services/{serviceId}/pricing/{pricingId}`

**Response (204):** No content

## Frontend Workflow

### Image Upload Process
1. **Upload Image**: POST to `/api/v1/admin/upload/image` with file
2. **Get Path**: Use returned `path` field in category/service creation
3. **Create/Update**: Include `imagePath` in request body

### Example Frontend Flow
```javascript
// 1. Upload image
const formData = new FormData();
formData.append('file', imageFile);
const uploadResponse = await fetch('/api/v1/admin/upload/image', {
  method: 'POST',
  body: formData
});
const { path } = await uploadResponse.json();

// 2. Create category with uploaded image
const categoryData = {
  name: 'Dry Cleaning',
  description: 'Professional services',
  imagePath: path
};
```

## Error Responses

### 400 Bad Request
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/admin/categories"
}
```

### 401 Unauthorized
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT token is missing or invalid",
  "path": "/api/v1/admin/categories"
}
```

### 403 Forbidden
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied. Admin role required",
  "path": "/api/v1/admin/categories"
}
```

### 404 Not Found
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Category not found with id: 999",
  "path": "/api/v1/admin/categories/999"
}
```

## Supported Values

### Price Types
- `PER_ITEM` - Price per individual item
- `PER_KG` - Price per kilogram
- `FLAT_RATE` - Fixed price regardless of quantity

### Item Types
- `GENERAL` - General clothing items
- `SHIRT` - Shirts and blouses
- `PANTS` - Pants and trousers
- `DRESS` - Dresses and formal wear
- `WHITE` - White clothing items
- `DELICATE` - Delicate fabrics
- `HEAVY` - Heavy items like coats

### Units
- `PCS` - Pieces (for PER_ITEM)
- `KG` - Kilograms (for PER_KG)
- `LOAD` - Per load (for FLAT_RATE)