# **Beekeeper API Backend**

This is the backend for the Beekeeper Journaling App. It's a Go-based API using the Gin framework that provides endpoints to manage beehives, logs, and tasks. It uses SQLite for persistent storage.

## **Table of Contents**

* [Prerequisites](#bookmark=id.gob91ndkbpif)  
* [Getting Started](#bookmark=id.xea1sps1plzk)  
  * [Installation](#bookmark=id.24k3vlnc5lz4)  
  * [Configuration](#bookmark=id.x3na04s3gpe0)  
  * [Running the Application](#bookmark=id.n5lcy2ypfbzd)  
* [API Documentation](#bookmark=id.r0szqfpolgi1)  
* [API Endpoints](#bookmark=id.96sud8c265qy)

## **Prerequisites**

Before you begin, ensure you have the following installed on your system:

* **Go**: Version 1.18 or higher.  
* **C Build Tools**: This project uses the github.com/mattn/go-sqlite3 package, which is a CGO wrapper around the native SQLite3 library. Therefore, you need a C compiler (like GCC) to build the project.  
  * **On Windows**: You can install TDM-GCC or MinGW.  
  * **On macOS**: Xcode Command Line Tools will provide the necessary tools.  
  * **On Linux (Debian/Ubuntu)**: sudo apt-get install build-essential

## **Getting Started**

Follow these instructions to get the backend server up and running.

### **Installation**

1. **Clone the repository:**  
   git clone https://github.com/Shraggen/Beekeeper  
   cd Beekeeper/apps/beekeeperBackend

2. Install dependencies:  
   This project uses Go Modules. To install the necessary dependencies, run:  
   go mod tidy

### **Configuration**

The application is configured using environment variables. You can place these in a .env file in the root of the project.

1. Create a file named .env:  
   touch .env

2. Add the following configuration variables. You can change these values if needed.  
   \# .env

   \# The port the server will listen on  
   PORT=8000

   \# The name of the SQLite database file  
   DB\_FILE=beekeeper.db

### **Running the Application**

To run the server, execute the following command from the project root. CGO\_ENABLED=1 is required to compile the SQLite driver.

CGO\_ENABLED=1 go run main.go

You should see an output indicating that the server has started:

\[GIN-debug\] \[WARNING\] Creating an Engine instance with the Logger and Recovery middleware already attached.

...

\[GIN-debug\] Listening and serving HTTP on :8000

The server is now running and listening for requests on http://localhost:8000.

## **API Documentation**

The API is documented using Swagger. Once the server is running, you can access the interactive Swagger UI in your browser at:

[**http://localhost:8000/swagger/index.html**](http://localhost:8000/swagger/index.html)

This interface provides detailed information about all available endpoints, their parameters, and allows you to send test requests directly from the browser.

## **API Endpoints**

The API is organized around three main resources. All endpoints are prefixed with /api.

* **/hives**: Manage your beehives.  
  * GET /hives: List all hives.  
  * POST /hives: Create a new hive.  
  * GET /hives/{id}: Get a specific hive by its ID.  
  * PATCH /hives/{id}: Update a hive.  
  * DELETE /hives/{id}: Delete a hive.  
* **/logs**: Manage log entries for your hives.  
  * GET /logs: Get all log entries.  
  * POST /logs: Create a new log entry.  
  * GET /logs/{id}: Get a specific log entry by its ID.  
  * PUT /logs/{id}: Update a log entry.  
  * DELETE /logs/{id}: Delete a log entry.  
  * GET /logs/last: Get the most recent log entry.  
* **/tasks**: Manage tasks associated with your hives.  
  * GET /tasks: Get all tasks.  
  * POST /tasks: Create a new task.  
  * GET /tasks/{id}: Get a specific task by its ID.  
  * PUT /tasks/{id}: Update a task.  
  * DELETE /tasks/{id}: Delete a task.  
  * GET /tasks/last: Get the most recent task.