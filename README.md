# MautonLaundry - Spring Boot Application

## Project Description
MautonLaundry is a comprehensive Spring Boot application designed to manage all aspects of a laundry service business. It provides robust functionalities for user management, laundry order processing, delivery management, and payment handling. The application follows modern software architecture principles and implements best practices in security and data management.

## Key Features
- **User Management**
  - User registration and authentication with JWT
  - Role-based access control (Admin, User, Laundry Agent, Delivery Agent)
  - User profile management and address handling
  - Soft delete functionality for users

- **Booking Management**
  - Laundry service booking with multiple service options
  - Urgency level selection (Standard, Express)
  - Booking status tracking (Pending, Washing, Ready for Pickup, Delivered)
  - Booking history management
  - Cancellation and refund processing

- **Delivery Management**
  - Delivery address management with geolocation
  - Delivery status tracking (Pending Pickup, In Transit, Delivered)
  - Integration with delivery agents

- **Payment Processing**
  - Multiple payment methods (Card, Cash, Transfer)
  - Payment status tracking (Pending, Success, Failed, Refunded)
  - Transaction history

- **Security Features**
  - JWT-based authentication
  - Password encryption
  - Role-based authorization
  - Secure API endpoints

## Technologies Used
- **Core Framework**: Spring Boot 3.1.0
- **Database**: MySQL with Spring Data JPA
- **Security**: Spring Security with JWT
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Other Libraries**:
  - Lombok for boilerplate code reduction
  - ModelMapper for object mapping
  - Jakarta Validation for input validation
  - JSON Web Tokens (JWT) for authentication

## Database Schema Overview
The application uses a relational database with the following key entities:
- **User**: Stores user information and roles
- **Address**: Manages user addresses with geolocation
- **Booking**: Tracks laundry service orders
- **DeliveryManagement**: Manages delivery information
- **Payment**: Handles payment transactions

## API Endpoints
The application provides RESTful APIs for all major operations. The API documentation is automatically generated using Swagger UI and can be accessed at `/swagger-ui.html` when the application is running.

## Getting Started

### Prerequisites
Before you begin, ensure you have the following installed:
*   **Java Development Kit (JDK) 17** or higher.
*   **Maven 3.6.0** or higher.
*   **MySQL Database**: A running instance of MySQL.

### Setup and Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/MautonLaundryJava.git
    cd MautonLaundryJava

# MautonLaundryJava

```properties
    spring.datasource.url=jdbc:mysql://localhost:3306/mauton_laundry_db?useSSL=false&serverTimezone=UTC
    spring.datasource.username=your_mysql_username
    spring.datasource.password=your_mysql_password
    spring.jpa.hibernate.ddl-auto=update # or create, create-drop, none
    spring.jpa.show-sql=true

    mvn clean install

    cd MautonLaundryJava

2.  **Configure Database (optional, see application.properties):**
    *   Open `src/main/resources/application.properties` (or `application-dev.properties` if you're using a profile) and configure your MySQL database connection. Replace placeholders with your actual credentials:
        ```properties
        spring.datasource.url=jdbc:mysql://localhost:3306/mauton_laundry_db?useSSL=false&serverTimezone=UTC
        spring.datasource.username=your_mysql_username
        spring.datasource.password=your_mysql_password
        spring.jpa.hibernate.ddl-auto=update # or create, create-drop, none
        spring.jpa.show-sql=true

   mvn clean install
   mvn spring-boot:run