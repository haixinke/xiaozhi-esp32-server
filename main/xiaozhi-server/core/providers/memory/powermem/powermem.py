#!/usr/bin/env python
# -*- coding: UTF-8 -*-
"""
@time: 2026/01/08
@file: powermem.py
@desc: PowerMem memory provider for xiaozhi-esp32-server
       PowerMem is an open-source agent memory component from OceanBase
       GitHub: https://github.com/oceanbase/powermem
       Website: https://www.powermem.ai/
@Author: wayyoungboy
"""

import asyncio
import json
import traceback
from typing import Optional, Dict, Any

from ..base import MemoryProviderBase, logger

TAG = __name__


def _register_oceanbase_dialect():
    """
    Register OceanBase dialect with SQLAlchemy.

    This is required for pyobvector to work with OceanBase database.
    Must be called before importing PowerMem SDK.
    """
    try:
        from sqlalchemy.dialects import registry
        # Register the OceanBase dialect for SQLAlchemy
        # This allows connection strings like "mysql+oceanbase://..."
        registry.register("mysql.oceanbase", "pyobvector.schema.dialect", "OceanBaseDialect")
        logger.bind(tag=TAG).info("OceanBase dialect registered with SQLAlchemy")
    except ImportError:
        # If sqlalchemy is not available, log a warning but don't fail
        logger.bind(tag=TAG).warning("SQLAlchemy not available, skipping OceanBase dialect registration")
    except Exception as e:
        # If registration fails, log but don't fail - PowerMem might handle this
        logger.bind(tag=TAG).warning(f"Failed to register OceanBase dialect: {e}")


def _fix_powermem_graph_store_bug():
    """
    Monkey-patch PowerMem SDK to fix graph_store initialization bug.

    Bug: In PowerMem v1.0.2, AsyncMemory passes the entire MemoryConfig object
    to GraphStoreFactory instead of just the graph_store dict.

    This patches both AsyncMemory and Memory classes.
    """
    try:
        from powermem.core.memory import Memory
        from powermem.core.async_memory import AsyncMemory
        import inspect

        # Patch function to fix the graph_store initialization
        def patch_asyncmemory_init(OriginalClass):
            original_source = inspect.getsource(OriginalClass.__init__)

            # Check if the bug exists
            if "config_to_pass = self.memory_config if self.memory_config else self.config" in original_source:
                original_init = OriginalClass.__init__

                def patched_init(self, config=None, storage_type=None, llm_provider=None, embedding_provider=None, agent_id=None):
                    # Extract graph_store config for later use
                    graph_config = None
                    should_enable_graph = False
                    if isinstance(config, dict) and "graph_store" in config:
                        graph_config = config["graph_store"]
                        should_enable_graph = graph_config.get("enabled", False)

                    # Completely remove graph_store from config to prevent original init from creating it
                    config_without_graph = {k: v for k, v in (config.items() if isinstance(config, dict) else [])}
                    config_without_graph.pop("graph_store", None)

                    # Call original init without graph_store config
                    original_init(self, config=config_without_graph, storage_type=storage_type,
                                llm_provider=llm_provider, embedding_provider=embedding_provider, agent_id=agent_id)

                    # Create graph_store correctly if it should be enabled
                    if should_enable_graph:
                        self.enable_graph = True
                        from powermem.storage.oceanbase.oceanbase_graph import MemoryGraph

                        # Create minimal MemoryConfig-like object for MemoryGraph
                        class MinimalConfig:
                            def __init__(self, gs_config, full_config):
                                # Create graph_store attribute
                                gs_inner_config = gs_config.get('config', gs_config)
                                self.graph_store = type('obj', (object,), {
                                    'config': gs_inner_config,
                                    'llm': None,
                                    'custom_prompt': None
                                })()

                                # Create llm attribute
                                self.llm = type('obj', (object,), {
                                    'provider': full_config.get('llm', {}).get('provider', 'qwen')
                                })()

                                # Create embedder and vector_store attributes
                                self.embedder = type('obj', (object,), {
                                    'provider': full_config.get('embedder', {}).get('provider', 'openai'),
                                    'config': full_config.get('embedder', {}).get('config', {})
                                })()

                                self.vector_store = type('obj', (object,), {
                                    'provider': full_config.get('vector_store', {}).get('provider', 'oceanbase'),
                                    'config': full_config.get('vector_store', {}).get('config', {})
                                })()

                        # Create the graph store directly
                        minimal_config = MinimalConfig(graph_config, config)
                        self.graph_store = MemoryGraph(minimal_config)

                OriginalClass.__init__ = patched_init
                return True
            return False

        # Patch both classes
        patched_async = patch_asyncmemory_init(AsyncMemory)
        patched_sync = patch_asyncmemory_init(Memory)

        if patched_async or patched_sync:
            logger.bind(tag=TAG).info("Applied PowerMem graph_store bug patch")
        else:
            logger.bind(tag=TAG).debug("PowerMem graph_store bug not detected, no patch needed")

    except Exception as e:
        logger.bind(tag=TAG).warning(f"Failed to patch PowerMem graph_store bug: {e}")


