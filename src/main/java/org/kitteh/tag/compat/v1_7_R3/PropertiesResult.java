package org.kitteh.tag.compat.v1_7_R3;

import com.google.common.base.Charsets;
import net.minecraft.util.com.google.gson.Gson;
import net.minecraft.util.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

public class PropertiesResult {

    private static HashMap<String, Properties> savedProperties = new HashMap<String, Properties>();

    private String id;
    private String name;
    public List<Properties> properties;

    public static class Properties {
        public String name;
        public String value;
        public String signature;
    }

    private static String skullbloburl = "https://sessionserver.mojang.com/session/minecraft/profile/";
    public static Properties getProperties(String id, boolean force) {
        if(!savedProperties.containsKey(id) ||force){
            try {
                URL url = new URL(skullbloburl+id);
                InputStream is = url.openStream();
                String result = IOUtils.toString(is, Charsets.UTF_8);
                Gson gson = new Gson();
                PropertiesResult propr = gson.fromJson(result, PropertiesResult.class);
                if (!propr.properties.isEmpty()) {
                    savedProperties.put(id, propr.properties.get(0));
                    return propr.properties.get(0);
                }
            } catch (IOException e) {
            }
        }
        return savedProperties.get(id);
    }

}