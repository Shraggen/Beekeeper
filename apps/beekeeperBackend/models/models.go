package models

import "time"

// Hive represents a beehive in the management system
type Hive struct {
	ID        uint      `json:"id" gorm:"primaryKey" example:"1"`
	HiveName  int       `json:"hive_name" gorm:"unique;not null" example:"123"`
	CreatedAt time.Time `json:"created_at" example:"2024-01-15T10:30:00Z"`
	UpdatedAt time.Time `json:"updated_at" example:"2024-01-15T10:30:00Z"`
	Logs      []Log     `json:"logs,omitempty" gorm:"foreignKey:HiveID;references:HiveName;constraint:OnDelete:CASCADE;"`
	Tasks     []Task    `json:"tasks,omitempty" gorm:"foreignKey:HiveID;references:HiveName;constraint:OnDelete:CASCADE;"`
}

// Log represents a log entry for a beehive
type Log struct {
	ID        uint      `json:"id" gorm:"primaryKey" example:"1"`
	HiveID    int       `json:"hive_id" gorm:"not null" example:"123"`
	Content   string    `json:"content" gorm:"not null" example:"Hive inspection completed. Queen spotted, brood pattern looks healthy."`
	CreatedAt time.Time `json:"created_at" example:"2024-01-15T10:30:00Z"`
	UpdatedAt time.Time `json:"updated_at" example:"2024-01-15T10:30:00Z"`
}

// Task represents a task entry for a beehive
type Task struct {
	ID        uint      `json:"id" gorm:"primaryKey" example:"1"`
	HiveID    int       `json:"hive_id" gorm:"not null" example:"123"`
	Content   string    `json:"content" gorm:"not null" example:"Check honey levels and replace frames"`
	CreatedAt time.Time `json:"created_at" example:"2024-01-15T10:30:00Z"`
	UpdatedAt time.Time `json:"updated_at" example:"2024-01-15T10:30:00Z"`
}

