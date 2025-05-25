package handlers

import (
	"beekeeper-backend/internal/api/models"
	"beekeeper-backend/internal/types"
	"strconv"

	"github.com/gin-gonic/gin"
)

// HiveHandler provides CRUD operations for hive-related data.
type HiveHandler struct {
	BaseHandler
}

// CreateHive handles POST /hives.
// It creates a new hive based on JSON input.
func (h *HiveHandler) CreateHive(c *gin.Context) {
	var input types.CreateHiveInput

	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(400, gin.H{"error": "Invalid input"})
		return
	}

	hive := models.Hive{
		HiveName: input.HiveName,
	}

	if err := h.DB.Create(&hive).Error; err != nil {
		c.JSON(500, gin.H{"error": "Could not create hive"})
		return
	}

	c.JSON(201, gin.H{"data": hive})
}

// GetAllHives handles GET /hives.
// It retrieves and returns all hives from the database.
func (h *HiveHandler) GetAllHives(c *gin.Context) {
	var hives []models.Hive
	h.GetAllEntries(c, &hives)
}

// GetHiveByID handles GET /hives/:id.
// It retrieves a single hive by its hive_name.
func (h *HiveHandler) GetHiveByID(c *gin.Context) {
	var hive models.Hive
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.Where("hive_name = ?", id).First(&hive).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	c.JSON(200, gin.H{"data": hive})
}

// UpdateHive handles PATCH /hives/:id.
// It updates a hive using provided JSON input.
func (h *HiveHandler) UpdateHive(c *gin.Context) {
	var hive models.Hive
	var input types.UpdateHiveInput
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.Where("hive_name = ?", id).First(&hive).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(400, gin.H{"error": "Invalid input"})
		return
	}

	hive.HiveName = *input.HiveName

	if err := h.DB.Save(&hive).Error; err != nil {
		c.JSON(500, gin.H{"error": "Failed to save"})
		return
	}

	c.JSON(200, gin.H{"data": hive})

}

// DeleteHive handles DELETE /hives/:id.
// It deletes a hive by its hive_name.
func (h *HiveHandler) DeleteHive(c *gin.Context) {
	var hive models.Hive
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": "Invalid ID"})
		return
	}

	if err := h.DB.Where("hive_name = ?", id).First(&hive).Error; err != nil {
		c.JSON(404, gin.H{"error": "Entry not found"})
		return
	}

	if err := h.DB.Delete(&hive).Error; err != nil {
		c.JSON(404, gin.H{"error": "Failed to delete hive"})
		return
	}

	c.Status(204)
}
