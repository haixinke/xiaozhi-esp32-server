#!/usr/bin/env python3
"""对 manager-api 开发库执行只读查询。

须用 main/xiaozhi-server/venv/bin/python 运行（依赖 pymysql + pyyaml）：
    main/xiaozhi-server/venv/bin/python scripts/db-query.py "SHOW TABLES"

连接参数从 main/manager-api/src/main/resources/application-dev.yml 的
spring.datasource.druid 段读取，密码不落命令行、不进 shell 历史。

用法:
    scripts/db-query.py "SELECT * FROM ai_star_transaction LIMIT 5"
    scripts/db-query.py --file path/to/query.sql
    echo "SHOW TABLES" | scripts/db-query.py

仅允许 SELECT / SHOW / DESCRIBE / EXPLAIN 开头的语句，防止误写。
"""
import re
import sys
from pathlib import Path

import pymysql
import yaml

CONFIG_PATH = (
    Path(__file__).resolve().parent.parent
    / "main/manager-api/src/main/resources/application-dev.yml"
)

READONLY_PATTERN = re.compile(r"^\s*(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN)\b", re.IGNORECASE)


def load_datasource(config_path: Path) -> dict:
    """解析 application-dev.yml，提取 JDBC url / username / password。"""
    with open(config_path, encoding="utf-8") as f:
        config = yaml.safe_load(f)
    druid = config["spring"]["datasource"]["druid"]
    url = druid["url"]
    # jdbc:mysql://host:port/database?params —— 只取 host/port/database 三段
    match = re.match(r"jdbc:mysql://([^:/]+):(\d+)/([^?]+)", url)
    if not match:
        sys.exit(f"无法解析 JDBC url: {url}")
    host, port, database = match.groups()
    # password 须 str()：纯数字密码会被 yaml 解析成 int，pymysql 要求 buffer
    return {
        "host": host,
        "port": int(port),
        "user": str(druid["username"]),
        "password": str(druid["password"]),
        "database": database,
    }


def read_sql() -> str:
    """从 --file / 位置参数 / stdin 三种来源取 SQL。"""
    args = sys.argv[1:]
    if args and args[0] == "--file":
        if len(args) < 2:
            sys.exit(__doc__)
        return Path(args[1]).read_text(encoding="utf-8")
    if args:
        return args[0]
    if not sys.stdin.isatty():
        return sys.stdin.read()
    sys.exit(__doc__)


def main() -> None:
    sql = read_sql().strip().rstrip(";")
    if not READONLY_PATTERN.match(sql):
        sys.exit("仅允许只读语句 (SELECT/SHOW/DESCRIBE/EXPLAIN)")

    conn = pymysql.connect(**load_datasource(CONFIG_PATH), charset="utf8mb4")
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchall()
            if cur.description:
                print("\t".join(col[0] for col in cur.description))
            for row in rows:
                print("\t".join("" if v is None else str(v) for v in row))
            print(f"--- {len(rows)} rows ---", file=sys.stderr)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
