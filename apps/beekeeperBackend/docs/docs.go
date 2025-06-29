// Package docs Code generated by swaggo/swag. DO NOT EDIT
package docs

import "github.com/swaggo/swag"

const docTemplate = `{
    "schemes": {{ marshal .Schemes }},
    "swagger": "2.0",
    "info": {
        "description": "{{escape .Description}}",
        "title": "{{.Title}}",
        "termsOfService": "http://swagger.io/terms/",
        "contact": {
            "name": "API Support",
            "url": "http://www.swagger.io/support",
            "email": "support@swagger.io"
        },
        "license": {
            "name": "Apache 2.0",
            "url": "http://www.apache.org/licenses/LICENSE-2.0.html"
        },
        "version": "{{.Version}}"
    },
    "host": "{{.Host}}",
    "basePath": "{{.BasePath}}",
    "paths": {
        "/hives": {
            "get": {
                "description": "Get a list of all hives",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "hives"
                ],
                "summary": "List all hives",
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "type": "array",
                            "items": {
                                "$ref": "#/definitions/models.Hive"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "post": {
                "description": "Create a new hive with the provided information",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "hives"
                ],
                "summary": "Create a new hive",
                "parameters": [
                    {
                        "description": "Hive data",
                        "name": "hive",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/hives.CreateHiveInput"
                        }
                    }
                ],
                "responses": {
                    "201": {
                        "description": "Created",
                        "schema": {
                            "$ref": "#/definitions/models.Hive"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/hives/{id}": {
            "get": {
                "description": "Get a single hive by its hive name/ID, NOT by entry's ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "hives"
                ],
                "summary": "Get hive by its name/ID",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Hive ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Hive"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "delete": {
                "description": "Delete a hive by its ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "hives"
                ],
                "summary": "Delete hive",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Hive ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "204": {
                        "description": "No Content"
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "patch": {
                "description": "Update an existing hive by its ID",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "hives"
                ],
                "summary": "Update hive",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Hive ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    },
                    {
                        "description": "Updated hive data",
                        "name": "hive",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/hives.UpdateHiveInput"
                        }
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Hive"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/logs": {
            "get": {
                "description": "Retrieve all log entries from the database",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Get all log entries",
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "type": "array",
                            "items": {
                                "$ref": "#/definitions/models.Log"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "post": {
                "description": "Create a new log entry for a hive. If the hive doesn't exist, it will be created automatically.",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Create a new log entry",
                "parameters": [
                    {
                        "description": "Log creation data",
                        "name": "log",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/logs.CreateEntryInput"
                        }
                    }
                ],
                "responses": {
                    "201": {
                        "description": "Created",
                        "schema": {
                            "$ref": "#/definitions/models.Log"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/logs/last": {
            "get": {
                "description": "Retrieve the last log entry based on creation time",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Get the most recent log entry",
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Log"
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/logs/{id}": {
            "get": {
                "description": "Retrieve a specific log entry by its ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Get a log entry by ID",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Log ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Log"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "put": {
                "description": "Update an existing log entry by ID",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Update a log entry",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Log ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    },
                    {
                        "description": "Log update data",
                        "name": "log",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/logs.UpdateEntryInput"
                        }
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Log"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "delete": {
                "description": "Delete a log entry by ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "logs"
                ],
                "summary": "Delete a log entry",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Log ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "204": {
                        "description": "No Content"
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/tasks": {
            "get": {
                "description": "Retrieve all tasks from the database",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Get all tasks",
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "type": "array",
                            "items": {
                                "$ref": "#/definitions/models.Task"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "post": {
                "description": "Create a new task for a hive. If the hive doesn't exist, it will be created automatically.",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Create a new task",
                "parameters": [
                    {
                        "description": "Task creation data",
                        "name": "task",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/tasks.CreateEntryInput"
                        }
                    }
                ],
                "responses": {
                    "201": {
                        "description": "Created",
                        "schema": {
                            "$ref": "#/definitions/models.Task"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/tasks/last": {
            "get": {
                "description": "Retrieve the last task based on creation time",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Get the most recent task",
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Task"
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        },
        "/tasks/{id}": {
            "get": {
                "description": "Retrieve a specific task by its ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Get a task by ID",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Task ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Task"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "put": {
                "description": "Update an existing task by ID",
                "consumes": [
                    "application/json"
                ],
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Update a task",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Task ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    },
                    {
                        "description": "Task update data",
                        "name": "task",
                        "in": "body",
                        "required": true,
                        "schema": {
                            "$ref": "#/definitions/tasks.UpdateEntryInput"
                        }
                    }
                ],
                "responses": {
                    "200": {
                        "description": "OK",
                        "schema": {
                            "$ref": "#/definitions/models.Task"
                        }
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "500": {
                        "description": "Internal Server Error",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            },
            "delete": {
                "description": "Delete a task by ID",
                "produces": [
                    "application/json"
                ],
                "tags": [
                    "tasks"
                ],
                "summary": "Delete a task",
                "parameters": [
                    {
                        "type": "integer",
                        "description": "Task ID",
                        "name": "id",
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "204": {
                        "description": "No Content"
                    },
                    "400": {
                        "description": "Bad Request",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    },
                    "404": {
                        "description": "Not Found",
                        "schema": {
                            "type": "object",
                            "additionalProperties": {
                                "type": "string"
                            }
                        }
                    }
                }
            }
        }
    },
    "definitions": {
        "hives.CreateHiveInput": {
            "type": "object",
            "required": [
                "hiveName"
            ],
            "properties": {
                "hiveName": {
                    "type": "integer"
                }
            }
        },
        "hives.UpdateHiveInput": {
            "type": "object",
            "properties": {
                "hiveName": {
                    "type": "integer"
                }
            }
        },
        "logs.CreateEntryInput": {
            "type": "object",
            "required": [
                "content",
                "hiveID"
            ],
            "properties": {
                "content": {
                    "type": "string"
                },
                "hiveID": {
                    "type": "integer"
                }
            }
        },
        "logs.UpdateEntryInput": {
            "type": "object",
            "properties": {
                "content": {
                    "type": "string"
                },
                "hiveID": {
                    "type": "integer"
                }
            }
        },
        "models.Hive": {
            "type": "object",
            "properties": {
                "created_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                },
                "hive_name": {
                    "type": "integer",
                    "example": 123
                },
                "id": {
                    "type": "integer",
                    "example": 1
                },
                "logs": {
                    "type": "array",
                    "items": {
                        "$ref": "#/definitions/models.Log"
                    }
                },
                "tasks": {
                    "type": "array",
                    "items": {
                        "$ref": "#/definitions/models.Task"
                    }
                },
                "updated_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                }
            }
        },
        "models.Log": {
            "type": "object",
            "properties": {
                "content": {
                    "type": "string",
                    "example": "Hive inspection completed. Queen spotted, brood pattern looks healthy."
                },
                "created_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                },
                "hive_id": {
                    "type": "integer",
                    "example": 123
                },
                "id": {
                    "type": "integer",
                    "example": 1
                },
                "updated_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                }
            }
        },
        "models.Task": {
            "type": "object",
            "properties": {
                "content": {
                    "type": "string",
                    "example": "Check honey levels and replace frames"
                },
                "created_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                },
                "hive_id": {
                    "type": "integer",
                    "example": 123
                },
                "id": {
                    "type": "integer",
                    "example": 1
                },
                "updated_at": {
                    "type": "string",
                    "example": "2024-01-15T10:30:00Z"
                }
            }
        },
        "tasks.CreateEntryInput": {
            "type": "object",
            "required": [
                "content",
                "hiveID"
            ],
            "properties": {
                "content": {
                    "type": "string"
                },
                "hiveID": {
                    "type": "integer"
                }
            }
        },
        "tasks.UpdateEntryInput": {
            "type": "object",
            "properties": {
                "content": {
                    "type": "string"
                },
                "hiveID": {
                    "type": "integer"
                }
            }
        }
    },
    "securityDefinitions": {
        "BasicAuth": {
            "type": "basic"
        }
    }
}`

// SwaggerInfo holds exported Swagger Info so clients can modify it
var SwaggerInfo = &swag.Spec{
	Version:          "1.0",
	Host:             "localhost:8000",
	BasePath:         "/api",
	Schemes:          []string{},
	Title:            "Beekeeper API",
	Description:      "A beekeeping management API built with Go and Gin framework",
	InfoInstanceName: "swagger",
	SwaggerTemplate:  docTemplate,
	LeftDelim:        "{{",
	RightDelim:       "}}",
}

func init() {
	swag.Register(SwaggerInfo.InstanceName(), SwaggerInfo)
}
