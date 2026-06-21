CREATE DATABASE IF NOT EXISTS cloud_user_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cloud_user_db;

CREATE TABLE IF NOT EXISTS sys_user (
  user_id BIGINT NOT NULL PRIMARY KEY COMMENT '云端用户ID',
  user_name VARCHAR(100) NOT NULL COMMENT '云端用户名',
  nick_name VARCHAR(100) NULL COMMENT '云端昵称',
  phonenumber VARCHAR(32) NULL COMMENT '手机号',
  INDEX idx_sys_user_user_name (user_name),
  INDEX idx_sys_user_nick_name (nick_name),
  INDEX idx_sys_user_phonenumber (phonenumber)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cloud_device_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cloud_device_db;

CREATE TABLE IF NOT EXISTS cgm_device (
  device_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设备记录ID',
  user_id BIGINT NOT NULL COMMENT '云端用户ID',
  sensor_num VARCHAR(128) NOT NULL COMMENT '传感器编号',
  wear_time DATETIME NULL COMMENT '佩戴时间',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '设备状态，1表示激活',
  INDEX idx_cgm_device_user_id (user_id),
  INDEX idx_cgm_device_sensor_num (sensor_num),
  INDEX idx_cgm_device_user_status (user_id, status),
  INDEX idx_cgm_device_wear_time (wear_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cgm_sensor_param (
  param_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '传感器参数ID',
  sensor_num VARCHAR(128) NOT NULL COMMENT '传感器编号',
  parameter TEXT NULL COMMENT '传感器校准参数，代码按英文逗号分隔数值解析',
  UNIQUE KEY uq_cgm_sensor_param_sensor_num (sensor_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS cloud_sensor_data_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cloud_sensor_data_db;

CREATE TABLE IF NOT EXISTS cgm_sensor (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '传感器读数ID',
  user_id BIGINT NOT NULL COMMENT '云端用户ID',
  sensor_num VARCHAR(128) NOT NULL COMMENT '传感器编号',
  index_time DATETIME NOT NULL COMMENT '读数时间',
  glu_value DECIMAL(10, 3) NULL COMMENT '血糖值',
  original_value DECIMAL(10, 3) NULL COMMENT '原始血糖值',
  current DECIMAL(12, 4) NULL COMMENT '电流值',
  INDEX idx_cgm_sensor_user_time (user_id, index_time),
  INDEX idx_cgm_sensor_sensor_time (sensor_num, index_time),
  INDEX idx_cgm_sensor_index_time (index_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
