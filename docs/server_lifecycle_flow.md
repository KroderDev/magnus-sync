# Server Lifecycle - Flow Diagram

## Overview

This document describes the lifecycle of the Magnus Sync mod, from server initialization to graceful shutdown. It details the initialization of infrastructure services and the fail-safe mechanisms implemented to prevent data loss during shutdown.

## Server Startup (Mod Initialization)

The `Magnus.kt` class (Composition Root) orchestrates the wiring of all components when the server starts.

```mermaid
sequenceDiagram
    participant F as Fabric Loader
    participant M as Magnus (Composition Root)
    participant C as ConfigLoader
    participant DB as Postgres/Redis
    participant R as Repositories
    participant MM as ModuleManager
    participant MB as MessageBus
    
    F->>M: onInitialize()
    M->>C: load()
    
    Note over M, DB: Infrastructure Setup
    M->>DB: Connect to PostgreSQL
    M->>DB: Verify/Migrate Schema
    M->>DB: Connect to Redis (JedisPool)
    
    M->>R: Initialize Repositories (Resilient/Cached)
    M-->>MM: Initialize ModuleManager
    M-->>MB: Initialize SecureMessageBus
    
    Note over M, MM: Module Registration
    M->>MM: Register & Enable Modules
    Note right of MM: GlobalChat, InventorySync, etc.
    
    M->>M: Register Shutdown Hook
    Note over M: Initialization Success [Ready]
```

## Server Shutdown (Graceful Stop)

To prevent the common "Race Condition" where players are still online when the database closes, Magnus implements a priority shutdown sequence.

```mermaid
sequenceDiagram
    participant S as Minecraft Server (/stop)
    participant M as Magnus Hook
    participant IS as InventorySyncModule
    participant SS as SyncService
    participant MM as ModuleManager
    participant MB as MessageBus
    participant DB as Postgres/Redis
    
    S->>M: SERVER_STOPPING Event
    
    Note over M, SS: CRITICAL: Data Preservation
    M->>IS: forceSaveAllPlayers(onlinePlayers)
    IS->>SS: saveAllPlayerData(snapshotList)
    Note right of SS: Fail-safe: Save each player independently
    SS-->>M: Save Complete
    
    Note over M, DB: Graceful Cleanup
    M->>MM: shutdown() (Disable all modules)
    M->>MB: close() (Disconnect listeners)
    M->>DB: close() (Close Jedis/DB Pools)
    
    M-->>S: Cleanup Finished
```

## Shutdown Priority

The shutdown process follows a strict order to ensure data integrity:

1.  **Forced Save**: Online players are snapshotted and saved to the persistent store immediately.
2.  **Module Shutdown**: Application-level logic is turned off.
3.  **Communication Closure**: Message buses are closed to stop incoming/outgoing global traffic.
4.  **Resource Release**: Database and cache connection pools are closed last.

> [!IMPORTANT]
> The `saveAllPlayerData` method in `SyncService` is designed to be "fail-safe". If saving one player fails due to a timeout or connection issue, it will log the error and continue with the remaining players, ensuring maximum data recovery during the shutdown window.

> [!NOTE]
> This priority sequence avoids the "Race Condition" where the `PlayerQuitEvent` might fire *after* the database connection has already been terminated by the mod's shutdown hook.
