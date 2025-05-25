package main

import (
	"beekeeper-backend/internal/api/routes"
	"beekeeper-backend/internal/config"
	"os"

	"github.com/gin-gonic/gin"
)

func main() {
	db := config.Initialization()

	port := os.Getenv("PORT")
	if port == "" {
		port = "8000"
	}

	app := gin.Default()
	api := app.Group("/api")

	routes.TaskRoutes(api, db)
	routes.LogRoutes(api, db)
	routes.HiveRoutes(api, db)

	app.Run(":" + port)
}
