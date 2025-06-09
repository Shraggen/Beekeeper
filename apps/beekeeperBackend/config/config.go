package config

import (
	"os"
)

// Config holds the application's configuration
type Config struct {
	Port   string
	DBFile string
}

// New creates a new Config instance from environment variables
func New() *Config {
	return &Config{
		Port:   getEnv("PORT", "8000"),
		DBFile: getEnv("DB_FILE", "beekeeper.db"),
	}
}

// Helper function to get an environment variable or return a default value
func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

