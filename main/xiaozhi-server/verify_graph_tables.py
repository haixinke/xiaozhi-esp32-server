#!/usr/bin/env python
"""Verify graph tables using initialized PowerMem from the service"""
import sys
sys.path.insert(0, '.')

from core.providers.memory.powermem.powermem import MemoryProvider
import asyncio

# Load config from database
from config.config_loader import get_config

config = get_config()
memory_config = config.get("memory", {})

print("=" * 60)
print("Verifying Graph Tables Creation")
print("=" * 60)

try:
    # Initialize memory provider
    provider = MemoryProvider(memory_config)

    if provider.use_powermem and provider.memory_client:
        print(f"✓ PowerMem initialized: {type(provider.memory_client).__name__}")

        # Check if graph is enabled
        if hasattr(provider.memory_client, 'memory'):
            memory = provider.memory_client.memory
            if hasattr(memory, 'enable_graph') and memory.enable_graph:
                print(f"✓ Graph enabled: {memory.enable_graph}")

                if hasattr(memory, 'graph_store') and memory.graph_store:
                    print(f"✓ Graph store initialized: {type(memory.graph_store).__name__}")

                    # Check tables
                    client = memory.graph_store.client
                    tables = ['graph_entities', 'graph_relationships']

                    print("\nChecking tables:")
                    for table in tables:
                        exists = client.check_table_exists(table)
                        status = "✓ EXISTS" if exists else "✗ MISSING"
                        print(f"  {status}: {table}")

                    if all(client.check_table_exists(t) for t in tables):
                        print("\n✓ All graph tables created successfully!")
                    else:
                        print("\n✗ Some tables are missing")
                else:
                    print("✗ Graph store not initialized")
            else:
                print("✗ Graph not enabled")
        else:
            print("✗ Cannot access memory.graph")
    else:
        print("✗ PowerMem not initialized")

except Exception as e:
    print(f"\n✗ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
