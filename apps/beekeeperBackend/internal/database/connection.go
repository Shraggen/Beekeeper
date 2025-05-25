package database

import (
	"fmt"
	"log"

	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

func ConnectDatabase() *gorm.DB {
	db, err := gorm.Open(sqlite.Open("../internal/database/beekeeper-database.db"), &gorm.Config{})
	if err != nil {
		fmt.Print(err)
		log.Fatal("Failed to connect to the Database")
	}

	log.Println("Database connection established")
	return db
}
