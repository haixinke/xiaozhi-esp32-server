#!/usr/bin/env python
"""Check graph tables in OceanBase using pyobvector"""
from pyobvector import ObVecClient

# Connection parameters
host = "localhost"
port = 2881
user = "root@test"
password = "123456"
db_name = "powermem"

try:
    print("Connecting to OceanBase...")
    client = ObVecClient(
        uri=f"{host}:{port}",
        user=user,
        password=password,
        db_name=db_name,
    )
    print("✓ Connected successfully")

    # Check tables
    tables = ['graph_entities', 'graph_relationships', 'memories', 'user_profiles']

    print("\nChecking tables:")
    for table in tables:
        exists = client.check_table_exists(table)
        status = "✓ EXISTS" if exists else "✗ MISSING"
        print(f"  {status}: {table}")

    # Close connection
    client.close()

    print("\n" + "=" * 60)
    if all(client.check_table_exists(t) for t in ['graph_entities', 'graph_relationships']):
        print("✓ Graph tables are present in database!")
    else:
        print("✗ Graph tables are NOT created yet")

except Exception as e:
    print(f"\n✗ Error: {e}")
    import traceback
    traceback.print_exc()
