# Magnus (Server-Side)

![Build Status](https://img.shields.io/github/actions/workflow/status/KroderDev/magnus/build.yml?branch=master&style=flat-square&label=Build)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brown?style=flat-square&logo=minecraft)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=flat-square&logo=fabric)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)
![License](https://img.shields.io/badge/License-LGPL_v3-blue?style=flat-square)
![GitHub Tag](https://img.shields.io/github/v/tag/KroderDev/magnus?style=flat-square&label=GitHub&color=green)
[![Modrinth Version](https://img.shields.io/modrinth/v/magnus?style=flat-square&logo=modrinth&color=00AF5C&label=Modrinth)](https://modrinth.com/mod/magnus)
[![CurseForge Version](https://img.shields.io/curseforge/v/1448049?style=flat-square&logo=curseforge&color=F16436&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/magnus)


A high-performance, fault-tolerant inventory synchronization mod for Minecraft Fabric servers.

## Features

### Core Sync
*   **Multi-Server Sync**: Syncs Inventory, Ender Chest, Health, Hunger, XP, Potion Effects.
*   **High Performance**: Uses **Redis** for sub-millisecond data retrieval.
*   **Persistence**: Uses **PostgreSQL** for permanent reliable storage.
*   **Fault Tolerance**:
    *   **Circuit Breaker**: Detects database failures automatically.
    *   **Local Fallback**: Saves data to disk if the database goes down.
    *   **Auto-Recovery**: "The Janitor" service automatically restores data when the database comes back online.

### Modules

| Module | Description |
|--------|-------------|
| **Global Chat** | Syncs chat messages across all servers via Redis pub/sub. Raw text format for compatibility with LuckPerms, Stylist, etc. |
| **Global Player List** | Maintains a global player count with heartbeat. Provides `/glist` command. |
| **Session Lock** | Prevents concurrent logins by locking player sessions during sync. Players are held in queue until their data is safely transferred. |

## Configuration

Magnus requires a **PostgreSQL** database and a **Redis** instance to function.

1.  **Generate Config**: Start your server once to generate the default configuration file at `config/magnus.json`.
2.  **Edit Config**: Stop the server and open `config/magnus.json`:

```json
{
    "postgresUrl": "jdbc:postgresql://your-db-host:5432/magnus",
    "postgresUser": "your_user",
    "postgresPass": "your_password",
    "redisHost": "your-redis-host",
    "redisPort": 6379,
    "redisPass": null,
    "serverName": "survival",
    "enableGlobalChat": false,
    "enableGlobalPlayerList": false,
    "enableSessionLock": false
}
```

3.  **Launch**: Restart your server. Magnus will automatically verify the connection and create the necessary database schema.

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `serverName` | `"default"` | Unique identifier for this server (e.g., `"survival"`, `"lobby"`) |
| `enableGlobalChat` | `false` | Enable cross-server chat synchronization |
| `enableGlobalPlayerList` | `false` | Enable global player list and `/glist` command |
| `enableSessionLock` | `false` | Enable session locking to prevent concurrent logins |

## Commands

| Command | Description |
|---------|-------------|
| `/glist` | Shows total player count and players grouped by server (requires `enableGlobalPlayerList`) |

## Architecture

For detailed architecture logic and failure recovery behavior, see [docs/BEHAVIOR.md](https://github.com/KroderDev/magnus/blob/master/docs/BEHAVIOR.md).

For module flow diagrams:
- [Global Chat Flow](docs/global_chat_flow.md)
- [Global Player List Flow](docs/global_playerlist_flow.md)
- [Session Lock Flow](docs/session_lock_flow.md)
