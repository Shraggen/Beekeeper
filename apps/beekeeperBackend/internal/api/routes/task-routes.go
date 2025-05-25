package routes

import (
	"beekeeper-backend/internal/api/handlers"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

func TaskRoutes(app *gin.RouterGroup, db *gorm.DB) {
	handler := handlers.TaskHandler{
		BaseHandler: handlers.BaseHandler{DB: db},
	}

	taskRoutes := app.Group("/tasks")

	taskRoutes.POST("/", handler.CreateTask)
	taskRoutes.GET("/", handler.GetAllTasks)
	taskRoutes.GET("/:id", handler.GetTaskByID)
	taskRoutes.PATCH("/:id", handler.UpdateTask)
	taskRoutes.DELETE("/:id", handler.DeleteTask)
}
