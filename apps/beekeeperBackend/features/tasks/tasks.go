package tasks

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

	taskRoutes := router.Group("/tasks")
	{
		taskRoutes.POST("", h.CreateTask)
		taskRoutes.GET("", h.ListTasks)
        taskRoutes.GET("/last", h.GetLastTask)
		taskRoutes.GET("/:id", h.GetTask)
		taskRoutes.PUT("/:id", h.UpdateTask)
		taskRoutes.DELETE("/:id", h.DeleteTask)
	}
}

// --- Handler ---

type handler struct {
	db *gorm.DB
}

// findOrCreateHive finds a hive by its ID (name) or creates it if it doesn't exist.
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


// CreateTask godoc
// @Summary Create a new task
// @Description Create a new task for a hive. If the hive doesn't exist, it will be created automatically.
// @Tags tasks
// @Accept  json
// @Produce  json
// @Param task body CreateEntryInput true "Task creation data"
// @Success 201 {object} models.Task
// @Failure 400 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /tasks [post]
func (h *handler) CreateTask(c *gin.Context) {
	var input CreateEntryInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid input"})
		return
	}

    err := h.db.Transaction(func(tx *gorm.DB) error {
        if _, err := h.findOrCreateHive(input.HiveID); err != nil {
            return err
        }

        task := models.Task{
            HiveID:  input.HiveID,
            Content: input.Content,
        }

        if result := tx.Create(&task); result.Error != nil {
            return result.Error
        }

        c.JSON(http.StatusCreated, task)
        return nil
    })

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Internal server error"})
		return
	}
}

// ListTasks godoc
// @Summary Get all tasks
// @Description Retrieve all tasks from the database
// @Tags tasks
// @Produce  json
// @Success 200 {array} models.Task
// @Failure 500 {object} map[string]string
// @Router /tasks [get]
func (h *handler) ListTasks(c *gin.Context) {
	var tasks []models.Task
	if result := h.db.Find(&tasks); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve tasks"})
		return
	}

	c.JSON(http.StatusOK, tasks)
}

// GetTask godoc
// @Summary Get a task by ID
// @Description Retrieve a specific task by its ID
// @Tags tasks
// @Produce  json
// @Param id path int true "Task ID"
// @Success 200 {object} models.Task
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /tasks/{id} [get]
func (h *handler) GetTask(c *gin.Context) {
	id := c.Param("id")
	var task models.Task

	if result := h.db.First(&task, id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Task not found"})
		return
	}

	c.JSON(http.StatusOK, task)
}

// GetLastTask godoc
// @Summary Get the most recent task
// @Description Retrieve the last task based on creation time
// @Tags tasks
// @Produce  json
// @Success 200 {object} models.Task
// @Failure 404 {object} map[string]string
// @Router /tasks/last [get]
func (h *handler) GetLastTask(c *gin.Context) {
    var task models.Task
    if result := h.db.Order("created_at desc").First(&task); result.Error != nil {
        if result.Error == gorm.ErrRecordNotFound {
            c.JSON(http.StatusNotFound, gin.H{"error": "No tasks found"})
            return
        }
        c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to retrieve last task"})
        return
    }
    c.JSON(http.StatusOK, task)
}


// UpdateTask godoc
// @Summary Update a task
// @Description Update an existing task by ID
// @Tags tasks
// @Accept  json
// @Produce  json
// @Param id path int true "Task ID"
// @Param task body UpdateEntryInput true "Task update data"
// @Success 200 {object} models.Task
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Failure 500 {object} map[string]string
// @Router /tasks/{id} [put]
func (h *handler) UpdateTask(c *gin.Context) {
	id := c.Param("id")
	var task models.Task

	if result := h.db.First(&task, id); result.Error != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Task not found"})
		return
	}

	var input UpdateEntryInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid ID or input"})
		return
	}

	if result := h.db.Model(&task).Updates(input); result.Error != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to update task"})
		return
	}

	c.JSON(http.StatusOK, task)
}

// DeleteTask godoc
// @Summary Delete a task
// @Description Delete a task by ID
// @Tags tasks
// @Produce  json
// @Param id path int true "Task ID"
// @Success 204
// @Failure 400 {object} map[string]string
// @Failure 404 {object} map[string]string
// @Router /tasks/{id} [delete]
func (h *handler) DeleteTask(c *gin.Context) {
	id, err := strconv.Atoi(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid ID"})
		return
	}
	
	if result := h.db.Delete(&models.Task{}, id); result.Error != nil || result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Task not found"})
		return
	}

	c.Status(http.StatusNoContent)
}

