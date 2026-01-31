# Global Player List Module - Flow Diagram

## Overview

The Global Player List module maintains a synchronized view of all players across the server network. It uses a heartbeat mechanism to publish local player lists and aggregates data from all servers into an in-memory map.

## Architecture

```mermaid
graph TB
    subgraph "Server A (survival)"
        HeartbeatA[Heartbeat Loop<br>every 2.5s] -->|publish| BusA[SecureRedisMessageBus]
        BusA -->|sign| Redis[(Redis<br>magnus:playerlist)]
        Redis -->|verify| BusA
        BusA --> MapA[Player Map]
    end
    
    subgraph "Server B (lobby)"
        HeartbeatB[Heartbeat Loop<br>every 2.5s] -->|publish| BusB[SecureRedisMessageBus]
        BusB -->|sign| Redis
        Redis -->|verify| BusB
        BusB --> MapB[Player Map]
    end
    
    MapA --> GlistA["/glist Command"]
    MapB --> GlistB["/glist Command"]
```

```mermaid
sequenceDiagram
    participant Server as Local Server
    participant GPLS as GlobalPlayerListService
    participant Bus as SecureRedisMessageBus
    participant Redis as Redis
    participant Bus2 as SecureRedisMessageBus<br>(Other Server)
    participant GPLS2 as GlobalPlayerListService<br>(Other Server)

    loop Every 2.5 seconds
        Server->>GPLS: getOnlinePlayers()
        GPLS->>GPLS: build ServerPlayerInfo
        GPLS->>Bus: publish(channel, json)
        Bus->>Bus: sign(json)
        Bus->>Redis: PUBLISH magnus:playerlist {signed}
    end
    
    Redis->>Bus2: onMessage callback
    Bus2->>Bus2: verify signature
    Bus2->>GPLS2: deliver payload
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

## Security

All heartbeats are automatically signed and verified by `SecureRedisMessageBus`:

| Protection | Description |
|------------|-------------|
| **HMAC-SHA256** | Prevents fake server injection |
| **Timestamp** | Rejects heartbeats older than 30 seconds |
| **Size Limit** | Drops payloads larger than 64KB |

See [Message Security Flow](message_security_flow.md) for details.
