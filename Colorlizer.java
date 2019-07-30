/*
 * Copyright 2019 azure (https://github.com/stilllogic20)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.CharBuffer;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

public final class Colorlizer {

    private static final String MC_LOG_FORMAT = "[{TIMESTAMP}] [{THREAD}/{LEVEL}]: {MESSAGE}";

    private static final char ESC = '\u001B';

    private static final String ANSI_RESET = ESC + "[0m";
    private static final String ANSI_GREEN = ESC + "[32m";
    private static final String ANSI_WHITE = ESC + "[37m";

    private static final String TZNAME;
    static {
        final ZoneId zone = ZoneId.systemDefault();
        final String id = zone.getId();
        String tz = id;
        if (zone.normalized().equals(ZoneOffset.UTC)) {
            tz = "UTC";
        } else {
            for (Map.Entry<String, String> e : ZoneId.SHORT_IDS.entrySet()) {
                String sn = e.getKey();
                String ln = e.getValue();
                if (sn.length() < tz.length() && Objects.equals(ln, id)) {
                    tz = e.getKey();
                }
            }
        }
        TZNAME = tz;
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        synchronized (out) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                for (;;) {
                    String line = reader.readLine();
                    if (line == null) {
                        // EOF
                        return;
                    }
                    out.println(colorlize(line));
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    private static String colorlize(String line) {
        String[] elements = parse(line);
        int i = 0;
        return MC_LOG_FORMAT //
                .replace("{TIMESTAMP}", timestamp(elements[i++])) //
                .replace("{THREAD}", thread(elements[i++])) //
                .replace("{LEVEL}", level(elements[i++])) //
                .replace("{MESSAGE}", message(elements[i++])); //
    }

    private static String[] parse(String line) {
        CharBuffer ptr = CharBuffer.wrap(line);
        Context[] contexts = Contexts.values();
        int size = contexts.length;
        String[] elements = new String[size];
        for (int i = 0; i < size; ++i) {
            elements[i] = contexts[i].parse(ptr);
        }
        return elements;
    }

    private static String timestamp(String s) {
        return ANSI_GREEN + s + " (" + TZNAME + ")" + ANSI_RESET;
    }

    private static String thread(String s) {
        return ESC + "[38;5;12m" + s + ANSI_RESET;
    }

    private static String level(String s) {
        switch (s) {
        case "ERROR":
            return ESC + "[38;5;09m" + s + ANSI_RESET;
        case "WARN":
            return ESC + "[38;5;11m" + s + ANSI_RESET;
        case "INFO":
            return ESC + "[38;5;14m" + s + ANSI_RESET;
        }
        throw new IllegalArgumentException("Level '" + s + "' is invalid.");
    }

    private static String message(String s) {
        return ANSI_WHITE + s + ANSI_RESET;
    }

    private interface Context {

        String parse(CharBuffer ptr);

    }

    private static void test(CharBuffer ptr, char expected) {
        char c = ptr.get();
        if (c != expected)
            throw new IllegalArgumentException(String.format(//
                    "Illegal char '%c' at %d", c, ptr.position() - 1));
    }

    private enum Contexts implements Context {
        TIMESTAMP {
            @Override
            public String parse(CharBuffer ptr) {
                test(ptr, '[');
                int begin = ptr.position();
                int end;
                for (;;) {
                    char c = ptr.get();
                    if (c == ']') {
                        end = ptr.position() - 1;
                        break;
                    }
                }
                return ptr.duplicate().position(begin).limit(end).toString();
            }
        },
        THREAD {
            @Override
            public String parse(CharBuffer ptr) {
                test(ptr, ' ');
                test(ptr, '[');
                int delimiterIndex = -1;
                {
                    int i = ptr.position();
                    for (;;) {
                        if (ptr.get(i) == ']') {
                            break;
                        }
                        ++i;
                    }
                    for (;;) {
                        if (ptr.get(i) == '/') {
                            delimiterIndex = i;
                            break;
                        }
                        --i;
                    }
                }

                String r = ptr.duplicate().limit(delimiterIndex).toString();
                ptr.position(delimiterIndex);
                return r;
            }
        },
        LEVEL {
            @Override
            public String parse(CharBuffer ptr) {
                test(ptr, '/');
                int begin = ptr.position();
                int end;
                for (;;) {
                    char c = ptr.get();
                    if (c == ']') {
                        end = ptr.position() - 1;
                        break;
                    }
                }
                return ptr.duplicate().position(begin).limit(end).toString();
            }
        },
        BODY {
            @Override
            public String parse(CharBuffer ptr) {
                test(ptr, ':');
                test(ptr, ' ');
                return ptr.toString();
            }
        }
    }

}
