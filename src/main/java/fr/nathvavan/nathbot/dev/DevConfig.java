package fr.nathvavan.nathbot.dev;

import fr.nathvavan.nathbot.BotConfig;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Tous les reglages de config/nathbot-client.toml, lisibles et modifiables en direct
 * (jeu ouvert). Chaque changement est enregistre dans le fichier tout de suite.
 */
public final class DevConfig {
    private DevConfig() {}

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> values;

    private static synchronized Map<String, ForgeConfigSpec.ConfigValue<?>> values() {
        if (values != null) return values;
        Map<String, ForgeConfigSpec.ConfigValue<?>> m = new LinkedHashMap<>();
        for (Field f : BotConfig.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !ForgeConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) continue;
            try {
                m.put(f.getName().toLowerCase(Locale.ROOT), (ForgeConfigSpec.ConfigValue<?>) f.get(null));
            } catch (Exception ignored) {}
        }
        values = m;
        return m;
    }

    public static Map<String, Object> all() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : values().entrySet()) {
            try {
                out.put(e.getKey(), e.getValue().get());
            } catch (Exception ex) {
                out.put(e.getKey(), "?");
            }
        }
        return out;
    }

    /** Change un reglage. Renvoie un message (ok ou erreur). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String set(String key, String raw) {
        ForgeConfigSpec.ConfigValue v = values().get(key.toLowerCase(Locale.ROOT));
        if (v == null) return "Reglage inconnu : " + key + ". Reglages : " + String.join(", ", values().keySet());
        try {
            Object cur = v.get();
            Object nv;
            String s = raw.trim();
            if (cur instanceof Boolean) nv = s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("oui");
            else if (cur instanceof Integer) nv = Integer.parseInt(s);
            else if (cur instanceof Long) nv = Long.parseLong(s);
            else if (cur instanceof Double) nv = Double.parseDouble(s);
            else if (cur instanceof List) {
                List<String> l = new ArrayList<>();
                for (String p : s.split(",")) if (!p.isBlank()) l.add(p.trim());
                nv = l;
            } else nv = s;
            v.set(nv);
            v.save();
            return key + " = " + nv + " (enregistre)";
        } catch (Exception e) {
            return "Valeur invalide pour " + key + " : " + raw;
        }
    }
}
