package me.reherhold.edifice;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
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
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import com.google.inject.Inject;

import jersey.repackaged.com.google.common.collect.ImmutableMap;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

@Plugin(id = PomData.ARTIFACT_ID, name = PomData.NAME, version = PomData.VERSION)
public class EdificeAuthPlugin {

	@Inject
	@DefaultConfig(sharedRoot = false)
	private File configFile;
	@Inject
	@DefaultConfig(sharedRoot = false)
	private ConfigurationLoader<CommentedConfigurationNode> configLoader;
	@Inject
	private Logger logger;
	private Configuration config;

	WebTarget target;

	@Listener
	public void preInit(GamePreInitializationEvent event) throws NoSuchAlgorithmException, KeyManagementException {
		setupConfig();

		// Jersey client setup
		SSLContext sslCxt = SSLContext.getInstance("TLSv1");
		System.setProperty("https.protocols", "TLSv1");

		TrustManager[] trustAllCerts = { new InsecureTrustManager() };
		sslCxt.init(null, trustAllCerts, new java.security.SecureRandom());
		HostnameVerifier allHostsValid = new InsecureHostnameVerifier();

		Client client = ClientBuilder.newBuilder().sslContext(sslCxt).hostnameVerifier(allHostsValid).build();

		target = client.target(config.getRestURI().toString() + "/auth/verificationcode");
	}

	@Listener
	public void playerLogin(ClientConnectionEvent.Login event) throws MalformedURLException, JSONException {
		GameProfile playerProfile = event.getProfile();
		JSONObject body = new JSONObject();
		body.put("playerId", playerProfile.getUniqueId().toString().replace("-", ""));

		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON_TYPE);
		invocationBuilder.header("Authorization", config.getSecretKey());

		Response response = invocationBuilder.post(Entity.entity(body.toString(), MediaType.APPLICATION_JSON));

		String rawBody = response.readEntity(String.class);
		JSONObject responseBody;
		try {
			responseBody = new JSONObject(rawBody);
		} catch (Exception e) {
			event.setMessage(Text.of("An unexpected error occured: " + rawBody));
			event.setCancelled(true);
			return;
		}

		switch (response.getStatus()) {
		case 201:
			event.setMessage(Text.of("Your verification code is ", TextColors.GOLD, responseBody.getString("code"),
					TextColors.WHITE, ". Go to ", TextColors.GREEN, config.getWebURI().toString() + "/signup",
					TextColors.WHITE, " to finish your registration."));
			break;
		case 400:
			// TODO This is pretty hacky, needs fixed
			if (responseBody.getString("message").equals("User already signed up.")) {
				event.setMessage(Text.of("You have already signed up for an account. You may log in ",
						Text.builder("here").color(TextColors.GOLD)
								.onClick(TextActions.openUrl(new URL(config.getWebURI().toString() + "/login")))));
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
	 * @return true if default config was successfully created, false if the
	 *         file was not created
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
