package database

import (
	"log"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"

	"beekeeper-api/config"
	"beekeeper-api/models"
)

// Init initializes and returns a GORM database instance
func Init(cfg *config.Config) *gorm.DB {
	db, err := gorm.Open(sqlite.Open(cfg.DBFile), &gorm.Config{})
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}

	// Auto-migrate the schema
	err = db.AutoMigrate(&models.Hive{}, &models.Log{}, &models.Task{})
	if err != nil {
		log.Fatalf("Failed to migrate database: %v", err)
	}

	return db
}

