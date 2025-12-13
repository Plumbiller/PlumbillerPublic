# PlumbillerPublic

## Meteor Client Addon

PlumbillerPublic is a Public meteor client addon created by Plumbiller. It's a work in progress, so expect some bugs and missing features which will be added and solved eventually.

### Contributions

Cumtributions are whalecum, you can do so by creating a pull request on 
https://github.com/Plumbiller/PlumbillerPublic

If you find any bugs or have any suggestions, please open an issue on
https://github.com/Plumbiller/PlumbillerPublic/issues

# Features

### Auto Rename Module
* This module let the player automatically rename items in bulk using anvils.
* You can set a prefix and/or suffix for the items, maintaining the original name or modifing it too.

![Auto Rename](/docs_assets/autorename.png)

### Restricted Areas
* Restricted areas are highly confurable zones that automatically manages (if you want to) the teleportation of allowed players in specific areas.
* All restricted areas are stored LOCALLY in your installation folder (for example .minecraft) in the PlumbillerAddon directory. Coordinates of the restricted areas are obviously stored in in a .json archive but remain private (as long as you don't actively share the archive).
* You can enable or disable the rendering of areas if you want to, and change its color. When the player is near a border, it renders it too, you can also customize the color of borders or disable it completely by setting the transparency (alpha) to 0, if you just wanna see the outline.
* The HUD displays the name of the area the player is currently in. In parentheses it also displays the number of players that are allowed in the area. The HUD can be customized too, you can change the scale, transparency or disable it completely.
* In order to render the areas and automatically manage your teleport requests (if enabled), you need to enable the RestrictedAreas module in the meteor menu.
* Note: I recommend using this module in case teleport requests from random players are annoying for you when you are in your homes or other private areas, and you don't want to accidentally accept a teleport request from a stranger, or if you want to automatically accept teleport requests from trusted players in a specific area.

### Restricted Areas Configuration
![Restricted Areas](/docs_assets/restrictedareas.png)

* As you can see in the image you can configure the HUD if you want more visual information about the area you are in, and how many players are allowed there. You can change the size, transparency or disable it.
* You can also choose if you want to render the area or not, and what colors you want to use for the lines and border (my favourite is rainbow mode). You can also choose between solid or pulsating render mode, tho I don't personally like the pulsating mode, but whatever.
* You will also see a title displaying on screen when entering an area, or Wilderness when exiting. This can also be disabled.
* Last but not least (and the whole point of this module in the first place), you can set the command to accept trusted players in the area, and the command to deny the rest of the players. In the image you can see these are set to /tpy and /tpn, which are the commands for 6b6t server. If your server has different commands such as /tpaccept or /tpdeny you can change the default values. 
* If your server has a command to toggle on and off teleport requests you can configure it here (for 6b6t is /tpt). This will make you automatically completely disable teleport requests when inside a restricted area with no trusted players, and enable it back when you exit the area or allow at least 1 player. Basically it automatically manages /tpt for you. As always, you can disable this too if you wanna manage /tpt by yourself.

### Restricted Areas Commands
The restricted area command is a meteor client command, meaning it is not a slash command, it's used by running .restrictedarea (subcommands) or its alias which is .ra (subcommands).

## .ra create (name) <area>
This command creates a new restricted area. It requires 2 arguments: the name of the area and optionally how big you want the area to be in every axis. The default size is 100 blocks.

## .ra delete (name)
This command deletes an existing restricted area. It requires 1 argument: the name of the area.

## .ra list
This command lists all the restricted areas.

## .ra allow (name) (player)
This command saves a player as trusted in a specific area. It requires 2 arguments: the name of the area and the player to allow. If the autoaccept (/tpy) config is enabled, it will automatically accept the allowed players' teleport requests when inside the area.

## .ra revoke (name) (player)
This command revokes a player's access to a specific area where you have previously allowed them with the allow subcommand. It requires 2 arguments: the name of the area and the player to revoke.

## .ra players (name)
This command lists all the players that have access to a specific area. It requires 1 argument: the name of the area.

## .ra cancel
If the auto accept config is enabled and the module detects a trusted player teleport request, it won't actually accept it immediately, instead it will wait for 10 seconds before accepting it. If you change your mind and you decide you don't want to accept the request from this trusted player, you always have this 10 seconds window to type .ra cancel and it won't be accepted. You can't disable this. Auto accept will always take 10 seconds to execute, just in case.

# Dependencies

* **Minecraft 1.21.7**

### Required Mods:
* **Meteor Client (1.21.7)**

* **Fabric Loader**

* **Fabric API**

### Installation:
1. Install Fabric Loader for Minecraft 1.21.7.
2. Place mods JAR in .minecraft/mods folder
3. Launch Minecraft with Fabric profile
4. Enable module in Meteor Client menu



If you are a nerdy good boi and you want to build the mod by yourself, I probably don't need to tell you how to do so. Do the gradlew thingy.