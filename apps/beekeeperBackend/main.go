package main

import (
	"log"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"

	"beekeeper-api/config"
	"beekeeper-api/database"
	_ "beekeeper-api/docs" // Import generated docs
	"beekeeper-api/features/hives"
	"beekeeper-api/features/logs"
	"beekeeper-api/features/tasks"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
)

// @title Beekeeper API
// @version 1.0
// @description A beekeeping management API built with Go and Gin framework
// @termsOfService http://swagger.io/terms/
// @contact.name API Support
// @contact.url http://www.swagger.io/support
// @contact.email support@swagger.io
// @license.name Apache 2.0
// @license.url http://www.apache.org/licenses/LICENSE-2.0.html
// @host localhost:8000
// @BasePath /api
// @securityDefinitions.basic BasicAuth
func main() {
	// Load environment variables from .env file
	// In a production environment, these should be set directly
	err := godotenv.Load()
	if err != nil {
		log.Println("No .env file found, using environment variables")
	}

	// Initialize configuration
	cfg := config.New()

	// Initialize database connection
	db := database.Init(cfg)

	// Create a new Gin router
	router := gin.Default()

	// API base path
	api := router.Group("/api")

	// Register feature-specific routes
	hives.RegisterRoutes(api, db)
	logs.RegisterRoutes(api, db)
	tasks.RegisterRoutes(api, db)

	// Add Swagger endpoint
	router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	// Start the server
	log.Printf("Server starting on port %s", cfg.Port)
	if err := router.Run(":" + cfg.Port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}