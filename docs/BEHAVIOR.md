# System Behavior & Architecture

This document describes how **Magnus Sync** handles data synchronization, failure scenarios, and automatic recovery using Hexagonal Architecture.

## 1. Player Join (Loading Data)

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
            S->>P: **KICK PLAYER** ("Database Unavailable")
        end
    end
```

## 2. Player Quit (Saving Data)

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

## 3. Recovery Strategy ("The Janitor")

A background service (`BackupRecoveryService`) runs every 5 minutes to attempt to merge local backups into the database.

```mermaid
flowchart TD
    Start(Start Janitor) --> CheckDB{Is DB Up?}
    CheckDB -- No --> Stop[Abort / Wait next cycle]
    CheckDB -- Yes --> Scan[Scan Local Backups]
    
    Scan --> Process{For Each Backup}
    
    Process --> CheckRemote[Fetch Remote DB Version]
    
    CheckRemote --> Compare{Compare Timestamps}
    
    Compare -- Local > Remote --> Overwrite[Save Local to DB]
    Overwrite --> Delete[Delete Local File]
    
    Compare -- Local <= Remote --> Discard[Discard Backup]
    Discard --> Delete
```

## 4. Performance Optimization (In-Memory Dirty Set)
To avoid checking the disk (`File.exists()`) on every player join, we use an **In-Memory Optimization**.

### The "Dirty Set" (Bloom Filter / Set)
*   **What**: A `Set<UUID>` kept in RAM.
*   **Init**: Scans the backup folder *once* on startup.
*   **Join**: Checks `set.contains(uuid)`.
    *   **False**: Skip disk check entirely. **0ms overhead**.
    *   **True**: Perform disk I/O and Freshness Check.
*   **Save**: On fallback save, add UUID to Set.
*   **Recover**: On Janitor delete, remove UUID from Set.
```
