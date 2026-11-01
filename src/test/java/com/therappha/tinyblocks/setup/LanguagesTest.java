package com.therappha.tinyblocks.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every language holds every text of en_us, with as many values to fill in, so none falls back to English. */
class LanguagesTest {

    private static JsonObject language(String name) throws IOException {
        var stream = LanguagesTest.class.getResourceAsStream("/assets/tinyblocks/lang/" + name + ".json");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static long placeholders(String text) {
        return text.split("%s", -1).length - 1;
    }

    @ParameterizedTest
    @ValueSource(strings = {"pt_br", "zh_cn", "zh_tw"})
    void holdsEveryText(String name) throws IOException {
        JsonObject english = language("en_us"), other = language(name);
        assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(other.keySet()));
        for (Map.Entry<String, com.google.gson.JsonElement> text : english.entrySet()) {
            assertEquals(placeholders(text.getValue().getAsString()), placeholders(other.get(text.getKey()).getAsString()), text.getKey());
        }
    }
}
