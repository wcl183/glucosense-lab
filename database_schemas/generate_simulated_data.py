from __future__ import annotations

import math
import random
from datetime import date, datetime, time, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "simulated_data.sql"

RANDOM_SEED = 20260618
READING_DAYS = 5
READING_INTERVAL_MINUTES = 15


BATCHES = [
    (1, "BATCH-202606-A", "2026-06-01 09:00:00", "2026-06-15 18:00:00"),
    (2, "BATCH-202606-B", "2026-06-08 09:00:00", "2026-06-22 18:00:00"),
    (3, "BATCH-202606-C", "2026-06-15 09:00:00", None),
]

PEOPLE = [
    (1, "Zhang Wei", "Male", 29, 1),
    (2, "Li Na", "Female", 34, 1),
    (3, "Wang Lei", "Male", 41, 1),
    (4, "Chen Yu", "Female", 26, 1),
    (5, "Liu Fang", "Female", 38, 2),
    (6, "Zhao Ming", "Male", 45, 2),
    (7, "Sun Qian", "Female", 31, 2),
    (8, "Yang Jie", "Male", 52, 2),
    (9, "Huang Lin", "Female", 28, 3),
    (10, "Zhou Hao", "Male", 36, 3),
    (11, "Wu Tong", "Female", 24, 3),
    (12, "Xu Rui", "Male", 33, 3),
]


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    escaped = str(value).replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def row(values):
    return "(" + ", ".join(sql_value(value) for value in values) + ")"


def insert_many(table, columns, rows, chunk_size=400):
    if not rows:
        return []
    statements = []
    cols = ", ".join(columns)
    for index in range(0, len(rows), chunk_size):
        chunk = rows[index:index + chunk_size]
        body = ",\n  ".join(row(item) for item in chunk)
        statements.append(f"INSERT INTO {table} ({cols}) VALUES\n  {body};")
    return statements


def glucose_at(reading_time: datetime, person_index: int) -> float:
    """Normal-person CGM curve in mmol/L with meal peaks and small noise."""
    minutes = reading_time.hour * 60 + reading_time.minute
    baseline = 5.05 + 0.18 * math.sin((person_index + 1) * 0.7)
    circadian = 0.18 * math.sin(2 * math.pi * (minutes - 240) / 1440)
    sleep_dip = -0.22 if minutes < 360 else 0

    meal_peaks = [
        (8 * 60, 1.35 + 0.05 * (person_index % 3), 62),
        (12 * 60 + 25, 1.75 + 0.08 * (person_index % 4), 78),
        (18 * 60 + 30, 1.55 + 0.06 * (person_index % 5), 88),
    ]

    meal_effect = 0.0
    for center, amplitude, width in meal_peaks:
        meal_effect += amplitude * math.exp(-((minutes - center) ** 2) / (2 * width ** 2))

    snack_effect = 0.28 * math.exp(-((minutes - 15 * 60 - 30) ** 2) / (2 * 55 ** 2))
    noise = random.gauss(0, 0.16)
    value = baseline + circadian + sleep_dip + meal_effect + snack_effect + noise
    return round(max(3.9, min(value, 8.7)), 1)


