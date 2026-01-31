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

*   **Multi-Server Sync**: Syncs Inventory, Ender Chest, Health, Hunger, XP.
*   **High Performance**: Uses **Redis** for sub-millisecond data retrieval.
*   **Persistence**: Uses **PostgreSQL** for permanent reliable storage.
*   **Fault Tolerance**:
    *   **Circuit Breaker**: Detects database failures automatically.
    *   **Local Fallback**: Saves data to disk if the database goes down.
    *   **Auto-Recovery**: "The Janitor" service automatically restores data when the database comes back online.

## Configuration

Magnus requires a **PostgreSQL** database and a **Redis** instance to function.

1.  **Generate Config**: Start your server once to generate the default configuration file at `config/magnus.json`.
2.  **Edit Credentials**: Stop the server and open `config/magnus.json`. Fill in your database and cache details:
    ```json
    {
        "postgresUrl": "jdbc:postgresql://your-db-host:5432/magnus",
        "postgresUser": "your_user",
        "postgresPass": "your_password",
        "redisHost": "your-redis-host",
        "redisPort": 6379,
        "redisPass": null
    }
    ```
3.  **Launch**: Restart your server. Magnus will automatically verify the connection and create the necessary database schema.

## Architecture

For detailed architecture logic and failure recovery behavior, see [docs/BEHAVIOR.md](https://github.com/KroderDev/magnus/blob/master/docs/BEHAVIOR.md).
