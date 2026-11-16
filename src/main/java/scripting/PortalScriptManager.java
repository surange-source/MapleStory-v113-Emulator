package scripting;

import client.MapleClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import server.MaplePortal;
import tools.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PortalScriptManager {
    private static final Logger LOG = LoggerFactory.getLogger(PortalScriptManager.class);

    private static final class SingletonHolder {
        private static final PortalScriptManager INSTANCE = new PortalScriptManager();
    }

    private final Map<String, PortalScript> scripts = new HashMap<>();
    private static final NashornScriptEngineFactory sef = new NashornScriptEngineFactory();

    private PortalScriptManager() {}

    public static PortalScriptManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private final PortalScript getPortalScript(final String scriptName) {
        if (scripts.containsKey(scriptName)) {
            return scripts.get(scriptName);
        }

        String scriptPath = "Libs/scripts/portal/" + scriptName + ".js";
        final File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            scripts.put(scriptName, null);
            return null;
        }

        final ScriptEngine portal = sef.getScriptEngine();
        try (InputStreamReader fr =
                new InputStreamReader(
                        new FileInputStream(scriptFile), StringUtil.codeString(scriptFile))) {
            CompiledScript compiled = ((Compilable) portal).compile(fr);
            compiled.eval();
        } catch (final FileNotFoundException | UnsupportedEncodingException | ScriptException e) {
            LOG.error("傳送門腳本執行錯誤: {}:", scriptName, e);
        } catch (IOException ex) {
            System.getLogger(PortalScriptManager.class.getName())
                    .log(System.Logger.Level.ERROR, (String) null, ex);
        }
        final PortalScript script = ((Invocable) portal).getInterface(PortalScript.class);
        scripts.put(scriptName, script);
        return script;
    }

    public final void executePortalScript(final MaplePortal portal, final MapleClient c) {
        final PortalScript script = getPortalScript(portal.getScriptName());

        if (script != null) {
            try {
                script.enter(new PortalPlayerInteraction(c, portal));
            } catch (Exception e) {
                LOG.error("進入傳送腳本失敗: {}:", portal.getScriptName(), e);
            }
        } else {
            LOG.warn("未處理的傳送腳本 {} 所在地圖 {}", portal.getScriptName(), c.getPlayer().getMapId());
        }
        clearScripts();
    }

    public final void clearScripts() {
        scripts.clear();
    }
}
