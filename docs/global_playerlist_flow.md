# Global Player List Module - Flow Diagram

## Overview

The Global Player List module maintains a synchronized view of all players across the server network. It uses a heartbeat mechanism to publish local player lists and aggregates data from all servers into an in-memory map.

## Architecture

```mermaid
graph TB
    subgraph "Server A (survival)"
        HeartbeatA[Heartbeat Loop<br>every 2.5s] -->|publish| Redis[(Redis<br>magnus:playerlist)]
        Redis -->|subscribe| MapA[Player Map]
    end
    
    subgraph "Server B (lobby)"
        HeartbeatB[Heartbeat Loop<br>every 2.5s] -->|publish| Redis
        Redis -->|subscribe| MapB[Player Map]
    end
    
    MapA --> GlistA["/glist Command"]
    MapB --> GlistB["/glist Command"]
```

## Heartbeat Sequence

```mermaid
sequenceDiagram
    participant Server as Local Server
    participant GPLS as GlobalPlayerListService
    participant Redis as Redis
    participant GPLS2 as GlobalPlayerListService<br>(Other Server)

    loop Every 2.5 seconds
        Server->>GPLS: getOnlinePlayers()
        GPLS->>GPLS: build ServerPlayerInfo
        GPLS->>Redis: PUBLISH magnus:playerlist {json}
    end
    
    Redis->>GPLS2: onMessage callback
    GPLS2->>GPLS2: update serverPlayers map
    GPLS2->>GPLS2: cleanupStaleEntries()
```

## /glist Command Flow

```mermaid
sequenceDiagram
    participant P as Player
    participant Cmd as GlistCommand
    participant GPLS as GlobalPlayerListService
    
    P->>Cmd: /glist
    Cmd->>GPLS: getGlobalPlayerCount()
    Cmd->>GPLS: getPlayersByServer()
    GPLS->>GPLS: cleanupStaleEntries()
    GPLS-->>Cmd: Map<server, players>
    Cmd->>P: formatted output
```

## Data Structures

### ServerPlayerInfo (Heartbeat Payload)

```json
{
  "serverName": "survival",
  "players": [
    {"uuid": "...", "name": "Player1"},
    {"uuid": "...", "name": "Player2"}
  ],
  "timestamp": 1706654400000
}
```

### In-Memory Map

```
ConcurrentHashMap<String, ServerPlayerInfo>
├── "survival" → ServerPlayerInfo(players=[...], timestamp=...)
├── "lobby"    → ServerPlayerInfo(players=[...], timestamp=...)
└── "creative" → ServerPlayerInfo(players=[...], timestamp=...)
```

## Stale Entry Cleanup

Servers that haven't sent a heartbeat in **10 seconds** are automatically removed from the map. This handles:
- Server crashes
- Network partitions
- Graceful shutdowns

## /glist Output Example

```
§6§l=== Global Player List ===
§7Total players online: §f15

§asurvival §7[8]: §fPlayer1, §fPlayer2, §fPlayer3, ...
§alobby §7[5]: §fNewPlayer, §fGuest1, ...
§acreative §7[2]: §fBuilder1, §fBuilder2
```

## Configuration

Enable in `config/magnus.json`:

```json
{
  "serverName": "survival",
  "enableGlobalPlayerList": true
}
```

## Coroutine Architecture

```mermaid
graph LR
    subgraph "Dispatchers.IO"
        Job[Heartbeat Job] --> Delay[delay 2.5s]
        Delay --> Publish[Publish to Redis]
        Publish --> Job
    end
    
    Shutdown[onDisable] -->|cancel| Job
```

> [!TIP]
> The heartbeat runs on `Dispatchers.IO` to avoid blocking the server thread.
