package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import java.util.concurrent.ThreadLocalRandom;
import tools.MaplePacketCreator;
import tools.data.input.SeekableLittleEndianAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeanGame {
    private static final Logger log = LoggerFactory.getLogger(BeanGame.class);

    public static final void BeanGame1(SeekableLittleEndianAccessor slea, MapleClient c) {
        MapleCharacter chr = c.getPlayer();
        if (chr == null) {
            return;
        }
        int type = slea.readByte();
        int 力度 = 0;
        int 豆豆序號 = 0;
        if (type == 1) {
            // 01 E8 03
            力度 = slea.readShort();
            chr.setBeansRange(力度);
        } else if (type == 2) {
            // type == 2 時，原條件 (type == 11 || type == 0) 恆為 false，此分支原本即不會執行
        } else if (type == 6) {
            {
                slea.skip(1);
                int 循環次數 = slea.readByte();
                if (循環次數 == 0) {
                    return;
                }
                if (循環次數 != 1) {
                    slea.skip((循環次數 - 1) * 8);
                }
                if (chr.isCanSetBeansNum()) {
                    chr.setBeansNum(chr.getBeansNum() + 循環次數);
                }
                chr.gainBeans(-循環次數);
                chr.setCanSetBeansNum(true);
            }
        } else {
            {
                log.debug("未處理的類型【{}】\n包{}", type, slea.toString());
            }
        }
        if (type == 6) {
            byte size = slea.readByte();
            short Pos = slea.readShort();
            byte Type = slea.readByte();
            c.sendPacket(MaplePacketCreator.showBeans(size, Pos, Type));
        }
    }

    private static int getBeanType() {
        int random = rand(1, 100);
        int beanType = 0;
        switch (random) {
            case 2:
                beanType = 1;
                break;
            case 49:
                beanType = 2;
                break;
            case 99:
                beanType = 3;
                break;
        }
        return beanType;
    }

    private static int rand(int lbound, int ubound) {
        return (int) ((ThreadLocalRandom.current().nextDouble() * (ubound - lbound + 1)) + lbound);
    }

    public static final void BeanGame2(SeekableLittleEndianAccessor slea, MapleClient c) {
        MapleCharacter chr = c.getPlayer();
        if (chr == null) {
            return;
        }
        c.sendPacket(MaplePacketCreator.updateBeans(chr.getId(), chr.getBeans()));
        c.sendPacket(MaplePacketCreator.enableActions());
    }
}
