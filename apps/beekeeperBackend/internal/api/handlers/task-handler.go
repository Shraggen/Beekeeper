package handlers

import (
	"beekeeper-backend/internal/api/models"
	"beekeeper-backend/internal/types"
	"errors"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"
)

// TaskHandler handles all CRUD operations related to tasks.
type TaskHandler struct {
	BaseHandler
}

// CreateTask handles POST /tasks.
// It creates a new task for a hive. If the hive does not exist, it is created remotely.
func (h *TaskHandler) CreateTask(c *gin.Context) {
	var input types.CreateEntryInput
	var hive models.Hive

	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(400, gin.H{"error": "Invalid input"})
		return
	}

	task := models.Task{
		HiveID:  input.HiveID,
		Content: input.Content,
	}

	// Check if hive exists
	if err := h.DB.Where("hive_name = ?", task.HiveID).First(&hive).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			// Create hive remotely if not found
			newHive, err := h.CreateHiveRemote(c, input.HiveID)
			if err != nil {
				c.JSON(500, gin.H{"error": "Could not create hive"})
				return
			}
			hive = *newHive
		} else {
			c.JSON(500, gin.H{"error": err.Error()})
			return
		}
	}

	h.CreateEntry(c, &task)
}

// GetTaskByID handles GET /tasks/:id.
// It retrieves a single task entry by its ID.
func (h *TaskHandler) GetTaskByID(c *gin.Context) {
	var task models.Task
	h.GetEntryByID(c, &task)
}

// GetAllTasks handles GET /tasks.
// It returns all tasks from the database.
func (h *TaskHandler) GetAllTasks(c *gin.Context) {
	var tasks []models.Task
	h.GetAllEntries(c, &tasks)
}

// UpdateTask handles PUT /tasks/:id.
// It updates a task’s content and/or hive association.
func (h *TaskHandler) UpdateTask(c *gin.Context) {

	var task models.Task
	var input types.UpdateEntryInput
	h.UpdateEntry(c, &task, &input, func(model any, input any) {
		t := model.(*models.Task)
		i := input.(*types.UpdateEntryInput)

		if i.Content != nil {
			t.Content = *i.Content
		}
		if i.HiveID != nil {
			t.HiveID = *i.HiveID
		}
	})
}

// DeleteTask handles DELETE /tasks/:id.
// It deletes a task by its ID.
func (h *TaskHandler) DeleteTask(c *gin.Context) {
	var task models.Task
	h.DeleteEntry(c, &task)
}
