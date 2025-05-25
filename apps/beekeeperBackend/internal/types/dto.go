package types

type CreateEntryInput struct {
	Content string `json:"content" binding:"required"`
	HiveID  int    `json:"hiveID" binding:"required"`
}

type UpdateEntryInput struct {
	Content *string `json:"content"`
	HiveID  *int    `json:"hiveID"`
}

type CreateHiveInput struct {
	HiveName int `json:"hiveName" binding:"required"`
}

type UpdateHiveInput struct {
	HiveName *int `json:"hiveName"`
}
