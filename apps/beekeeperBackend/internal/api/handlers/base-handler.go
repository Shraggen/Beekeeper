package handlers

import (
	"beekeeper-backend/internal/api/models"
	"strconv"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// BaseHandler holds a database connection and provides common CRUD methods.
type BaseHandler struct {
	DB *gorm.DB
}

// CreateEntry inserts a new record into the database using the given model.
func (h *BaseHandler) CreateEntry(c *gin.Context, model any) {
	if err := h.DB.Create(model).Error; err != nil {
		c.JSON(500, gin.H{"error": "Failed to create entry"})
		return
	}

	c.JSON(201, gin.H{"data": model})
}

// GetAllEntries retrieves all records of the given model type from the database.
func (h *BaseHandler) GetAllEntries(c *gin.Context, model any) {
	if err := h.DB.Find(model).Error; err != nil {
		c.JSON(500, gin.H{"error": "Failed to retrieve entry"})
		return
	}

	c.JSON(200, gin.H{"data": model})
}

// GetEntryByID finds a single record by its ID
func (h *BaseHandler) GetEntryByID(c *gin.Context, model any) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.First(&model, id).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	c.JSON(200, gin.H{"data": model})
}

// UpdateEntry updates a record by its ID using the provided input and applyChanges function.
func (h *BaseHandler) UpdateEntry(
	c *gin.Context,
	model any,
	input any,
	applyChanges func(any, any)) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.First(&model, id).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	if err := c.ShouldBindJSON(input); err != nil {
		c.JSON(400, gin.H{"error": "Invalid input"})
		return
	}

	applyChanges(model, input)

	if err := h.DB.Save(model).Error; err != nil {
		c.JSON(500, gin.H{"error": "Failed to save"})
		return
	}

	c.JSON(200, gin.H{"data": model})
}

// DeleteEntry removes a record by its ID from the database.
func (h *BaseHandler) DeleteEntry(c *gin.Context, model any) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.First(&model, id).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	if err := h.DB.Delete(&model, id).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	c.Status(204)
}

// CreateHiveRemote creates a new Hive record using the provided integer as HiveName.
func (h *BaseHandler) CreateHiveRemote(c *gin.Context, input int) (*models.Hive, error) {
	hive := models.Hive{
		HiveName: input,
	}

	if err := h.DB.Create(&hive).Error; err != nil {
		c.JSON(500, gin.H{"error": "Could not create hive"})
		return nil, err
	}

	return &hive, nil
}
