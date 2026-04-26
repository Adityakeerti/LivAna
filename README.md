# LivAna

LivAna is currently in the initial stages of development. The project focuses on building a Spring Boot backend with a PostgreSQL database and Valkey for caching/messaging.

## Current Progress

So far, the foundational infrastructure and project structure have been established:

* **Containerized Infrastructure**: 
  * Configured a `docker-compose.yml` to set up local environments for PostgreSQL 16 and Valkey 7.
  * Configured named volumes for persistent database storage.
  * Added database initialization scripts (`db/01_schema.sql`) to automatically seed the database schema upon the first start.
* **Backend Initialization**:
  * Generated a Spring Boot project (Java 21) within the `backend/` directory using Maven.
  * Configured essential dependencies in `pom.xml`, including:
    * Spring WebMVC
    * Spring Data JPA
    * Spring Security
    * Spring Boot Actuator
    * PostgreSQL Driver
    * Lombok
  * Added basic configurations in `application.properties` and `application-dev.properties`.
* **Documentation**:
  * Architecture diagrams, UI flows, database schemas, and implementation plans have been added to the `docs/` directory.

## Prerequisites

* [Docker Desktop](https://www.docker.com/products/docker-desktop/) or Docker Engine & Docker Compose
* Java 21 (if running the backend locally outside of containers)

## Getting Started

### 1. Start Infrastructure Services

To start the database and Valkey instances, run the following command from the root of the project:

```bash
docker-compose up -d
```

*This will start PostgreSQL on port `5432` and Valkey on port `6379`. It will also automatically execute the schema initialization script located at `db/01_schema.sql`.*

### 2. Run the Backend

Navigate to the `backend` directory and run the Spring Boot application using the Maven Wrapper:

```bash
cd backend
./mvnw spring-boot:run
```

*(On Windows, you can use `mvnw.cmd spring-boot:run`)*
