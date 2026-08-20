# IT Asset Allocation Portal

A DevOps project for developing and delivering a web-based IT Asset Allocation Portal using an automated software delivery lifecycle.

## Project Objective

The system provides a centralized platform for managing organizational IT assets, including asset creation, viewing, updating, searching, allocation and status management.

The project demonstrates a complete DevOps workflow covering source control, continuous integration, automated testing, containerization, deployment and configuration management.

## Planned Technology Stack

* Java
* Spring Boot
* Maven
* PostgreSQL
* Git
* GitHub
* Jenkins
* Selenium WebDriver
* Docker
* Puppet

## Current Status

Week 4 – Git and GitHub Repository Initialization

The initial Spring Boot application skeleton has been created and successfully verified with a Maven build.

## Planned DevOps Lifecycle

```text
Plan
  ↓
Code
  ↓
Git / GitHub
  ↓
Jenkins
  ↓
Maven Build
  ↓
Selenium Testing
  ↓
Docker
  ↓
Deployment
  ↓
Puppet Provisioning
  ↓
Health Check
```

## Project Structure

```text
asset-allocation-portal/
├── src/
├── docs/
├── docker/
├── puppet/
├── selenium-tests/
├── .gitignore
├── README.md
├── Dockerfile
├── Jenkinsfile
└── pom.xml
```

Some directories and files will be introduced in later weeks as their corresponding DevOps stages are implemented.

## Development

The application is built using Maven.

```bash
mvn clean package
```

Run the application using:

```bash
mvn spring-boot:run
```

## Project Scope

The MVP will support:

* Asset creation
* Asset viewing
* Asset updating
* Asset searching
* Asset allocation
* Asset status management
* Role-based access
* Summary dashboard

The DevOps implementation will progressively add:

* Git/GitHub collaboration
* Jenkins CI/CD
* Selenium automated testing
* Docker containerization
* Puppet configuration management
* Automated provisioning
* Health checks and recovery

