package dev.scriptautoce.script;

import java.util.List;

public interface ScriptHost {
    Position position();

    String blockAt(String x, String y, String z);

    List<String> players();

    List<String> entities(String type, double radius);

    long score(String objective, String target);

    record Position(double x, double y, double z) {
    }
}
