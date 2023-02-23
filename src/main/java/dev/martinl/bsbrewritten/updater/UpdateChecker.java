package dev.martinl.bsbrewritten.updater;

import dev.martinl.bsbrewritten.BSBRewritten;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public class UpdateChecker {
    private final BSBRewritten instance;
    private final int project;
    private URL checkURL;
    private URL changelogURL;
    private URL vlnVersionListURL;
    private BukkitTask vlnVersionCheckTask;
    private boolean newerVersionAvailable = false;
    private String newVersion;
    private ArrayList<String> latestChangelog = new ArrayList<>();
    private boolean runningVulnerableVersion = false;

    public UpdateChecker(BSBRewritten instance, int projectID) {
        this.instance = instance;
        this.newVersion = instance.getDescription().getVersion();
        this.project = projectID;
        try {
            this.checkURL = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + projectID);
            this.changelogURL = new URL("https://raw.githubusercontent.com/lMartin3/BetterShulkerBoxesRewritten/master/changelog.txt");
            this.vlnVersionListURL = new URL("https://raw.githubusercontent.com/lMartin3/BetterShulkerBoxesRewritten/master/vulnerable_versions.txt");
        } catch (MalformedURLException localMalformedURLException) {
            Bukkit.getServer().getConsoleSender().sendMessage("Error: MalformedURLException, please send this to the developer");
        }

    }

    public String getResourceURL() {
        return "https://www.spigotmc.org/resources/" + this.project;
    }

    public String getActualVersion() {
        return this.instance.getDescription().getVersion();
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch(NumberFormatException ignore) {
            return 0;
        }
    }

    private boolean isNewVersion(String newVersion) {
        if(this.instance.getDescription().getVersion().equals(newVersion)) return false;
        Integer[] splitCurrent = Arrays.stream(instance.getDescription().getVersion().split("\\.")).map(this::parseInt).toArray(Integer[]::new);
        Integer[] splitNew = Arrays.stream(newVersion.split("\\.")).map(this::parseInt).toArray(Integer[]::new);
        for(int i=0;i<3;i++) {
            if(!splitCurrent[i].equals(splitNew[i])) return splitCurrent[i] < splitNew[i];
        }
        return false;
    }

    //GET request to a spigot api
    public boolean checkForUpdates() {
        try {
            URLConnection con = this.checkURL.openConnection();
            this.newVersion = new BufferedReader(new InputStreamReader(con.getInputStream())).readLine();
            boolean result = isNewVersion(this.newVersion);
            newerVersionAvailable = result;
            getChangelog();
            return result;
        } catch (IOException ioex) {
            instance.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Error! BSB could not check for updates:");
            instance.getServer().getConsoleSender().sendMessage(ioex.toString());
            return false;
        }
    }

    public void setupVulnerableVersionCheck(boolean disableOnDetection) {
        vlnVersionCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(instance, () -> {
            List<String> vulnerableVersions = getVulnerableVersionList();
            if (vulnerableVersions.contains(instance.getDescription().getVersion())) {
                runningVulnerableVersion = true;
                vlnVersionCheckTask.cancel();
                if(disableOnDetection) {
                    instance.setLockFeatures(true);
                    instance.getShulkerManager().closeAllInventories(false);
                }
                instance.getServer().getConsoleSender().sendMessage(ChatColor.RED +
                        "WARNING! You a re currently using a vulnerable version of Better Shulker Boxes!\n" +
                        "The plugin " + (instance.isLockFeatures() ? "disabled the features to prevent exploitation"
                        : "did NOT disable anything because of the configuration\n" +
                        ChatColor.GOLD + ChatColor.BOLD + ChatColor.UNDERLINE + "Please update the plugin as soon as possible!"));

            }
        }, 20, 20 * 60 * 10);
    }

    public ArrayList<String> getChangelog() {
        return getStringListFromURL(changelogURL);
    }

    public ArrayList<String> getVulnerableVersionList() {
        return getStringListFromURL(vlnVersionListURL);
    }

    //GET request to a (raw) file on the github repo
    public ArrayList<String> getStringListFromURL(URL url) {
        ArrayList<String> lines = new ArrayList<>();
        try {
            URLConnection con = url.openConnection();
            InputStreamReader inSR = new InputStreamReader(con.getInputStream());
            BufferedReader bufferedReader = new BufferedReader(inSR);
            String inputLine;
            //StringBuffer content = new StringBuffer();
            while ((inputLine = bufferedReader.readLine()) != null) {
                lines.add(inputLine);
            }
            bufferedReader.close();
            latestChangelog = lines;
            return lines;
        } catch (IOException ioex) {
            // This error flooded my console = =
//            instance.getServer().getConsoleSender().sendMessage(ChatColor.RED + "Error! BSB could not retrieve the changelog:");
//            instance.getServer().getConsoleSender().sendMessage(ioex.toString());
            latestChangelog = lines;
            return lines;
        }
    }

    public List<String> getUpdateMessages() {
        ArrayList<String> changes = new ArrayList<>();
        changes.add(ChatColor.YELLOW + "[BSB] " + ChatColor.GRAY + "A new version of BetterShulkerBoxes is available! You are currently running " +
                ChatColor.GOLD + instance.getDescription().getVersion() + ChatColor.GRAY + ", the newest version is " + ChatColor.GREEN + newVersion);
        changes.add(ChatColor.YELLOW + "[BSB] New version changes: ");
        latestChangelog.forEach(ch -> changes.add(ChatColor.GRAY + "-> " + ch));
        return changes;
    }

}