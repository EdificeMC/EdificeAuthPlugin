package me.reherhold.edifice;

import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;

import java.net.URI;

/**
 * Class that contains all the configurable options for the plugin
 */
public class Configuration {

    public static final ObjectMapper<Configuration> MAPPER;

    static {
        try {
            MAPPER = ObjectMapper.forClass(Configuration.class);
        } catch (ObjectMappingException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /********************
     * General Settings *
     ********************/

    @Setting(value = "rest-uri", comment = "REST API URI") private URI restURI = URI.create("https://api.edificemc.com");
    @Setting(value = "web-uri", comment = "Website URI") private URI webURI = URI.create("https://www.edificemc.com/#");
    @Setting(value = "secret-key", comment = "Secret key for issuing verification codes") private String secretkey = "secret";

    public URI getRestURI() {
        return this.restURI;
    }

    public URI getWebURI() {
        return this.webURI;
    }
    
    public String getSecretKey() {
    	return this.secretkey;
    }

}
