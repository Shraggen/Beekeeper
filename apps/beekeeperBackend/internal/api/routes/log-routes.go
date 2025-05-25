package routes

import (
	"beekeeper-backend/internal/api/handlers"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func LogRoutes(app *gin.RouterGroup, db *gorm.DB) {
	handler := handlers.LogHandler{
		BaseHandler: handlers.BaseHandler{DB: db},
	}

	logRoutes := app.Group("/logs")

	logRoutes.POST("/", handler.CreateLog)
	logRoutes.GET("/", handler.GetAllLogs)
	logRoutes.GET("/:id", handler.GetLogByID)
	logRoutes.PATCH("/:id", handler.UpdateLog)
	logRoutes.DELETE("/:id", handler.DeleteLog)
}