class MemoryProvider(MemoryProviderBase):
    """
    PowerMem memory provider implementation.

    PowerMem is an open-source agent memory component that provides
    efficient memory management for AI agents.

    Supports multiple storage backends (sqlite, oceanbase, postgres),
    LLM providers (qwen, openai, etc.) and embedding providers.

    Config options:
        - enable_user_profile: bool - Enable UserMemory for user profiling (requires OceanBase)
        - database_provider: str - Storage backend (sqlite, oceanbase, postgres)
        - llm_provider: str - LLM provider (qwen, openai, etc.)
        - embedding_provider: str - Embedding provider (qwen, openai, etc.)
    """

    def __init__(self, config: Dict[str, Any], summary_memory: Optional[str] = None):
        super().__init__(config)
        self.use_powermem = False
        self.memory_client = None
        self.enable_user_profile = False
        self.last_profile_content = ""  # Cache for user profile from UserMemory

        try:
            # Check if user profile mode is enabled
            # Handle both boolean and string representations (from JSON config)
            enable_user_profile_config = config.get("enable_user_profile", False)
            if isinstance(enable_user_profile_config, str):
                # Convert string "false"/"true" to boolean
                self.enable_user_profile = enable_user_profile_config.lower() in ("true", "1", "yes", "on")
            else:
                self.enable_user_profile = bool(enable_user_profile_config)

            # Get configuration parameters
            database_provider = config.get("database_provider", "sqlite")
            llm_provider = config.get("llm_provider", "qwen")
            embedding_provider = config.get("embedding_provider", "qwen")

            # Build powermem configuration dict
            # PowerMem supports two config styles:
            # 1. powermem style: database, llm, embedding
            # 2. mem0 style: vector_store, llm, embedder
            powermem_config = {}

            # Configure vector store / database
            if "vector_store" in config:
                vector_config = config["vector_store"]
                # Keep the nested config format as-is (PowerMem SDK expects it)
                # Manager-API format: {provider: "oceanbase", config: {host, port, ...}}
                # PowerMem SDK expects: {provider: "oceanbase", config: {...}}
                powermem_config["vector_store"] = vector_config
            elif "database" in config:
                powermem_config["database"] = config["database"]
            else:
                powermem_config["vector_store"] = {
                    "provider": database_provider,
                    "config": {}
                }

            # Configure LLM
            if "llm" in config:
                powermem_config["llm"] = config["llm"]
            else:
                llm_config = {}
                if config.get("llm_api_key"):
                    llm_config["api_key"] = config["llm_api_key"]
                if config.get("llm_model"):
                    llm_config["model"] = config["llm_model"]
                # Handle base_url based on provider type
                # - qwen provider uses dashscope_base_url
                # - openai provider uses openai_base_url
                if llm_provider == "qwen":
                    base_url = config.get("dashscope_base_url") or config.get("llm_base_url")
                    if base_url:
                        llm_config["dashscope_base_url"] = base_url
                else:
                    base_url = config.get("openai_base_url") or config.get("llm_base_url")
                    if base_url:
                        llm_config["openai_base_url"] = base_url

                powermem_config["llm"] = {
                    "provider": llm_provider,
                    "config": llm_config
                }

            # Configure embedder
            if "embedder" in config:
                powermem_config["embedder"] = config["embedder"]
            else:
                embedder_config = {}
                if config.get("embedding_api_key"):
                    embedder_config["api_key"] = config["embedding_api_key"]
                if config.get("embedding_model"):
                    embedder_config["model"] = config["embedding_model"]
                # Handle base_url based on provider type
                # - qwen provider uses dashscope_base_url
                # - openai provider uses openai_base_url
                # Priority: embedding_xxx_base_url > embedding_base_url > xxx_base_url
                if embedding_provider == "qwen":
                    base_url = config.get("embedding_dashscope_base_url") or config.get("embedding_base_url")
                    if base_url:
                        embedder_config["dashscope_base_url"] = base_url
                else:
                    base_url = config.get("embedding_openai_base_url") or config.get("embedding_base_url")
                    if base_url:
                        embedder_config["openai_base_url"] = base_url

                powermem_config["embedder"] = {
                    "provider": embedding_provider,
                    "config": embedder_config
                }

            # Configure graph store if enabled
            if "graph_store" in config:
                graph_config = config["graph_store"]
                # Convert max_hops from string to int if needed (PowerMem SDK expects int)
                if isinstance(graph_config, dict) and "config" in graph_config:
                    if "max_hops" in graph_config["config"] and isinstance(graph_config["config"]["max_hops"], str):
                        graph_config["config"]["max_hops"] = int(graph_config["config"]["max_hops"])
                # Keep the nested config format as-is
                powermem_config["graph_store"] = graph_config
                logger.bind(tag=TAG).info(f"Graph store enabled: provider={config['graph_store'].get('provider', 'unknown')}")

            # Use SDK's built-in prompts (no custom prompts needed)
            # SDK already handles role filtering via include_roles parameter

            # Log the final configuration for debugging
            logger.bind(tag=TAG).info(f"PowerMem config: {json.dumps(powermem_config, default=str, indent=2)}")

            # Register OceanBase dialect with SQLAlchemy before importing PowerMem
            # This is required for pyobvector to work correctly
            _register_oceanbase_dialect()

            # Apply PowerMem SDK bug fixes
            _fix_powermem_graph_store_bug()

            # Initialize memory client based on mode
            if self.enable_user_profile:
                from powermem import UserMemory
                self.memory_client = UserMemory(config=powermem_config)
                memory_mode = "UserMemory (用户画像模式)"
            else:
                from powermem import AsyncMemory
                self.memory_client = AsyncMemory(config=powermem_config)
                memory_mode = "AsyncMemory (普通记忆模式)"

            self.use_powermem = True

            logger.bind(tag=TAG).info(
                f"PowerMem initialized successfully: mode={memory_mode}, "
                f"database={powermem_config['vector_store']['provider']}, llm={powermem_config['llm']['provider']}, embedding={powermem_config['embedder']['provider']}"
            )            
            
        except ImportError as e:
            logger.bind(tag=TAG).error(
                f"PowerMem not installed. Please install with: pip install powermem. Error: {e}"
            )
            self.use_powermem = False
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to initialize PowerMem: {str(e)}")
            logger.bind(tag=TAG).debug(f"Detailed error: {traceback.format_exc()}")
            self.use_powermem = False

    async def save_memory(self, msgs, session_id=None):
        """
        Save conversation messages to PowerMem.

        Args:
            msgs: List of message objects with 'role' and 'content' attributes

            session_id: Session identifier (optional, for compatibility)

        Returns:
            Result from PowerMem API or None if failed
        """
        logger.bind(tag=TAG).info(f"save_memory called, use_powermem={self.use_powermem}, client={self.memory_client is not None}, msgs_len={len(msgs)}")
        try:
            if self.use_powermem and self.memory_client is not None and len(msgs) >= 2:
                # Format the content as a message list for PowerMem
                messages = []
                for message in msgs:
                    if message.role == "system":
                        continue

                    content = message.content

                    # Extract content from JSON format if present (for ASR with emotion/language tags)
                    # Same logic as in query_memory method
                    try:
                        if content and content.strip().startswith("{") and content.strip().endswith("}"):
                            data = json.loads(content)
                            if "content" in data:
                                content = data["content"]
                    except (json.JSONDecodeError, KeyError, TypeError):
                        # If parsing fails, use original content
                        pass

                    messages.append({"role": message.role, "content": content})

                # Add memory using PowerMem SDK
                logger.bind(tag=TAG).info(f"Calling PowerMem add(), user_id={self.role_id}, messages_count={len(messages)}, messages_sample={messages[:2] if messages else 'empty'}")
                result = self.memory_client.add(
                    messages=messages,
                    user_id=self.role_id,
                    native_language="zh",  # Force profile extraction in Chinese
                    # profile_type="topics",  # Extract structured topics (JSON) instead of plain text content
                    profile_type="content",
                    include_roles=["user"]  # Only extract profile from user messages, not AI assistant responses
                )
                # Handle both sync and async returns
                if asyncio.iscoroutine(result):
                    result = await result

                logger.bind(tag=TAG).info(f"Save memory result: {result}, type={type(result)}")

                # Cache user profile if UserMemory mode and profile was extracted
                if self.enable_user_profile and result:
                    if result.get('profile_extracted'):
                        # Store topics as JSON string for structured profile
                        topics = result.get('topics')
                        if topics:
                            import json
                            self.last_profile_content = json.dumps(topics, ensure_ascii=False, indent=2)
                            logger.bind(tag=TAG).debug(f"User profile topics extracted: {self.last_profile_content}")
                        else:
                            # Fallback to profile_content if topics not available
                            self.last_profile_content = result.get('profile_content', '')
                            logger.bind(tag=TAG).debug(f"User profile content extracted: {self.last_profile_content}")
            else:
                if not self.use_powermem or self.memory_client is None:
                    logger.bind(tag=TAG).warning("PowerMem is not available, skipping save_memory")
                elif len(msgs) < 2:
                    logger.bind(tag=TAG).info("Not enough messages to save (need at least 2)")
        except Exception as e:
            logger.bind(tag=TAG).error(f"Error saving memory: {str(e)}")
            logger.bind(tag=TAG).info(f"Detailed error: {traceback.format_exc()}")

        return None

    async def query_memory(self, query: str) -> str:
        """
        Query memories from PowerMem based on similarity search.

        Args:
            query: The search query string (may be JSON format with metadata)

        Returns:
            Formatted string of relevant memories or empty string if none found
        """
        if not self.use_powermem or self.memory_client is None:
            logger.bind(tag=TAG).warning("PowerMem is not available, skipping query_memory")
            return ""

        try:
            if not getattr(self, "role_id", None):
                logger.bind(tag=TAG).debug("No role_id set, returning empty memory")
                return ""

            # Extract content from JSON format if present (for ASR with emotion/language tags)
            search_query = query
            try:
                if query.strip().startswith("{") and query.strip().endswith("}"):
                    data = json.loads(query)
                    if "content" in data:
                        search_query = data["content"]
            except (json.JSONDecodeError, KeyError):
                # If parsing fails, use original query
                pass

            result_parts = []

            # If user profile mode is enabled, include user profile in results
            if self.enable_user_profile:
                profile = await self.get_user_profile()
                if profile:
                    result_parts.append(f"【用户画像】\n{profile}")

            # Search memories using PowerMem SDK
            if self.enable_user_profile:
                # UserMemory uses sync search
                results = await asyncio.to_thread(
                    self.memory_client.search,
                    query=search_query,
                    user_id=self.role_id,
                    limit=30
                )
            else:
                # AsyncMemory uses async search
                results = await self.memory_client.search(
                    query=search_query,
                    user_id=self.role_id,
                    limit=30
                )

            if results and "results" in results:
                # Format each memory entry with its update time
                memories = []
                for entry in results.get("results", []):
                    # Get timestamp from updated_at or created_at
                    timestamp = ""
                    if "updated_at" in entry and entry["updated_at"]:
                        timestamp = str(entry["updated_at"])
                    elif "created_at" in entry and entry["created_at"]:
                        timestamp = str(entry["created_at"])

                    if timestamp:
                        try:
                            # Parse and reformat the timestamp (remove milliseconds if present)
                            if "." in timestamp:
                                dt = timestamp.split(".")[0]
                            else:
                                dt = timestamp
                            formatted_time = dt.replace("T", " ")
                        except Exception:
                            formatted_time = timestamp
                    else:
                        formatted_time = ""

                    memory = entry.get("memory", "") or entry.get("content", "")
                    if memory:
                        if formatted_time:
                            # Store tuple of (timestamp, formatted_string) for sorting
                            memories.append((timestamp, f"[{formatted_time}] {memory}"))
                        else:
                            memories.append(("", memory))

                # Sort by timestamp in descending order (newest first)
                memories.sort(key=lambda x: x[0], reverse=True)

                # Extract only the formatted strings
                if memories:
                    memories_str = "\n".join(f"- {memory[1]}" for memory in memories)
                    result_parts.append(f"【相关记忆】\n{memories_str}")

            final_result = "\n\n".join(result_parts)
            logger.bind(tag=TAG).debug(f"Query results: {final_result}")
            return final_result

        except Exception as e:
            logger.bind(tag=TAG).error(f"Error querying memory: {str(e)}")
            logger.bind(tag=TAG).debug(f"Detailed error: {traceback.format_exc()}")
            return ""

    async def get_user_profile(self) -> str:
        """
        Get user profile from PowerMem (only available in UserMemory mode).
        
        In PowerMem 0.3.0+, user profile is automatically extracted during add()
        and cached in last_profile_content.

        Returns:
            Formatted user profile string or empty string if not available
        """
        if not self.use_powermem or self.memory_client is None:
            return ""

        if not self.enable_user_profile:
            logger.bind(tag=TAG).debug("User profile mode is not enabled")
            return ""

        # Return cached profile content from last add() operation
        if self.last_profile_content:
            return self.last_profile_content

        return ""

