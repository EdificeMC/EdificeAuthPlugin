package me.reherhold.edifice;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.format.TextColors;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

@Plugin(id = "edifice-auth-plugin")
public class EdificeAuthPlugin {

    @Inject @DefaultConfig(sharedRoot = false) private File configFile;
    @Inject @DefaultConfig(sharedRoot = false) private ConfigurationLoader<CommentedConfigurationNode> configLoader;
    @Inject private Logger logger;
    private Configuration config;

    @Listener
    public void preInit(GamePreInitializationEvent event) throws NoSuchAlgorithmException, KeyManagementException {
        setupConfig();
        setupRestClient();
    }

    private void setupRestClient() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslcontext = SSLContext.getInstance("TLSv1");
        System.setProperty("https.protocols", "TLSv1");
        TrustManager[] trustAllCerts = {new InsecureTrustManager()};
        sslcontext.init(null, trustAllCerts, new java.security.SecureRandom());

        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultHeaders(Lists.newArrayList(new BasicHeader("Authorization", this.config.getSecretKey())))
                .setSSLHostnameVerifier(new InsecureHostnameVerifier())
                .setSSLContext(sslcontext)
                .build();

        Unirest.setHttpClient(httpclient);
    }

    @Listener
    public void playerLogin(ClientConnectionEvent.Login event) throws UnirestException {
        GameProfile playerProfile = event.getProfile();
        JSONObject body = new JSONObject();
        body.put("playerId", playerProfile.getUniqueId().toString());

        HttpResponse<JsonNode> response = Unirest.post(this.config.getRestURI().toString() + "/auth/verificationcode").body(body).asJson();
        JSONObject responseBody = response.getBody().getObject();

        switch (response.getStatus()) {
            case 201:
                event.setMessage(Text.of("Your verification code is ", TextColors.GOLD, responseBody.getString("code"),
                        TextColors.WHITE, ". Go to ", TextColors.GREEN, this.config.getWebURI().toString() + "/signup",
                        TextColors.WHITE, " to finish your registration."));
                break;
            case 400:
                // TODO This is pretty hacky, needs fixed
                if (responseBody.getString("message").equals("User already signed up.")) {
                    event.setMessage(Text.of("You have already signed up for an account. You may log in at ",
                            TextColors.GOLD, this.config.getWebURI().toString()));
                } else {
                    errMessage(response.getStatus(), responseBody, event);
                }
                break;
            default:
                errMessage(response.getStatus(), responseBody, event);
                break;
        }
        event.setCancelled(true);

    }

    private void errMessage(int status, JSONObject responseBody, ClientConnectionEvent.Login event) {
        TextTemplate errTemplate = TextTemplate.of("An error occurrred. Status: ", TextTemplate.arg("status"),
                ". Message: ", TextTemplate.arg("message"));

        Text message = Text.of(responseBody.getString("error"));
        if (responseBody.has("message")) {
            message = Text.of(responseBody.getString("message"));
        }
        event.setMessage(errTemplate.apply(ImmutableMap.of("status", Text.of(status), "message", message)).build());
    }

    @Listener
    public void playerJoin(ClientConnectionEvent.Join event) {
        // If the player somehow managed to not be disconnected, kick them
        event.getTargetEntity().kick();
    }

    private void setupConfig() {
        if (!this.configFile.exists()) {
            saveDefaultConfig();
        } else {
            loadConfig();
        }
    }

    /**
     * Reads in config values supplied from the ConfigManager. Falls back on the
     * default configuration values in Settings
     */
    private void loadConfig() {
        ConfigurationNode rawConfig = null;
        try {
            rawConfig = this.configLoader.load();
            this.config = Configuration.MAPPER.bindToNew().populate(rawConfig);
        } catch (IOException e) {
            this.logger.warn("The configuration could not be loaded! Using the default configuration");
        } catch (ObjectMappingException e) {
            this.logger.warn("There was an error loading the configuration." + e.getStackTrace());
        }
    }

    /**
     * Saves a config file with default values if it does not already exist
     *
     */
    private void saveDefaultConfig() {
        try {
            this.logger.info("Generating config file...");
            this.configFile.getParentFile().mkdirs();
            this.configFile.createNewFile();
            CommentedConfigurationNode rawConfig = this.configLoader.load();

            try {
                // Populate config with default values
                this.config = Configuration.MAPPER.bindToNew().populate(rawConfig);
                Configuration.MAPPER.bind(this.config).serialize(rawConfig);
            } catch (ObjectMappingException e) {
                e.printStackTrace();
            }

            this.configLoader.save(rawConfig);
            this.logger.info("Config file successfully generated.");
        } catch (IOException exception) {
            this.logger.warn("The default configuration could not be created!");
        }
    }

}
