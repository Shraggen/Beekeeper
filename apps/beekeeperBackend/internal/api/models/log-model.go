package models

import "time"

type Log struct {
	ID        uint   `gorm:"primaryKey"`
	HiveID    int    `gorm:"not null"`
	Content   string `gorm:"not null"`
	CreatedAt time.Time
	UpdatedAt time.Time
}
