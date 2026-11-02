package com.frimer.launcher.core;

import java.util.List;
import java.util.Map;

/** Правила совместимости библиотек и аргументов (поле "rules" в JSON версии). */
public final class Rules {

    private Rules() {
    }

    /** Разрешено ли правило на текущей ОС. Правила с "features" не применяются. */
    public static boolean allowOs(List<Object> rules) {
        String os = Dirs.osName();
        Boolean allowed = null;
        for (Object o : rules) {
            Map<String, Object> rule = Json.obj(o);
            boolean match = true;
            if (rule.get("features") != null) {
                Object features = rule.get("features");
                if (features instanceof Map<?, ?> map && !map.isEmpty()) {
                    match = false;
                }
            }
            Map<String, Object> ruleOs = Json.obj(rule.get("os"));
            String name = Json.str(ruleOs.get("name"), "");
            if (!name.isEmpty() && !name.equals(os)) {
                match = false;
            }
            if (match) {
                allowed = "allow".equals(Json.str(rule.get("action"), ""));
            }
        }
        return Boolean.TRUE.equals(allowed);
    }

    public static boolean allowed(Map<String, Object> entry) {
        Object rules = entry.get("rules");
        if (rules instanceof List<?> list && !list.isEmpty()) {
            return allowOs(Json.arr(rules));
        }
        return true;
    }
}
