package com.dutils.servicebusviewer.codearea;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JsonEditorJackson {
    private final JsonFactory jsonFactory = new JsonFactory();

    public record Match(String kind, long start, long end) implements Comparable<Match> {
        @Override
        public int compareTo(Match match) {
            return Long.compare(start, match.start);
        }

        @Override
        public String toString() {
            return String.format("%s[%d,%d]", kind, start, end);
        }
    }

    public static String styleClassName(JsonToken jsonToken) {
        if (jsonToken == null) {
            return "";
        }
        return switch (jsonToken) {
            case FIELD_NAME -> "json-property";
            case VALUE_STRING -> "json-string";
            case START_OBJECT -> "json-start-object";
            case END_OBJECT -> "json-end-object";
            case VALUE_NUMBER_FLOAT -> "json-float";
            case VALUE_NUMBER_INT -> "json-int";
            case VALUE_TRUE -> "json-true";
            case VALUE_FALSE -> "json-false";
            case START_ARRAY -> "json-start-array";
            case END_ARRAY -> "json-end-array";
            case VALUE_EMBEDDED_OBJECT -> "json-embedded";
            case VALUE_NULL -> "json-null";
            default -> "";
        };
    }

    public StyleSpans<Collection<String>> highlight(String json) {

        try {
            JsonParser parser = jsonFactory.createParser(json);
            List<Match> matches = new ArrayList<>(64);

            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                String style = styleClassName(token);
                if (style.isEmpty()) continue;

                long start = parser.getTokenLocation().getCharOffset();
                long end = start + parser.getTextLength();

                if (token == JsonToken.VALUE_STRING || token == JsonToken.FIELD_NAME) {
                    end += 2;
                }
                matches.add(new Match(style, start, end));
            }
            StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
            int last = 0;
            for (Match m : matches) {
                int s = (int) m.start();
                int e = (int) m.end();
                if (s > last) {
                    spans.add(Collections.emptyList(), s - last);
                }
                spans.add(Collections.singleton(m.kind()), e - s);
                last = e;
            }
            if (last < json.length()) {
                spans.add(Collections.emptyList(), json.length() - last);
            }
            return spans.create();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}