<div>
<a href="https://modrinth.com/plugin/modpanel"><img alt="modrinth" height="40" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg"></a>
<a href="https://www.spigotmc.org/resources/modpanel.131671/"><img alt="spigot" height="40" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/spigot_vector.svg"></a> 
<a href="https://hangar.papermc.io/lighscent/ModPanel"><img alt="hangar" height="40" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg">
</a></div>


# ModPanel

ModPanel is a powerful Minecraft server management plugin that provides an intuitive web interface for administrators to monitor and manage player inventories in real-time. With ModPanel, you can easily view and modify player inventories, even when players are offline, thanks to its robust database integration.

## Features

This plugin is actually in early development, but here are some of the key features already implemented:

- **Web Interface** : A website accessible via `http://<server-ip>:9999` that provides a user-friendly dashboard for server administrators.
- **Advanced Inventory Management** :
  - Full inventory view (Main hand, Armor, Off hand).
  - Live editing: Move items in players' inventories via a "Drag & Drop" system on the web page.
  - Offline Support: Inventories are saved in a local database (H2), allowing modification of a player's inventory even when they are offline.

## Configuration

The configuration file `config.yml` allows you to manage the web server settings and security options. Below is an example configuration:
```yaml
server-port: 9999              # Web server port
ip-whitelist-enabled: true     # Enable IP whitelist
ip-whitelist:                  # List of allowed IP addresses
  - "127.0.0.1"
  - "0.0.0.0"
```

## Commands
The following commands are available for server administrators:

- `/mp reload` : Reload the plugin configuration.
- `/mp help` : Display the list of available commands.

## Roadmap

The project is under active development. Here are some upcoming features:

- Sanction management (Ban, Kick, Mute) directly from the web interface.
- Viewing and editing the Enderchest.
- User account authentication system (Login/Password) for enhanced security.
- Live viewing of chat and console logs.

## Statistics by bStats
<img src="https://bstats.org/signatures/bukkit/ModPanel.svg" alt="ModPanel"/>