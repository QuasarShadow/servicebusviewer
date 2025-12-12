        package com.dutils.servicebusviewer.codearea;

        import com.fasterxml.jackson.databind.ObjectMapper;
        import org.fxmisc.richtext.model.StyleSpans;
        import org.fxmisc.richtext.model.StyleSpansBuilder;

        import java.util.Collection;
        import java.util.Collections;
        import java.util.regex.Matcher;
        import java.util.regex.Pattern;

public class JsonEditor {
    private static final Pattern JSON_STRUCT = Pattern.compile(
            "(?<STRING>\"(?:\\\\.|[^\"])*\")"
                    + "|(?<NUMBER>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)"
                    + "|(?<BOOLEAN>true|false|TRUE|FALSE)"
                    + "|(?<NULL>null)"
                    + "|(?<BRACE>[{}])"
                    + "|(?<BRACKET>[\\[\\]])"
                    + "|(?<COLON>:)"
                    + "|(?<COMMA>,)"
    );
    private static final Pattern JSON_PATTERN = Pattern.compile("^\\s*(\\{|\\[)");
    public static boolean isValid(String text){
        return JSON_PATTERN.matcher(text).find();
    }
    public static StyleSpans<Collection<String>> highlight(String text) {
        Matcher matcher = JSON_STRUCT.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            String styleClass = switch (findMatchedGroup(matcher)) {
                case "STRING" -> isFieldName(text, matcher.end()) ? "json-property" : "json-string";
                case "NUMBER" -> "json-number";
                case "BOOLEAN" -> "json-bool";
                case "NULL" -> "json-null";
                case "BRACE" -> "json-brace";
                case "BRACKET" -> "json-bracket";
                case "COLON", "COMMA" -> "json-seperator";
                default -> null;
            };
            if (styleClass != null) {
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            }
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static String findMatchedGroup(Matcher matcher) {
        for (String name : matcher.pattern().namedGroups().keySet()) {
            if (matcher.group(name) != null) {
                return name;
            }
        }
        return "";
    }

    private static boolean isFieldName(String text, int stringEnd) {
        int i = stringEnd;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == ':';
            }
            i++;
        }
        return false;
    }

    public static String format(String jsonInput) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Object jsonObject = objectMapper.readValue(jsonInput, Object.class);
            String formated = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            return formated;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error: Invalid JSON input - " + e.getMessage());
        }
        return jsonInput;
    }
}
