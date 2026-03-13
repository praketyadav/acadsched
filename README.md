# AcadSched - Smart Academic Scheduling System

## Project Overview
AcadSched is a web-based academic management system designed to automate academic timetable generation and classroom allocation in educational institutions. The system uses a priority-based constraint scheduling algorithm to create conflict-free timetables.

## Features
- **Automated Timetable Generation**: Priority-based constraint scheduling algorithm
- **Faculty Management**: Track faculty availability and assignments
- **Classroom Allocation**: Optimize room utilization based on capacity
- **Grievance Analytics**: Basic categorization and tracking of student grievances
- **Campus Event Management**: Centralized platform for event registration and publishing
- **Dynamic Rescheduling**: Handle schedule changes due to faculty leave or room unavailability
- **Role-Based Access Control**: Admin, Faculty, and Student roles

## Technology Stack
- **Backend**: Java 17, Spring Boot 3.2.0
- **Frontend**: Thymeleaf, HTML5, CSS3, JavaScript
- **Database**: H2 (development), MySQL/PostgreSQL (production)
- **Security**: Spring Security
- **Build Tool**: Maven

## Prerequisites
- Java JDK 17 or higher
- Maven 3.6+
- MySQL 8.0+ (for production) or use embedded H2 database
- IDE: IntelliJ IDEA, Eclipse, or VS Code with Java extensions

## Installation and Setup

### 1. Clone or Extract the Project
```bash
cd acadsched
```

### 2. Configure Database
The application is configured to use H2 in-memory database by default for development.

**For H2 Database (Development - Default)**:
No configuration needed. The application will use the settings in `application.properties`.

**For MySQL (Production)**:
1. Create a MySQL database:
```sql
CREATE DATABASE acadsched;
```

2. Update `src/main/resources/application.properties`:
```properties
# Comment out H2 configuration
# spring.datasource.url=jdbc:h2:mem:acadsched
# spring.datasource.driverClassName=org.h2.Driver
# spring.datasource.username=sa
# spring.datasource.password=
# spring.h2.console.enabled=true

# Uncomment MySQL configuration
spring.datasource.url=jdbc:mysql://localhost:3306/acadsched?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

### 3. Build the Project
```bash
mvn clean install
```

### 4. Run the Application
```bash
mvn spring-boot:run
```

Alternatively, you can run the JAR file:
```bash
java -jar target/acadsched-1.0.0.jar
```

### 5. Access the Application
Open your browser and navigate to:
```
http://localhost:8080
```

**Default Ports**:
- Application: `http://localhost:8080`
- H2 Console (if enabled): `http://localhost:8080/h2-console`

## Running in VS Code

### 1. Install Extensions
Install the following VS Code extensions:
- Extension Pack for Java (Microsoft)
- Spring Boot Extension Pack (VMware)
- Maven for Java

### 2. Open the Project
- Open VS Code
- File → Open Folder → Select the `acadsched` folder

### 3. Run the Application
**Method 1: Using Spring Boot Dashboard**
1. Open Spring Boot Dashboard (usually in the left sidebar)
2. Find `AcadSchedApplication`
3. Click the play button to run

**Method 2: Using Terminal**
1. Open integrated terminal (Ctrl + `)
2. Run: `mvn spring-boot:run`

**Method 3: Using Debug**
1. Open `AcadSchedApplication.java`
2. Press F5 or click Run → Start Debugging

## Default User Accounts

After first run, you'll need to register users through the registration page. The first registered ADMIN user will have full access.

**To create sample users, you can use the registration page with these roles**:
- ADMIN - Full system access
- FACULTY - Timetable management
- STUDENT - Grievance submission, event viewing

## Project Structure
```
acadsched/
├── src/
│   ├── main/
│   │   ├── java/com/acadsched/
│   │   │   ├── AcadSchedApplication.java
│   │   │   ├── model/              # Entity classes
│   │   │   ├── repository/         # JPA repositories
│   │   │   ├── service/            # Business logic
│   │   │   ├── controller/         # Web controllers
│   │   │   ├── config/             # Configuration classes
│   │   │   └── security/           # Security components
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── templates/          # Thymeleaf templates
│   │       └── static/             # CSS, JS, images
│   └── test/                       # Test classes
├── pom.xml                         # Maven configuration
└── README.md
```

## Key Modules

### 1. Timetable Scheduling
- Priority-based constraint scheduling algorithm
- Automatic conflict detection
- Faculty availability checking
- Classroom capacity validation
- Dynamic rescheduling support

### 2. Grievance Management
- Student grievance submission
- Categorization (Academic, Infrastructure, etc.)
- Status tracking (Pending, In Progress, Resolved)
- Basic analytics

### 3. Event Management
- Event creation and publishing
- Event type categorization
- Upcoming events display

## API Endpoints

### Authentication
- `GET /login` - Login page
- `GET /register` - Registration page
- `POST /logout` - Logout

### Timetable
- `GET /timetable` - View timetable
- `POST /timetable/generate` - Generate timetable
- `GET /timetable/view?semester=X&section=Y` - View specific timetable
- `POST /timetable/reschedule/{id}` - Reschedule session

### Grievances
- `GET /grievances` - List grievances
- `GET /grievances/new` - Create grievance form
- `POST /grievances/new` - Submit grievance
- `GET /grievances/analytics` - View analytics
- `POST /grievances/{id}/update-status` - Update status

### Events
- `GET /events` - List upcoming events
- `GET /events/new` - Create event form
- `POST /events/new` - Submit event
- `POST /events/{id}/publish` - Publish event

## Troubleshooting

### Port Already in Use
If port 8080 is already in use, change it in `application.properties`:
```properties
server.port=8081
```

### Database Connection Issues
- Verify MySQL is running
- Check credentials in `application.properties`
- Ensure database exists

### Build Failures
```bash
mvn clean
mvn install
```

### H2 Console Not Accessible
Ensure in `application.properties`:
```properties
spring.h2.console.enabled=true
```

## Development Tips

### Hot Reload
Spring Boot DevTools is included for automatic restart on code changes.

### Database Changes
The application uses `spring.jpa.hibernate.ddl-auto=update` which automatically updates the schema. For production, use `validate` or Flyway/Liquibase.

### Adding New Features
1. Create entity in `model` package
2. Create repository in `repository` package
3. Create service in `service` package
4. Create controller in `controller` package
5. Create Thymeleaf template in `templates`

## Testing
Run tests using:
```bash
mvn test
```

## Production Deployment

### 1. Build Production JAR
```bash
mvn clean package -DskipTests
```

### 2. Run with Production Profile
```bash
java -jar target/acadsched-1.0.0.jar --spring.profiles.active=prod
```

### 3. Environment Variables
Set sensitive data via environment variables:
```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/acadsched
export SPRING_DATASOURCE_USERNAME=dbuser
export SPRING_DATASOURCE_PASSWORD=dbpass
```

## Future Enhancements
- AI-based timetable optimization
- Advanced grievance sentiment analysis
- Mobile application support
- Integration with institutional ERP systems
- Attendance tracking integration

## Support
For issues and questions, please check the documentation or create an issue in the project repository.

## License
This project is developed for educational purposes.

## Contributors
AcadSched Development Team
