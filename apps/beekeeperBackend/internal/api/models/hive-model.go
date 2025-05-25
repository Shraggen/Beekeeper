package models

import "time"

type Hive struct {
	ID        uint `gorm:"primaryKey"`
	HiveName  int  `gorm:"not null"`
	CreatedAt time.Time
	UpdatedAt time.Time
	Logs      []Log  `gorm:"foreignKey:HiveID;constraint:OnDelete:CASCADE"`
	Tasks     []Task `gorm:"foreignKey:HiveID;constraint:OnDelete:CASCADE"`
}
