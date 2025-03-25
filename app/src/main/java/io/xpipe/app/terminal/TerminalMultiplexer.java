package io.xpipe.app.terminal;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.xpipe.core.process.ShellScript;
import io.xpipe.core.util.ValidationException;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface TerminalMultiplexer {

    static List<Class<?>> getClasses() {
        var l = new ArrayList<Class<?>>();
        l.add(ZellijTerminalMultiplexer.class);
        return l;
    }

    default void checkComplete() throws ValidationException {}

    String getDocsLink();

    ShellScript launchScriptExternal(String command) throws Exception;

    ShellScript launchScriptSession(String command) throws Exception;
}