def generate():
    random.seed(RANDOM_SEED)

    output = []
    output.append("-- Simulated data generated from project code requirements.")
    output.append("-- It resets demo tables and inserts linked data across all four local databases.")
    output.append("SET NAMES utf8mb4;")
    output.append("SET FOREIGN_KEY_CHECKS = 0;")

    output.append("\nUSE experiment_manage;")
    for table in [
        "daily_experiment_data",
        "activities",
        "user_permissions",
        "wear_records",
        "sensors",
        "finger_blood_files",
        "competitor_files",
        "experiment_members",
        "experiments",
        "sensor_details",
        "persons",
        "batches",
        "users",
    ]:
        output.append(f"TRUNCATE TABLE {table};")

    output.append("\nUSE cloud_user_db;")
    output.append("TRUNCATE TABLE sys_user;")
    output.append("\nUSE cloud_device_db;")
    output.append("TRUNCATE TABLE cgm_sensor_param;")
    output.append("TRUNCATE TABLE cgm_device;")
    output.append("\nUSE cloud_sensor_data_db;")
    output.append("TRUNCATE TABLE cgm_sensor;")

    output.append("\nUSE experiment_manage;")
    output += insert_many(
        "users",
        ["user_id", "username", "password_hash", "role", "createTime", "updateTime"],
        [
            (1, "admin", "$2b$12$simulated.admin.password.hash", "Admin", "2026-06-01 08:00:00", "2026-06-01 08:00:00"),
            (2, "researcher", "$2b$12$simulated.researcher.password.hash", "User", "2026-06-01 08:30:00", "2026-06-01 08:30:00"),
        ],
    )

    output += insert_many(
        "batches",
        ["batch_id", "batch_number", "start_time", "end_time", "person_count"],
        [(batch_id, number, start, end, sum(1 for p in PEOPLE if p[4] == batch_id)) for batch_id, number, start, end in BATCHES],
    )

    output += insert_many(
        "persons",
        ["person_id", "person_name", "gender", "age", "batch_id"],
        PEOPLE,
    )

    sensor_details = []
    sensors = []
    wear_records = []
    cloud_users = []
    cloud_devices = []
    cloud_params = []
    sensor_readings = []
    finger_blood_rows = []
    daily_rows = []
    competitor_rows = []

    base_cloud_user_id = 10000
    cgm_row_id = 1
    finger_id = 1
    daily_id = 1

    for idx, (person_id, person_name, gender, age, batch_id) in enumerate(PEOPLE, start=1):
        batch_number = next(batch[1] for batch in BATCHES if batch[0] == batch_id)
        start_date = date(2026, 6, 1) + timedelta(days=(batch_id - 1) * 7 + (idx % 3))
        sensor_num = f"CGM-SN-{202606000 + idx}"
        test_number = f"T-{202606000 + idx}"
        probe_number = f"P-{202606000 + idx}"
        cloud_user_id = base_cloud_user_id + idx
        nickname = f"demo{idx:02d}"

        v0 = round(random.uniform(0.02, 0.09), 4)
        v2 = round(random.uniform(1.6, 2.4), 4)
        v5 = round(random.uniform(4.6, 5.8), 4)
        v25 = round(random.uniform(23.0, 27.5), 4)
        sensitivity = round((v25 - v0) / 25.0, 10)
        r_value = round(random.uniform(0.992, 0.999), 10)

        sensor_details.append((
            idx,
            (start_date - timedelta(days=10)).isoformat(),
            test_number,
            probe_number,
            v0,
            v2,
            v5,
            v25,
            sensitivity,
            r_value,
            "demo stock",
            "normal simulated sensor response",
            f"{start_date.isoformat()} 08:00:00",
        ))

        sensors.append((
            idx,
            person_id,
            batch_id,
            idx,
            f"LOT-{batch_id:02d}-{idx:03d}",
            test_number,
            sensor_num,
            f"TX-{8000 + idx}",
            f"{start_date.isoformat()} 08:30:00",
            None,
            None,
        ))

        wear_records.append((
            idx,
            batch_id,
            person_id,
            idx,
            idx,
            f"APP-{batch_id:02d}-{idx:03d}",
            f"LOT-{batch_id:02d}-{idx:03d}",
            test_number,
            sensor_num,
            f"TX-{8000 + idx}",
            start_date.isoformat(),
            None,
            "left_arm" if idx % 2 else "right_arm",
            person_name,
            nickname,
            None,
            None,
        ))

        cloud_users.append((cloud_user_id, person_name, nickname, f"1380000{idx:04d}"))
        cloud_devices.append((idx, cloud_user_id, sensor_num, f"{start_date.isoformat()} 08:30:00", 1))
        params = [round(1.0 + random.uniform(-0.035, 0.035), 5) for _ in range(21)]
        cloud_params.append((idx, sensor_num, ",".join(str(value) for value in params)))

        current_dt = datetime.combine(start_date, time(8, 30))
        end_dt = current_dt + timedelta(days=READING_DAYS)
        while current_dt < end_dt:
            glucose = glucose_at(current_dt, idx)
            original = round(max(3.7, min(glucose + random.gauss(0, 0.22), 9.0)), 1)
            current_na = round(5.0 + glucose * sensitivity * 18 + random.gauss(0, 0.9), 2)
            sensor_readings.append((
                cgm_row_id,
                cloud_user_id,
                sensor_num,
                current_dt.strftime("%Y-%m-%d %H:%M:%S"),
                glucose,
                original,
                current_na,
            ))
            cgm_row_id += 1
            current_dt += timedelta(minutes=READING_INTERVAL_MINUTES)

        for day_offset in range(READING_DAYS):
            record_date = start_date + timedelta(days=day_offset)
            for meal_hour, meal_label in [(7, "fasting"), (12, "lunch"), (19, "dinner")]:
                sample_time = datetime.combine(record_date, time(meal_hour, 20))
                sample_value = glucose_at(sample_time, idx) + random.gauss(0, 0.18)
                finger_blood_rows.append((
                    finger_id,
                    person_id,
                    batch_id,
                    sample_time.strftime("%Y-%m-%d %H:%M:%S"),
                    round(max(3.9, min(sample_value, 8.9)), 2),
                ))
                finger_id += 1

            daily_rows.append((
                daily_id,
                person_id,
                batch_id,
                day_offset + 1,
                round(random.uniform(7.2, 11.8), 5),
                round(random.uniform(7.8, 12.6), 5),
                record_date.isoformat(),
            ))
            daily_id += 1

        competitor_rows.append((
            idx,
            person_id,
            batch_id,
            f"E:/Desktop/codex/Interview/ExperimentMS-main/backend/uploads/competitor_files/simulated/{batch_number}_{person_name.replace(' ', '_')}.xlsx",
            f"{start_date.isoformat()} 10:00:00",
        ))

    output += insert_many(
        "sensor_details",
        [
            "sensor_detail_id", "sterilization_date", "test_number", "probe_number",
            "value_0", "value_2", "value_5", "value_25", "sensitivity", "r_value",
            "destination", "remarks", "created_time",
        ],
        sensor_details,
    )

    output += insert_many(
        "experiments",
        ["experiment_id", "batch_id", "experiment_content", "created_time"],
        [
            (1, 1, "Normal glucose fluctuation observation, batch A.", "2026-06-01 09:30:00"),
            (2, 2, "Normal glucose fluctuation observation, batch B.", "2026-06-08 09:30:00"),
            (3, 3, "Normal glucose fluctuation observation, batch C.", "2026-06-15 09:30:00"),
        ],
    )

    experiment_members = []
    member_id = 1
    for person_id, _, _, _, batch_id in PEOPLE:
        experiment_members.append((member_id, batch_id, person_id))
        member_id += 1

    output += insert_many(
        "experiment_members",
        ["id", "experiment_id", "person_id"],
        experiment_members,
    )

    output += insert_many(
        "competitor_files",
        ["competitor_file_id", "person_id", "batch_id", "file_path", "upload_time"],
        competitor_rows,
    )

    output += insert_many(
        "finger_blood_files",
        ["finger_blood_file_id", "person_id", "batch_id", "collection_time", "blood_glucose_value"],
        finger_blood_rows,
    )

    output += insert_many(
        "sensors",
        [
            "sensor_id", "person_id", "batch_id", "sensor_detail_id", "sensor_lot_no",
            "sensor_batch", "sensor_number", "transmitter_id", "start_time", "end_time", "end_reason",
        ],
        sensors,
    )

    output += insert_many(
        "wear_records",
        [
            "wear_record_id", "batch_id", "person_id", "sensor_id", "sensor_detail_id",
            "applicator_lot_no", "sensor_lot_no", "sensor_batch", "sensor_number",
            "transmitter_id", "wear_time", "wear_end_time", "wear_position",
            "user_name", "nickname", "abnormal_situation", "cause_analysis",
        ],
        wear_records,
    )

    permission_modules = [
        "batch_management",
        "person_management",
        "experiment_management",
        "competitor_data",
        "finger_blood_data",
        "sensor_data",
        "sensor_details",
        "wear_records",
        "experiment_data_analysis",
        "sensor_data_visualization",
    ]
    permissions = [(i + 1, 2, module, 1, 1 if module != "user_management" else 0, 0) for i, module in enumerate(permission_modules)]
    output += insert_many(
        "user_permissions",
        ["permission_id", "user_id", "module", "can_read", "can_write", "can_delete"],
        permissions,
    )

    output += insert_many(
        "activities",
        ["activity_id", "activity_type", "description", "createTime", "user_id"],
        [
            (1, "data_seed", "Generated simulated local and CGM data.", "2026-06-18 09:00:00", 1),
            (2, "batch_create", "Created three simulated experiment batches.", "2026-06-18 09:05:00", 1),
            (3, "sensor_sync", "Generated normal-person CGM fluctuation readings.", "2026-06-18 09:10:00", 1),
        ],
    )

    output += insert_many(
        "daily_experiment_data",
        ["data_id", "person_id", "batch_id", "experiment_day", "mard_value", "pard_value", "record_date"],
        daily_rows,
    )

    output.append("\nUSE cloud_user_db;")
    output += insert_many(
        "sys_user",
        ["user_id", "user_name", "nick_name", "phonenumber"],
        cloud_users,
    )

    output.append("\nUSE cloud_device_db;")
    output += insert_many(
        "cgm_device",
        ["device_id", "user_id", "sensor_num", "wear_time", "status"],
        cloud_devices,
    )
    output += insert_many(
        "cgm_sensor_param",
        ["param_id", "sensor_num", "parameter"],
        cloud_params,
    )

    output.append("\nUSE cloud_sensor_data_db;")
    output += insert_many(
        "cgm_sensor",
        ["id", "user_id", "sensor_num", "index_time", "glu_value", "original_value", "current"],
        sensor_readings,
        chunk_size=500,
    )

    output.append("\nSET FOREIGN_KEY_CHECKS = 1;")

    OUTPUT.write_text("\n\n".join(output) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT}")
    print(f"People: {len(PEOPLE)}")
    print(f"CGM readings: {len(sensor_readings)}")
    print(f"Finger blood rows: {len(finger_blood_rows)}")


if __name__ == "__main__":
    generate()
