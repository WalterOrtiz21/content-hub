#!/bin/bash
-- Archivo: docker/postgres/init/01-init-db.sql
-- Script de inicialización para PostgreSQL en Docker

-- Crear la base de datos principal si no existe
SELECT 'CREATE DATABASE contentshub' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'contentshub')\gexec

-- Crear usuario de aplicación con permisos limitados
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'contentshub_app') THEN
CREATE ROLE contentshub_app LOGIN PASSWORD 'contentshub_app_pass';
END IF;
END
$$;

-- Conectar a la base de datos contentshub
\c contentshub;

-- Otorgar permisos al usuario de aplicación
GRANT CONNECT ON DATABASE contentshub TO contentshub_app;
GRANT USAGE ON SCHEMA public TO contentshub_app;
GRANT CREATE ON SCHEMA public TO contentshub_app;

-- Ejecutar el schema principal
\i /db-scripts/schema.sql

-- Otorgar permisos en las tablas creadas
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO contentshub_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO contentshub_app;

-- Configurar permisos por defecto para objetos futuros
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO contentshub_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO contentshub_app;

-- Crear índices adicionales para performance
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_document_metadata_created_at ON document_metadata(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_composite ON audit_log(user_id, timestamp, action);

-- Configuraciones de performance
ALTER SYSTEM SET shared_preload_libraries = 'pg_stat_statements';
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;

-- Reload configuration
SELECT pg_reload_conf();

-- Información de status
SELECT 'PostgreSQL Database initialized successfully for Content Hub' as status;
