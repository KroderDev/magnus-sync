# Session Lock Module - Flow Diagram

## Overview

The Session Lock module prevents data corruption during cross-server player transfers by ensuring that a player's data is fully saved before they can join another server.

## Architecture

```mermaid
graph TB
    subgraph "Server A (Source)"
        Disconnect[Player Disconnects] --> Lock[LockManager.lock]
        Lock --> Save[Save PlayerData]
        Save --> Unlock[LockManager.unlock]
    end
    
    subgraph "Redis"
        LockKey["magnus:lock:{uuid}<br>TTL: 30s"]
    end
    
    subgraph "Server B (Destination)"
        Join[Player Attempts Join] --> Check[LoginQueueHandler]
        Check --> Query{isLocked?}
        Query -->|Yes| Wait[Wait 500ms]
        Wait --> Query
        Query -->|No| Proceed[Allow Login]
        Proceed --> Load[Load PlayerData]
    end
    
    Lock --> LockKey
    Unlock --> LockKey
    Check --> LockKey
```

## Sequence Diagram

```mermaid
sequenceDiagram
    participant PA as Player
    participant SA as Server A
    participant Redis as Redis
    participant SB as Server B
    
    Note over PA,SB: Player transfers from Server A to Server B
    
    PA->>SA: Disconnect
    SA->>Redis: SET magnus:lock:{uuid} LOCKED EX 30
    SA->>SA: Save PlayerData to DB
    
    PA->>SB: Connect (Login)
    SB->>Redis: EXISTS magnus:lock:{uuid}
    Redis-->>SB: true (locked)
    
    Note over SB: LoginQueueHandler holds connection
    
    loop Every 500ms (max 10s)
        SB->>Redis: EXISTS magnus:lock:{uuid}
        Redis-->>SB: true
    end
    
    SA->>SA: Save complete
    SA->>Redis: DEL magnus:lock:{uuid}
    
    SB->>Redis: EXISTS magnus:lock:{uuid}
    Redis-->>SB: false (unlocked)
    SB->>SB: Proceed with login
    SB->>SB: Load PlayerData from DB
    SB->>PA: Join successful
```

## Lock Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Unlocked: Initial
    Unlocked --> Locked: Player Disconnect
    Locked --> Unlocked: Data Save Complete
    Locked --> Unlocked: TTL Expires (30s)
    
    note right of Locked
        Other servers wait
        before allowing join
    end note
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `LockManager` | Redis-based distributed lock with 30s TTL |
| `LoginQueueHandler` | Holds login connections while waiting for lock release |
| `SyncService` | Calls lock/unlock during save operations |

## Configuration

Enable in `config/magnus.json`:

```json
{
  "enableSessionLock": true
}
```

## Safety Features

> [!IMPORTANT]
> The lock has a **30-second TTL** as a safety net. If Server A crashes during save, the lock will auto-expire and players won't be permanently locked out.

> [!TIP]
> If Redis is unavailable, the lock check **fails open** (allows login) to prevent blocking players due to infrastructure issues.
