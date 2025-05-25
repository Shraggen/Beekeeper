package routes

import (
	"beekeeper-backend/internal/api/handlers"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func HiveRoutes(app *gin.RouterGroup, db *gorm.DB) {
	handler := handlers.HiveHandler{
		BaseHandler: handlers.BaseHandler{DB: db},
	}

	taskRoutes := app.Group("/hives")

	taskRoutes.POST("/", handler.CreateHive)
	taskRoutes.GET("/", handler.GetAllHives)
	taskRoutes.GET("/:id", handler.GetHiveByID)
	taskRoutes.PATCH("/:id", handler.UpdateHive)
	taskRoutes.DELETE("/:id", handler.DeleteHive)
}
