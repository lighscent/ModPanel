# ModPanel - Copilot Instructions

## Project Overview

ModPanel is a **comprehensive Spigot/Paper Minecraft plugin** (1.19+) that provides a web-based moderation panel with advanced inventory management. It embeds an HTTP server within the plugin and uses H2 for local database storage with persistent inventory data.

**Requirements:**
- Java 21
- Spigot/Paper 1.19+

## Architecture

```
Main.java (JavaPlugin entry point)
├── WebServer.java         - Embedded HTTP server with REST API
├── DatabaseManager.java   - H2 database operations (queries, connections)
├── DatabaseSchema.java    - H2 database table creation
├── MpCommand.java         - /mp command handler with reload/help subcommands
├── PlayerListener.java    - Player join/quit events with inventory persistence
├── ColoredLogger.java     - ANSI-colored console logging wrapper
└── listeners/PlayerListener.java - Player event handling
```

**Key data flows:**
- Plugin lifecycle (`onEnable`/`onDisable`) manages WebServer and DatabaseManager instances
- WebServer serves static HTML from `/pages/` resources and logs all access to DatabaseManager
- PlayerListener saves inventory data on quit and loads it on join for offline modifications
- Config reload recreates WebServer instance via `Main.setWebServer()` (note: requires port availability check)

**Database Schema:**
- `access_logs`: IP access logging (id, ip, timestamp, allowed)
- `players`: Player UUID/name storage (uuid, name, first_seen, last_seen)
- `player_inventories`: Persistent inventory data (main, armor, offhand items)

## Build & Test

```bash
mvn clean package    # Output: target/modpanel-0.1.0.jar (shaded with H2)
```

- **No test framework configured** - testing requires manual deployment to a Minecraft server
- JAR is shaded via `maven-shade-plugin` to include H2 dependency

## Code Conventions

### Dependency Injection Pattern
All major components receive dependencies via constructor injection, not static access:
```java
// Correct pattern - see MpCommand.java
public MpCommand(Main plugin, ColoredLogger coloredLogger, WebServer webServer, DatabaseManager databaseManager)
```

### Logging
Use `ColoredLogger` wrapper instead of raw `Logger` - provides ANSI coloring:
- `coloredLogger.info()` - green, normal operations
- `coloredLogger.warning()` - yellow, recoverable issues
- `coloredLogger.severe()` - red, critical errors (also used for shutdown messages)

### Resource Loading
HTML pages are loaded from classpath resources at `/pages/`:
```java
getClass().getResourceAsStream("/pages/index.html")  // See WebServer.loadHtmlPage()
```

### Configuration
- Config values in `src/main/resources/config.yml` with defaults in code
- Pattern: `getConfig().getInt("key", defaultValue)`
- IP whitelist uses `List<String>` from `getStringList()`

## Key Files

| File | Purpose |
|------|---------|
| [plugin.yml](src/main/resources/plugin.yml) | Plugin metadata, command definitions |
| [config.yml](src/main/resources/config.yml) | Runtime config (port, IP whitelist) |
| [pom.xml](pom.xml) | Maven build with shade plugin for fat JAR |

## Advanced Features

### Inventory Management System
- **Offline Support**: Load and modify player inventories even when offline
- **Database Persistence**: Inventory data survives server restarts
- **Live Sync**: Changes apply immediately to online players
- **Armor Validation**: Prevents invalid armor placements with client-side validation

### Web API Endpoints
- `GET /` - Main player dashboard
- `GET /api/players` - Player list with online status
- `GET /api/inventory?player=name` - Inventory data (works offline)
- `POST /api/move-item` - Move items between slots (works offline)
- `GET /inventory.html` - Inventory management interface

### Security & Access Control
- **IP Whitelisting**: Configurable IP restrictions
- **Access Logging**: All web requests logged to database
- **Player Validation**: UUID-based player identification

## Common Tasks

**Adding a new subcommand:** Extend switch statement in [MpCommand.onCommand()](src/main/java/com/xl1te/modpanel/commands/MpCommand.java) and add to `onTabComplete()`

**Adding a new web endpoint:** Add `server.createContext("/path", handler)` in [WebServer.startWebServer()](src/main/java/com/xl1te/modpanel/web/WebServer.java)

**Adding a new HTML page:** Place in `src/main/resources/pages/` and load via `loadHtmlPage("/pages/filename.html")`

**Player data management:** Use DatabaseManager methods:
- `storePlayer(uuid, name)` - Insert/update player record
- `getPlayerName(uuid)` - Retrieve player name by UUID
- `getPlayerUUID(name)` - Retrieve player UUID by name
- `updateLastSeen(uuid)` - Update player's last seen timestamp
- `savePlayerInventory(uuid, main, armor, offhand)` - Save inventory data
- `loadPlayerMainInventory(uuid)` - Load main inventory from database
- `loadPlayerArmorInventory(uuid)` - Load armor inventory from database
- `loadPlayerOffhandItem(uuid)` - Load offhand item from database

**Inventory slot mapping:**
- Main inventory: slots 0-35 (hotbar 0-8, inventory 9-35)
- Armor: slots 36-39 (boots=36, leggings=37, chestplate=38, helmet=39)
- Offhand: slot 40

**Armor restrictions (client-side validation):**
- Helmet slot (39): `*_helmet` items only
- Chestplate slot (38): `*_chestplate` items only
- Leggings slot (37): `*_leggings` items only
- Boots slot (36): `*_boots` items only


# Personnalized Instructions for GitHub Copilot

Do not make commands mvn clean package or similar build commands.