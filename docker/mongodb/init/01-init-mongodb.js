// Archivo: docker/mongodb/init/01-init-mongodb.js
// Script de inicialización para MongoDB en Docker

// Conectar a la base de datos contentshub
db = db.getSiblingDB('contentshub');

// Crear usuario de aplicación
db.createUser({
    user: 'contentshub_app',
    pwd: 'contentshub_app_pass',
    roles: [
        {
            role: 'readWrite',
            db: 'contentshub'
        }
    ]
});

// Crear colecciones principales con validación de esquema
db.createCollection('documents', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['title', 'content', 'owner_id', 'status', 'created_at'],
            properties: {
                title: {
                    bsonType: 'string',
                    description: 'Título del documento - requerido'
                },
                content: {
                    bsonType: 'object',
                    description: 'Contenido del documento en formato JSON'
                },
                owner_id: {
                    bsonType: 'long',
                    description: 'ID del propietario - requerido'
                },
                status: {
                    bsonType: 'string',
                    enum: ['DRAFT', 'PUBLISHED', 'ARCHIVED'],
                    description: 'Estado del documento'
                },
                document_type: {
                    bsonType: 'string',
                    description: 'Tipo de documento'
                },
                tags: {
                    bsonType: 'array',
                    items: {
                        bsonType: 'string'
                    },
                    description: 'Tags del documento'
                },
                version: {
                    bsonType: 'int',
                    minimum: 1,
                    description: 'Versión del documento'
                },
                is_public: {
                    bsonType: 'bool',
                    description: 'Si el documento es público'
                },
                created_at: {
                    bsonType: 'date',
                    description: 'Fecha de creación - requerida'
                },
                updated_at: {
                    bsonType: 'date',
                    description: 'Fecha de actualización'
                },
                last_modified_by: {
                    bsonType: 'long',
                    description: 'ID del último usuario que modificó'
                }
            }
        }
    }
});

// Crear colección para versiones de documentos
db.createCollection('document_versions', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['document_id', 'version_number', 'content', 'created_at', 'created_by'],
            properties: {
                document_id: {
                    bsonType: 'objectId',
                    description: 'ID del documento padre - requerido'
                },
                version_number: {
                    bsonType: 'int',
                    minimum: 1,
                    description: 'Número de versión - requerido'
                },
                content: {
                    bsonType: 'object',
                    description: 'Contenido de la versión'
                },
                changes_summary: {
                    bsonType: 'string',
                    description: 'Resumen de cambios'
                },
                created_at: {
                    bsonType: 'date',
                    description: 'Fecha de creación - requerida'
                },
                created_by: {
                    bsonType: 'long',
                    description: 'Usuario que creó la versión - requerido'
                }
            }
        }
    }
});

// Crear colección para comentarios
db.createCollection('comments', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['document_id', 'content', 'author_id', 'created_at'],
            properties: {
                document_id: {
                    bsonType: 'objectId',
                    description: 'ID del documento - requerido'
                },
                content: {
                    bsonType: 'string',
                    description: 'Contenido del comentario - requerido'
                },
                author_id: {
                    bsonType: 'long',
                    description: 'ID del autor - requerido'
                },
                parent_comment_id: {
                    bsonType: 'objectId',
                    description: 'ID del comentario padre (para respuestas)'
                },
                position: {
                    bsonType: 'object',
                    description: 'Posición del comentario en el documento'
                },
                is_resolved: {
                    bsonType: 'bool',
                    description: 'Si el comentario está resuelto'
                },
                created_at: {
                    bsonType: 'date',
                    description: 'Fecha de creación - requerida'
                },
                updated_at: {
                    bsonType: 'date',
                    description: 'Fecha de actualización'
                }
            }
        }
    }
});

// Crear colección para templates
db.createCollection('templates', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['name', 'content', 'category', 'created_by', 'created_at'],
            properties: {
                name: {
                    bsonType: 'string',
                    description: 'Nombre del template - requerido'
                },
                description: {
                    bsonType: 'string',
                    description: 'Descripción del template'
                },
                content: {
                    bsonType: 'object',
                    description: 'Contenido del template'
                },
                category: {
                    bsonType: 'string',
                    description: 'Categoría del template - requerida'
                },
                is_public: {
                    bsonType: 'bool',
                    description: 'Si el template es público'
                },
                created_by: {
                    bsonType: 'long',
                    description: 'Usuario creador - requerido'
                },
                created_at: {
                    bsonType: 'date',
                    description: 'Fecha de creación - requerida'
                }
            }
        }
    }
});

// Crear índices para optimización de consultas
db.documents.createIndex({ "owner_id": 1 });
db.documents.createIndex({ "status": 1 });
db.documents.createIndex({ "document_type": 1 });
db.documents.createIndex({ "tags": 1 });
db.documents.createIndex({ "created_at": -1 });
db.documents.createIndex({ "updated_at": -1 });
db.documents.createIndex({ "title": "text", "content.text": "text" }); // Índice de texto para búsqueda

db.document_versions.createIndex({ "document_id": 1, "version_number": -1 });
db.document_versions.createIndex({ "created_at": -1 });

db.comments.createIndex({ "document_id": 1 });
db.comments.createIndex({ "author_id": 1 });
db.comments.createIndex({ "created_at": -1 });
db.comments.createIndex({ "parent_comment_id": 1 });

db.templates.createIndex({ "category": 1 });
db.templates.createIndex({ "created_by": 1 });
db.templates.createIndex({ "is_public": 1 });
db.templates.createIndex({ "name": "text", "description": "text" });

// Insertar datos de ejemplo
db.documents.insertMany([
    {
        title: "Documento de Bienvenida",
        content: {
            type: "markdown",
            text: "# Bienvenido a Content Hub\n\nEste es tu primer documento.",
            blocks: []
        },
        owner_id: NumberLong(1),
        status: "PUBLISHED",
        document_type: "markdown",
        tags: ["welcome", "getting-started"],
        version: 1,
        is_public: true,
        created_at: new Date(),
        updated_at: new Date(),
        last_modified_by: NumberLong(1)
    },
    {
        title: "Documento Borrador",
        content: {
            type: "rich-text",
            text: "Este es un documento en borrador...",
            blocks: []
        },
        owner_id: NumberLong(1),
        status: "DRAFT",
        document_type: "rich-text",
        tags: ["draft"],
        version: 1,
        is_public: false,
        created_at: new Date(),
        updated_at: new Date(),
        last_modified_by: NumberLong(1)
    }
]);

db.templates.insertMany([
    {
        name: "Plantilla de Artículo",
        description: "Plantilla básica para artículos",
        content: {
            type: "markdown",
            text: "# Título del Artículo\n\n## Introducción\n\n## Desarrollo\n\n## Conclusión",
            blocks: []
        },
        category: "article",
        is_public: true,
        created_by: NumberLong(1),
        created_at: new Date()
    },
    {
        name: "Plantilla de Reunión",
        description: "Plantilla para actas de reunión",
        content: {
            type: "structured",
            text: "",
            blocks: [
                { type: "heading", text: "Acta de Reunión" },
                { type: "field", label: "Fecha", value: "" },
                { type: "field", label: "Participantes", value: "" },
                { type: "field", label: "Agenda", value: "" },
                { type: "field", label: "Acuerdos", value: "" }
            ]
        },
        category: "meeting",
        is_public: true,
        created_by: NumberLong(1),
        created_at: new Date()
    }
]);

print("MongoDB inicializado correctamente para Content Hub");
print("Colecciones creadas: documents, document_versions, comments, templates");
print("Índices creados para optimización de consultas");
print("Datos de ejemplo insertados");
