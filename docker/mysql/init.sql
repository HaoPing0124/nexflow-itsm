-- NEXFLOW MySQL init (executed on first container start)
CREATE DATABASE IF NOT EXISTS nexflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'nexflow'@'%' IDENTIFIED BY 'nexflow_secret';
GRANT ALL PRIVILEGES ON nexflow.* TO 'nexflow'@'%';
FLUSH PRIVILEGES;
