package config

import (
	"beekeeper-backend/internal/api/models"
	"beekeeper-backend/internal/database"
	"beekeeper-backend/internal/utils"

	"gorm.io/gorm"
)

func Initialization() *gorm.DB {
	utils.LoadEnv()
	db := database.ConnectDatabase()
	db.AutoMigrate(&models.Hive{}, &models.Log{}, &models.Task{})

	return db
}
