# Magnus Sync (Server-Side)

![Build Status](https://img.shields.io/github/actions/workflow/status/KroderDev/magnus-sync/build.yml?branch=master&style=flat-square&label=Build)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brown?style=flat-square&logo=minecraft)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue?style=flat-square&logo=fabric)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)
![License](https://img.shields.io/github/license/KroderDev/magnus-sync?style=flat-square&color=blue)
![Version](https://img.shields.io/badge/Version-1.0.0-green?style=flat-square)


A high-performance, fault-tolerant inventory synchronization mod for Minecraft Fabric servers.

## Features

*   **Multi-Server Sync**: Syncs Inventory, Ender Chest, Health, Hunger, XP.
*   **High Performance**: Uses **Redis** for sub-millisecond data retrieval.
*   **Persistence**: Uses **PostgreSQL** for permanent reliable storage.
*   **Fault Tolerance**:
    *   **Circuit Breaker**: Detects database failures automatically.
    *   **Local Fallback**: Saves data to disk if the database goes down.
    *   **Auto-Recovery**: "The Janitor" service automatically restores data when the database comes back online.

## Installation

### Prerequisites
*   **Java 21** installed.
*   **PostgreSQL** database.
*   **Redis** server.

### Setup

1.  **Download**: Get the latest `.jar` from releases.
2.  **Install**: Drop the `.jar` into your server's `mods` folder.
3.  **Run Once**: Start the server to generate configuration files.
4.  **Configure**:
    *   Start the server once. The mod will generate a configuration file at `config/magnus.json`.
    *   Stop the server and edit `config/magnus.json`:
        ```json
        {
            "postgresUrl": "jdbc:postgresql://localhost:5432/magnus",
            "postgresUser": "postgres",
            "postgresPass": "password",
            "redisHost": "localhost",
            "redisPort": 6379,
            "redisPass": null
        }
        ```
    *   Set your actual PostgreSQL and Redis credentials.
5.  **Restart**: Restart the server.

### Database Setup
The mod automatically creates the necessary tables (`player_data`) on the first run. No manual SQL scripts required.

## Documentation

For detailed architecture logic and failure recovery behavior, see [docs/BEHAVIOR.md](docs/BEHAVIOR.md).
