package com.leafclient.connect.sdk.paper;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Runs a task on the thread that owns a player. {@link FoliaScheduler} is the Folia one; on other servers
 * {@link PaperConnect} uses one Bukkit timer instead.
 */
interface PlayerScheduler {

    /**
     * Runs a task every tick, from the next tick on, on the thread that owns the player.
     *
     * @param plugin the plugin that owns the task
     * @param player the player
     * @param task the task
     * @param retired runs instead when the player leaves the server before the task is cancelled
     * @return what cancels the task, or null when the player has already left
     */
    Runnable everyTick(Plugin plugin, Player player, Runnable task, Runnable retired);
}
