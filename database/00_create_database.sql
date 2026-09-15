-- Local development bootstrap only. Run this file with a MySQL administrator account.
CREATE DATABASE IF NOT EXISTS expense_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'expense_app'@'localhost' IDENTIFIED BY 'expense_app_dev';
CREATE USER IF NOT EXISTS 'expense_app'@'%' IDENTIFIED BY 'expense_app_dev';

GRANT ALL PRIVILEGES ON expense_platform.* TO 'expense_app'@'localhost';
GRANT ALL PRIVILEGES ON expense_platform.* TO 'expense_app'@'%';
FLUSH PRIVILEGES;

