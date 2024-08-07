package ares06.inactivityshutdown;


import java.util.logging.Logger;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.StopInstancesRequest;

public final class Inactivityshutdown extends JavaPlugin {
    private Logger log;
    private Integer inactivityTimeout;
    private PlayerQuitListener playerQuitListener;
    private PlayerJoinListener playerJoinListener;
    private Timer idleTimer;
    @Override
    public void onEnable() {
        this.log = getLogger();
        saveDefaultConfig();

        // Check if the plugin is enabled in the config
        boolean pluginEnabled = getConfig().getBoolean("Enabled");
        if (!pluginEnabled) {
            log.info("Inactivityshutdown plugin is disabled in the config. Plugin will not be loaded.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Get the current config
        this.inactivityTimeout = getConfig().getInt("InactivityTimeout");

        if (this.inactivityTimeout < 0) {
            log.warning("You cannot use a negative inactivityTimeout! Time set to 0 seconds.");
            this.inactivityTimeout = 0;
        }

        log.info(String.format("This server is running InactivityShutdown: " +
                        "It will stop after %d seconds with no player online.",
                this.inactivityTimeout));

        this.playerQuitListener = new PlayerQuitListener(this);
        this.playerJoinListener = new PlayerJoinListener(this);

        getServer().getPluginManager()
                .registerEvents(this.playerQuitListener, this);

        if (noPlayerOnline()) {
            startIdleTimer();
        }
    }

    @Override
    public void onDisable() {
        // Cancel the timer and remove all Listeners
        if (this.idleTimer != null) {
            log.finer("Cancelling timer");
            this.idleTimer.cancel();
        }
        HandlerList.unregisterAll(this);
    }

    public void onTimerExpired() {
        if(noPlayerOnline()) {
            log.info("No players online, shutting down");
            //Remove all our Listeners
            HandlerList.unregisterAll(this);
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    getServer().getWorlds().forEach((world) -> {
                        world.save();
                        log.info("World " + world.getName() + " saved.");
                    });
                    getServer().savePlayers();
                    log.info("Players saved.");

                    String accessKeyId = getConfig().getString("AccessKeyId");
                    String secretAccessKey = getConfig().getString("SecretAccessKey");
                    String region = getConfig().getString("Region");
                    String instanceId = getConfig().getString("InstanceId");
                    
                    BasicAWSCredentials creds = new BasicAWSCredentials(accessKeyId, secretAccessKey);
                    AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                        .withCredentials(new AWSStaticCredentialsProvider(creds))
                        .withRegion(region)
                        .build();

                    StopInstancesRequest request = new StopInstancesRequest().withInstanceIds(instanceId);
                    ec2.stopInstances(request);
                    log.info("Requested stop of EC2 instance with ID " + instanceId + ".");

                    getServer().shutdown();
                };
            });
        }
    }

    void onPlayerQuit() {
        if (lastPlayerOnline()) {
            log.info("The last player is leaving!");
            startIdleTimer();
        }
    }

    void onPlayerJoin() {
        log.fine("A player has joined!");

        // Remove the Listener
        log.finer("Unregistering PlayerJoinListener...");
        HandlerList.unregisterAll(this.playerJoinListener);

        log.finer("Aborting timer...");
        this.idleTimer.cancel();
    }

    private boolean lastPlayerOnline() {
        if (getServer().getOnlinePlayers().size() <= 1) {
            return true;
        } else {
            return false;
        }
    }

    private boolean noPlayerOnline() {
        if (getServer().getOnlinePlayers().size() <= 0) {
            return true;
        } else {
            return false;
        }
    }

    private void startIdleTimer() {
        if (this.inactivityTimeout == 0) {
            log.fine("idle_wait_time is 0, not scheduling timer!");
            onTimerExpired();
        } else {

            log.finer("Registering PlayerJoinListener...");
            getServer().getPluginManager()
                    .registerEvents(this.playerJoinListener, this);

            log.finer("Creating and scheduling timer...");
            this.idleTimer = new Timer();
            TimerTask idleTimerTask = new TimerTask() {
                @Override
                public void run() {
                    onTimerExpired();
                }
            };
            this.idleTimer.schedule(idleTimerTask, this.inactivityTimeout*1000);
        }
    }
}

