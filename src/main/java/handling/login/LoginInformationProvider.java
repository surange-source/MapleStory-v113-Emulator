/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package handling.login;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import provider.MapleData;
import provider.MapleDataProviderFactory;
import provider.MapleDataTool;
import server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginInformationProvider {
    private static final Logger log = LoggerFactory.getLogger(LoginInformationProvider.class);

    private final List<String> ForbiddenName = new ArrayList<>();

    private static final class Holder {
        private static final LoginInformationProvider INSTANCE = new LoginInformationProvider();
    }

    public static LoginInformationProvider getInstance() {
        return Holder.INSTANCE;
    }

    private LoginInformationProvider() {
        log.info("Loading LoginInformationProvider :::");
        final String WZpath = ServerProperties.getProperty("tms.Wzpath");
        final MapleData nameData =
                MapleDataProviderFactory.getDataProvider(new File(WZpath + "/Etc.wz"))
                        .getData("ForbiddenName.img");
        for (final MapleData data : nameData.getChildren()) {
            ForbiddenName.add(MapleDataTool.getString(data));
        }
        log.info(" Loaded");
    }

    public final boolean isForbiddenName(final String in) {
        for (final String name : ForbiddenName) {
            if (in.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
