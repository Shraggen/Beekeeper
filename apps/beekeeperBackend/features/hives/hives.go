package hives

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"

	"beekeeper-api/models"
)

// --- Structs for Input Validation ---

type CreateHiveInput struct {
	HiveName int `json:"hiveName" binding:"required"`
}

type UpdateHiveInput struct {
	HiveName int `json:"hiveName"`
}

// --- Route Registration ---

func RegisterRoutes(router *gin.RouterGroup, db *gorm.DB) {
	h := &handler{db: db}

	hiveRoutes := router.Group("/hives")
	{
		hiveRoutes.POST("", h.CreateHive)
		hiveRoutes.GET("", h.ListHives)
		hiveRoutes.GET("/:id", h.GetHive)
		hiveRoutes.PATCH("/:id", h.UpdateHive)
		hiveRoutes.DELETE("/:id", h.DeleteHive)
	}
}

// --- Handler ---

type handler struct {
	db *gorm.DB
}

// CreateHive godoc
// @Summary Create a new hive
// @Description Create a new hive with the provided information
// @Tags hives
// @Accept  json
// @Produce  json
// @Param hive body CreateHiveInput true "Hive data"
// @Success 201 {object} models.Hive
// @Failure 400 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /hives [post]
func (h *handler) CreateHive(c *gin.Context) {
	var input CreateHiveInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	hive := models.Hive{HiveName: input.HiveName}
	if result := h.db.Create(&hive); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Could not create hive"})
		return
	}

	c.JSON(http.StatusCreated, hive)
}

// ListHives godoc
// @Summary List all hives
// @Description Get a list of all hives
// @Tags hives
// @Produce  json
// @Success 200 {array} models.Hive
// @Failure 500 {object} map[string]string
// @Router /hives [get]
func (h *handler) ListHives(c *gin.Context) {
	var hives []models.Hive
	if result := h.db.Find(&hives); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve hives"})
		return
	}

	c.JSON(http.StatusOK, hives)
}

// GetHive godoc
// @Summary Get hive by its name/ID
// @Description Get a single hive by its hive name/ID, NOT by entry's ID
// @Tags hives
// @Produce  json
// @Param id path int true "Hive ID"
// @Success 200 {object} models.Hive
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /hives/{id} [get]
func (h *handler) GetHive(c *gin.Context) {
	id := c.Param("id")
	var hive models.Hive

	if result := h.db.Preload("Logs").Preload("Tasks").First(&hive, "hive_name = ?", id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Hive not found"})
		return
	}

	c.JSON(http.StatusOK, hive)
}

// UpdateHive godoc
// @Summary Update hive
// @Description Update an existing hive by its ID
// @Tags hives
// @Accept  json
// @Produce  json
// @Param id path int true "Hive ID"
// @Param hive body UpdateHiveInput true "Updated hive data"
// @Success 200 {object} models.Hive
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /hives/{id} [patch]
func (h *handler) UpdateHive(c *gin.Context) {
	id := c.Param("id")
	var hive models.Hive

	if result := h.db.First(&hive, "hive_name = ?", id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Hive not found"})
		return
	}

	var input UpdateHiveInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid input"})
		return
	}

	if result := h.db.Model(&hive).Updates(models.Hive{HiveName: input.HiveName}); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save"})
		return
	}

	c.JSON(http.StatusOK, hive)
}

// DeleteHive godoc
// @Summary Delete hive
// @Description Delete a hive by its ID
// @Tags hives
// @Produce  json
// @Param id path int true "Hive ID"
// @Success 204
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /hives/{id} [delete]
func (h *handler) DeleteHive(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid ID"})
		return
	}
	
	if result := h.db.Delete(&models.Hive{}, "hive_name = ?", id); result.Error != nil || result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Hive not found"})
		return
	}
	
	c.Status(http.StatusNoContent)
}


