package logs

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
    "gorm.io/gorm/clause"

	"beekeeper-api/models"
)

// --- Structs for Input Validation ---

type CreateEntryInput struct {
	Content string `json:"content" binding:"required"`
	HiveID  int    `json:"hiveID" binding:"required"`
}

type UpdateEntryInput struct {
	Content string `json:"content"`
	HiveID  int    `json:"hiveID"`
}

// --- Route Registration ---

func RegisterRoutes(router *gin.RouterGroup, db *gorm.DB) {
	h := &handler{db: db}

	logRoutes := router.Group("/logs")
	{
		logRoutes.POST("", h.CreateLog)
		logRoutes.GET("", h.ListLogs)
        logRoutes.GET("/last", h.GetLastLog)
		logRoutes.GET("/:id", h.GetLog)
		logRoutes.PUT("/:id", h.UpdateLog)
		logRoutes.DELETE("/:id", h.DeleteLog)
	}
}

// --- Handler ---

type handler struct {
	db *gorm.DB
}


// findOrCreateHive finds a hive by its ID (name) or creates it if it doesn't exist.
// This implements the "lazy creation" logic described in the ADR.
func (h *handler) findOrCreateHive(hiveID int) (models.Hive, error) {
    var hive models.Hive
    err := h.db.Clauses(clause.Locking{Strength: "UPDATE"}).First(&hive, "hive_name = ?", hiveID).Error
    if err != nil {
        if err == gorm.ErrRecordNotFound {
            // Hive not found, create it
            hive = models.Hive{HiveName: hiveID}
            if createErr := h.db.Create(&hive).Error; createErr != nil {
                return models.Hive{}, createErr
            }
        } else {
            // Another error occurred
            return models.Hive{}, err
        }
    }
    return hive, nil
}


// CreateLog godoc
// @Summary Create a new log entry
// @Description Create a new log entry for a hive. If the hive doesn't exist, it will be created automatically.
// @Tags logs
// @Accept  json
// @Produce  json
// @Param log body CreateEntryInput true "Log creation data"
// @Success 201 {object} models.Log
// @Failure 400 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /logs [post]
func (h *handler) CreateLog(c *gin.Context) {
	var input CreateEntryInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid input"})
		return
	}

    // Transaction to ensure atomicity of find/create hive and create log
    err := h.db.Transaction(func(tx *gorm.DB) error {
        if _, err := h.findOrCreateHive(input.HiveID); err != nil {
            return err
        }

        logEntry := models.Log{
            HiveID:  input.HiveID,
            Content: input.Content,
        }

        if result := tx.Create(&logEntry); result.Error != nil {
            return result.Error
        }

        c.JSON(http.StatusCreated, logEntry)
        return nil
    })

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Internal server error"})
		return
	}
}

// ListLogs godoc
// @Summary Get all log entries
// @Description Retrieve all log entries from the database
// @Tags logs
// @Produce  json
// @Success 200 {array} models.Log
// @Failure 500 {object} map[string]string
// @Router /logs [get]
func (h *handler) ListLogs(c *gin.Context) {
	var logs []models.Log
	if result := h.db.Find(&logs); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve logs"})
		return
	}

	c.JSON(http.StatusOK, logs)
}

// GetLog godoc
// @Summary Get a log entry by ID
// @Description Retrieve a specific log entry by its ID
// @Tags logs
// @Produce  json
// @Param id path int true "Log ID"
// @Success 200 {object} models.Log
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /logs/{id} [get]
func (h *handler) GetLog(c *gin.Context) {
	id := c.Param("id")
	var log models.Log

	if result := h.db.First(&log, id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Log not found"})
		return
	}

	c.JSON(http.StatusOK, log)
}

// GetLastLog godoc
// @Summary Get the most recent log entry
// @Description Retrieve the last log entry based on creation time
// @Tags logs
// @Produce  json
// @Success 200 {object} models.Log
// @Failure 404 {object} map[string]string
// @Router /logs/last [get]
func (h *handler) GetLastLog(c *gin.Context) {
    var log models.Log
    if result := h.db.Order("created_at desc").First(&log); result.Error != nil {
        if result.Error == gorm.ErrRecordNotFound {
            c.JSON(http.StatusNotFound, gin.H{"error": "No logs found"})
            return
        }
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve last log"})
        return
    }
    c.JSON(http.StatusOK, log)
}


// UpdateLog godoc
// @Summary Update a log entry
// @Description Update an existing log entry by ID
// @Tags logs
// @Accept  json
// @Produce  json
// @Param id path int true "Log ID"
// @Param log body UpdateEntryInput true "Log update data"
// @Success 200 {object} models.Log
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /logs/{id} [put]
func (h *handler) UpdateLog(c *gin.Context) {
	id := c.Param("id")
	var log models.Log

	if result := h.db.First(&log, id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Log not found"})
		return
	}

	var input UpdateEntryInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid ID or input"})
		return
	}

	if result := h.db.Model(&log).Updates(input); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update log"})
		return
	}

	c.JSON(http.StatusOK, log)
}

// DeleteLog godoc
// @Summary Delete a log entry
// @Description Delete a log entry by ID
// @Tags logs
// @Produce  json
// @Param id path int true "Log ID"
// @Success 204
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /logs/{id} [delete]
func (h *handler) DeleteLog(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid ID"})
		return
	}

	if result := h.db.Delete(&models.Log{}, id); result.Error != nil || result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Log not found"})
		return
	}
	
	c.Status(http.StatusNoContent)
}

