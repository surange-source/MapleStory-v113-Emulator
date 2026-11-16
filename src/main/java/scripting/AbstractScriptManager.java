package scripting;

import client.MapleClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.StringUtil;

/**
 * @author Matze
 */
public abstract class AbstractScriptManager {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractScriptManager.class);
    private static final NashornScriptEngineFactory sem = new NashornScriptEngineFactory();

    protected Invocable getInvocable(String path, MapleClient c) {
        return getInvocable(path, c, false);
    }

    protected Invocable getInvocable(String path, MapleClient c, boolean npc) {
        path = "Libs/scripts/" + path;
        ScriptEngine engine = null;
        if (c != null) {
            engine = c.getScriptEngine(path);
        }
        if (engine == null) {
            File scriptFile = new File(path);
            if (!scriptFile.exists()) {
                return null;
            }
            engine = sem.getScriptEngine();

            try (InputStreamReader fr =
                    new InputStreamReader(
                            new FileInputStream(scriptFile), StringUtil.codeString(scriptFile))) {
                engine.eval(fr);
            } catch (ScriptException | IOException e) {
                LOG.error("Error executing script. Path: " + path + "\nException " , e);
                return null;
            }

            // 僅在腳本成功載入後才快取 engine，避免半初始化狀態被後續呼叫重用
            if (c != null) {
                c.setScriptEngine(path, engine);
            }
        } else if (c != null && npc) {
            if (c.getPlayer() != null) {
                c.getPlayer().dropMessage(5, "角色狀態異常，請使用@解卡/@ea 來解除異常狀態");
            }
        }
        return (Invocable) engine;
    }
}
