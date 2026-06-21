CREATE DATABASE IF NOT EXISTS experiment_manage
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE experiment_manage;

CREATE TABLE IF NOT EXISTS users (
  user_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户唯一标识符',
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
  password_hash VARCHAR(255) NOT NULL COMMENT '哈希加密后的密码',
  role ENUM('Admin', 'User') NOT NULL COMMENT '用户角色',
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS batches (
  batch_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '批次唯一标识符',
  batch_number VARCHAR(50) NOT NULL UNIQUE COMMENT '批次号名',
  start_time DATETIME NOT NULL COMMENT '批次开始时间',
  end_time DATETIME NULL COMMENT '批次结束时间',
  person_count INT NOT NULL DEFAULT 0 COMMENT '批次绑定的人员数量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS persons (
  person_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '人员唯一标识符',
  person_name VARCHAR(100) NOT NULL COMMENT '人员名字',
  gender ENUM('Male', 'Female', 'Other') NULL COMMENT '性别',
  age INT NULL COMMENT '年龄',
  batch_id INT NULL COMMENT '关联的批次ID',
  INDEX idx_persons_batch_id (batch_id),
  INDEX idx_persons_name (person_name),
  CONSTRAINT fk_persons_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sensor_details (
  sensor_detail_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '传感器详细信息唯一标识符',
  sterilization_date DATE NULL COMMENT '灭菌日期',
  test_number VARCHAR(100) NOT NULL UNIQUE COMMENT '传感器测试编号',
  probe_number VARCHAR(100) NOT NULL UNIQUE COMMENT '探针编号',
  value_0 DECIMAL(10, 4) NULL COMMENT '0.00浓度响应值',
  value_2 DECIMAL(10, 4) NULL COMMENT '2.00浓度响应值',
  value_5 DECIMAL(10, 4) NULL COMMENT '5.00浓度响应值',
  value_25 DECIMAL(10, 4) NULL COMMENT '25.00浓度响应值',
  sensitivity DECIMAL(20, 10) NULL COMMENT '线性灵敏度',
  r_value DECIMAL(20, 10) NULL COMMENT '相关系数R',
  destination VARCHAR(255) NULL COMMENT '去向',
  remarks VARCHAR(255) NULL COMMENT '备注',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  INDEX idx_sensor_details_test_number (test_number),
  INDEX idx_sensor_details_probe_number (probe_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS experiments (
  experiment_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '实验唯一标识符',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  experiment_content TEXT NULL COMMENT '实验具体内容描述',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_experiments_batch_id (batch_id),
  CONSTRAINT fk_experiments_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS experiment_members (
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '关联唯一标识符',
  experiment_id INT NOT NULL COMMENT '实验ID',
  person_id INT NOT NULL COMMENT '人员ID',
  UNIQUE KEY uq_experiment_member (experiment_id, person_id),
  INDEX idx_experiment_members_person_id (person_id),
  CONSTRAINT fk_experiment_members_experiment
    FOREIGN KEY (experiment_id) REFERENCES experiments(experiment_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_experiment_members_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS competitor_files (
  competitor_file_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '竞品文件唯一标识符',
  person_id INT NOT NULL COMMENT '关联的人员ID',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  file_path VARCHAR(512) NOT NULL COMMENT '文件存储路径',
  upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '文件上传时间',
  INDEX idx_competitor_files_person_id (person_id),
  INDEX idx_competitor_files_batch_id (batch_id),
  CONSTRAINT fk_competitor_files_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_competitor_files_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS finger_blood_files (
  finger_blood_file_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '指尖血文件唯一标识符',
  person_id INT NOT NULL COMMENT '关联的人员ID',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  collection_time DATETIME NOT NULL COMMENT '采集时间',
  blood_glucose_value DECIMAL(5, 2) NOT NULL COMMENT '血糖值',
  INDEX idx_finger_blood_person_id (person_id),
  INDEX idx_finger_blood_batch_id (batch_id),
  INDEX idx_finger_blood_collection_time (collection_time),
  CONSTRAINT fk_finger_blood_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_finger_blood_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sensors (
  sensor_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '传感器唯一标识符',
  person_id INT NOT NULL COMMENT '关联的人员ID',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  sensor_detail_id INT NULL COMMENT '关联的传感器详细信息ID',
  sensor_lot_no VARCHAR(255) NULL COMMENT '传感器批号',
  sensor_batch VARCHAR(255) NULL COMMENT '传感器批次',
  sensor_number VARCHAR(255) NULL COMMENT '传感器号',
  transmitter_id VARCHAR(255) NULL COMMENT '发射器号',
  start_time DATETIME NOT NULL COMMENT '传感器开始使用时间',
  end_time DATETIME NULL COMMENT '传感器结束使用时间',
  end_reason VARCHAR(255) NULL COMMENT '传感器结束使用原因',
  INDEX idx_sensors_person_id (person_id),
  INDEX idx_sensors_batch_id (batch_id),
  INDEX idx_sensors_sensor_detail_id (sensor_detail_id),
  INDEX idx_sensors_sensor_number (sensor_number),
  CONSTRAINT fk_sensors_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_sensors_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_sensors_sensor_detail
    FOREIGN KEY (sensor_detail_id) REFERENCES sensor_details(sensor_detail_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wear_records (
  wear_record_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '佩戴记录唯一标识符',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  person_id INT NOT NULL COMMENT '关联的人员ID',
  sensor_id INT NOT NULL COMMENT '关联的传感器ID',
  sensor_detail_id INT NULL COMMENT '关联的传感器详细信息ID',
  applicator_lot_no VARCHAR(255) NULL COMMENT '敷贴器批号',
  sensor_lot_no VARCHAR(255) NULL COMMENT '传感器批号',
  sensor_batch VARCHAR(255) NULL COMMENT '传感器批次',
  sensor_number VARCHAR(255) NULL COMMENT '传感器号',
  transmitter_id VARCHAR(255) NULL COMMENT '发射器号',
  wear_time DATE NOT NULL COMMENT '佩戴开始时间',
  wear_end_time DATE NULL COMMENT '佩戴结束时间',
  wear_position VARCHAR(20) NULL COMMENT '传感器佩戴位置',
  user_name VARCHAR(100) NULL COMMENT '人员信息',
  nickname VARCHAR(50) NULL COMMENT '云端用户匹配名称',
  abnormal_situation TEXT NULL COMMENT '异常情况',
  cause_analysis TEXT NULL COMMENT '原因分析',
  INDEX idx_wear_records_batch_id (batch_id),
  INDEX idx_wear_records_person_id (person_id),
  INDEX idx_wear_records_sensor_id (sensor_id),
  INDEX idx_wear_records_sensor_detail_id (sensor_detail_id),
  INDEX idx_wear_records_wear_time (wear_time),
  INDEX idx_wear_records_nickname (nickname),
  CONSTRAINT fk_wear_records_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_wear_records_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_wear_records_sensor
    FOREIGN KEY (sensor_id) REFERENCES sensors(sensor_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_wear_records_sensor_detail
    FOREIGN KEY (sensor_detail_id) REFERENCES sensor_details(sensor_detail_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_permissions (
  permission_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '权限唯一标识符',
  user_id INT NOT NULL COMMENT '关联的用户ID',
  module ENUM(
    'batch_management',
    'person_management',
    'experiment_management',
    'competitor_data',
    'finger_blood_data',
    'sensor_data',
    'sensor_details',
    'wear_records',
    'experiment_data_analysis',
    'sensor_data_visualization'
  ) NOT NULL COMMENT '模块名称',
  can_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '读取权限',
  can_write TINYINT(1) NOT NULL DEFAULT 0 COMMENT '写入权限',
  can_delete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '删除权限',
  UNIQUE KEY uq_user_permissions_user_module (user_id, module),
  CONSTRAINT fk_user_permissions_user
    FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activities (
  activity_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '活动唯一标识符',
  activity_type VARCHAR(50) NOT NULL COMMENT '活动类型',
  description TEXT NOT NULL COMMENT '活动描述',
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  user_id INT NULL COMMENT '操作用户ID',
  INDEX idx_activities_type (activity_type),
  INDEX idx_activities_createTime (createTime),
  INDEX idx_activities_user_id (user_id),
  CONSTRAINT fk_activities_user
    FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_experiment_data (
  data_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '数据唯一标识符',
  person_id INT NOT NULL COMMENT '关联的人员ID',
  batch_id INT NOT NULL COMMENT '关联的批次ID',
  experiment_day INT NOT NULL COMMENT '实验天数',
  mard_value DECIMAL(10, 5) NULL COMMENT '当天的MARD值',
  pard_value DECIMAL(10, 5) NULL COMMENT '当天的PARD值',
  record_date DATE NOT NULL COMMENT '记录的实际日期',
  UNIQUE KEY uq_daily_experiment_person_day (person_id, experiment_day),
  INDEX idx_daily_experiment_batch_id (batch_id),
  INDEX idx_daily_experiment_record_date (record_date),
  CONSTRAINT fk_daily_experiment_person
    FOREIGN KEY (person_id) REFERENCES persons(person_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_daily_experiment_batch
    FOREIGN KEY (batch_id) REFERENCES batches(batch_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
