# Core Sync Module - Flow Diagram

## Overview

Magnus Sync handles player data synchronization between Minecraft servers using a resilient architecture with automatic failover and recovery. This document describes how data flows through the system during player join/leave events and failure scenarios.

## Architecture

```mermaid
graph TB
    subgraph "Application Layer"
        SyncService[SyncService]
        RecoveryService[BackupRecoveryService]
    end
    
    subgraph "Infrastructure Layer"
        ResilientRepo[ResilientPlayerRepository]
        CachedRepo[CachedPlayerRepository]
        LocalBackup[LocalBackupRepository]
    end
    
    subgraph "External Systems"
        Redis[(Redis Cache)]
        Postgres[(PostgreSQL)]
        Disk[(Local Disk)]
    end
    
    SyncService --> ResilientRepo
    ResilientRepo --> CachedRepo
    ResilientRepo --> LocalBackup
    CachedRepo --> Redis
    CachedRepo --> Postgres
    LocalBackup --> Disk
    RecoveryService --> LocalBackup
    RecoveryService --> CachedRepo
```

## Player Join (Loading Data)

When a player joins the server, we attempt to load their data from the Persistence Layer.

```mermaid
sequenceDiagram
    participant P as Player
    participant S as SyncService
    participant R as ResilientRepo
    participant DB as Postgres/Redis
    participant L as LocalBackup
    
    P->>S: Join Event
    S->>R: loadPlayerData(uuid)
    
    alt Database is ONLINE
        R->>DB: findByUuid()
        DB-->>R: Returns RemoteData
        
        R->>R: Freshness Check (Remote vs Local)
        
        alt Local is FRESHER
            R-->>S: Returns LocalBackup
            Note right of R: Critical: DB is stale!
        else Remote is NEWER
            R-->>S: Returns RemoteData
        end

    else Database is OFFLINE
        R->>L: hasBackup(uuid) (O(1) Memory Check)
        R->>DB: findByUuid()
        DB-->>R: Exception (Connection Refused)
        
        R->>L: findByUuid() (Check Local Backup)
        
        alt Backup Exists
            L-->>R: Returns BackupData
            R-->>S: Returns BackupData (Fallback)
            S->>P: Apply Backup Data
        else No Backup
            L-->>R: Returns Null
            R-->>S: Throws DataUnavailableException
            S->>P: KICK PLAYER ("Database Unavailable")
        end
    end
```

## Player Quit (Saving Data)

When a player leaves, we capture their state and save it.

```mermaid
sequenceDiagram
    participant P as Player
    participant S as SyncService
    participant R as ResilientRepo
    participant DB as Postgres/Redis
    participant L as LocalBackup
    
    P->>S: Quit Event
    S->>R: savePlayerData(snapshot)
    
    alt Database is ONLINE
        R->>DB: save(snapshot)
        DB-->>R: Success
    else Database is OFFLINE
        R->>DB: save(snapshot)
        DB-->>R: Exception!
        R->>L: save(snapshot)
        L-->>R: Saved to disk (.json)
        Note right of L: Data is safe locally until DB returns.
    end
```

## Recovery Strategy ("The Janitor")

A background service (`BackupRecoveryService`) runs every 5 minutes to attempt to merge local backups into the database.

```mermaid
flowchart TD
    Start(Start Janitor) --> CheckDB{Is DB Up?}
    CheckDB -- No --> Stop[Abort / Wait next cycle]
    CheckDB -- Yes --> Scan[Scan Local Backups]
    
    Scan --> Process{For Each Backup}
    
    Process --> CheckRemote[Fetch Remote DB Version]
    
    CheckRemote --> Compare{Compare Timestamps}
    
    Compare -- "Local > Remote" --> Overwrite[Save Local to DB]
    Overwrite --> Delete[Delete Local File]
    
    Compare -- "Local <= Remote" --> Discard[Discard Backup]
    Discard --> Delete
```

## Data Flow States

```mermaid
stateDiagram-v2
    [*] --> Healthy: Server Starts
    
    Healthy --> Degraded: DB Connection Lost
    Degraded --> Healthy: DB Reconnects
    
    state Healthy {
        [*] --> Normal
        Normal --> Saving: Player Quit
        Saving --> Normal: Save Complete
        Normal --> Loading: Player Join
        Loading --> Normal: Load Complete
    }
    
    state Degraded {
        [*] --> Fallback
        Fallback --> LocalSave: Player Quit
        LocalSave --> Fallback: Saved to Disk
        Fallback --> LocalLoad: Player Join
        LocalLoad --> Fallback: Load from Backup
        LocalLoad --> Kick: No Backup Found
    }
```

## Performance Optimization (Dirty Set)

To avoid checking the disk (`File.exists()`) on every player join, we use an **In-Memory Optimization**.

```mermaid
flowchart LR
    subgraph "Startup"
        Scan[Scan Backup Folder] --> InitSet[Initialize Set&lt;UUID&gt;]
    end
    
    subgraph "Player Join"
        Check{uuid in Set?}
        Check -- No --> Skip[Skip disk check<br>0ms overhead]
        Check -- Yes --> DiskIO[Read from disk]
    end
    
    subgraph "Fallback Save"
        Save[Save to disk] --> AddSet[Add uuid to Set]
    end
    
    subgraph "Janitor Cleanup"
        Delete[Delete backup] --> RemoveSet[Remove uuid from Set]
    end
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `SyncService` | Orchestrates load/save operations with lock management |
| `ResilientPlayerRepository` | Wraps primary repo with local fallback logic |
| `CachedPlayerRepository` | Redis cache layer over PostgreSQL |
| `LocalBackupRepository` | JSON file-based backup storage |
| `BackupRecoveryService` | "The Janitor" - periodic backup recovery |

## Configuration

Core sync is always enabled. Configure database connections in `config/magnus.json`:

```json
{
    "postgresUrl": "jdbc:postgresql://host:5432/magnus",
    "postgresUser": "user",
    "postgresPass": "password",
    "redisHost": "localhost",
    "redisPort": 6379
}
```

> [!IMPORTANT]
> If both database and local backup are unavailable, players will be **kicked** to prevent data corruption.

> [!TIP]
> Local backups are stored in `config/magnus/backups/` as JSON files named by player UUID.
