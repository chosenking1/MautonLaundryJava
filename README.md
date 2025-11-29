# MautonLaundry - Spring Boot Application

A comprehensive laundry service management system built with Spring Boot, providing complete business operations from user registration to payment processing.

## Features

### Core Functionality
- **User Management**: Registration, authentication, role-based access (Admin, User, Laundry Agent, Delivery Agent)
- **Booking System**: Service booking with urgency levels, status tracking, cancellation/refunds
- **Delivery Management**: Address handling, geolocation, delivery tracking
- **Payment Processing**: Multiple payment methods, transaction history
- **Security**: JWT authentication, password encryption, role-based authorization

### Technical Features
- RESTful API with Swagger documentation
- Soft delete functionality
- Transaction management
- Input validation
- Audit logging capabilities

## Tech Stack

- **Java**: 25
- **Spring Boot**: 3.4.1
- **Maven**: 3.9.11
- **Database**: MySQL with JPA/Hibernate
- **Security**: Spring Security + JWT
- **Documentation**: SpringDoc OpenAPI
- **Libraries**: Lombok, ModelMapper, Jakarta Validation

## Quick Start

### Prerequisites
- Java 25
- Maven 3.9.11+
- MySQL 8.0+

### Setup
1. **Clone repository**
   ```bash
   git clone <repository-url>
   cd MautonLaundryJava
   ```

2. **Configure database**
   ```properties
   # src/main/resources/application.properties
   spring.datasource.url=jdbc:mysql://localhost:3306/mauton_cleans
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

3. **Run application**
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Access API documentation**
   - Swagger UI: `http://localhost:8079/swagger-ui.html`
   - API runs on port: `8079`

## Project Structure

```
src/main/java/com/work/mautonlaundry/
├── config/          # Configuration classes
├── controllers/     # REST controllers
├── data/           # Entities and repositories
├── dtos/           # Data transfer objects
├── exceptions/     # Custom exceptions
├── security/       # Security configuration
├── services/       # Business logic
└── util/           # Utility classes
```

## Database Schema

Key entities: User, Address, Booking, DeliveryManagement, Payment

## API Endpoints

All endpoints documented via Swagger UI. Key operations:
- User registration/authentication
- Booking management
- Delivery tracking
- Payment processing

## Security

- JWT-based authentication
- BCrypt password hashing
- Role-based access control
- Secure API endpoints

## Development

```bash
# Build
./mvnw clean install

# Run tests
./mvnw test

# Package
./mvnw package
```