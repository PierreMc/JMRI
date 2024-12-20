package jmri.jmrix.lenz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the standard/common routines used in multiple classes related to the
 * a Lenz Command Station, on an XpressNet network.
 *
 * @author Bob Jacobsen Copyright (C) 2001 Portions by Paul Bender Copyright (C) 2003
 */
public class LenzCommandStation implements jmri.CommandStation {

    /* The First group of routines is for obtaining the Software and
     hardware version of the Command station */

    /**
     * We need to add a few data members for saving the version information we
     * get from the layout.
     */
    private int cmdStationType = -1;
    private float cmdStationSoftwareVersion = -1;
    private int cmdStationSoftwareVersionBCD = -1;

    /**
     * Return the CS Type.
     * @return CS type.
     */
    public int getCommandStationType() {
        return cmdStationType;
    }

    /**
     * Set the CS Type.
     * @param t CS type.
     */
    public void setCommandStationType(int t) {
        cmdStationType = t;
    }

    /**
     * Set the CS Type based on an XpressNet Message.
     * @param l XNetReply containing the CS type.
     */
    public void setCommandStationType(XNetReply l) {
        if (l.getElement(0) == XNetConstants.CS_SERVICE_MODE_RESPONSE) {
            // This is the Command Station Software Version Response
            if (l.getElement(1) == XNetConstants.CS_SOFTWARE_VERSION) {
                cmdStationType = l.getElement(3);
            }
        }
    }

    /**
     * Get the CS Software Version.
     * @return software version.
     */
    public float getCommandStationSoftwareVersion() {
        return cmdStationSoftwareVersion;
    }

    /**
     * Get the CS Software Version in BCD (for use in comparisons).
     * @return software version.
     */
    public float getCommandStationSoftwareVersionBCD() {
        return cmdStationSoftwareVersionBCD;
    }

    /**
     * Set the CS Software Version.
     * @param v software version.
     */
    public void setCommandStationSoftwareVersion(float v) {
        cmdStationSoftwareVersion = v;
    }

    /**
     * Set the CS Software Version based on an XpressNet Message.
     * @param l reply containing CS version.
     */
    public void setCommandStationSoftwareVersion(XNetReply l) {
        if (l.getElement(0) == XNetConstants.CS_SERVICE_MODE_RESPONSE) {
            // This is the Command Station Software Version Response
            if (l.getElement(1) == XNetConstants.CS_SOFTWARE_VERSION) {
                try {
                    cmdStationSoftwareVersion = (l.getElementBCD(2).floatValue()) / 10;
                } catch (java.lang.NumberFormatException nfe) {
                    // the number was not in BCD format as expected.
                    // the upper nibble is the major version and the lower 
                    // nibble is the minor version.
                    cmdStationSoftwareVersion = ((l.getElement(2) & 0xf0) >> 4) + (l.getElement(2) & 0x0f) / 100.0f;
                }
                cmdStationSoftwareVersionBCD = l.getElement(2);
            }
        }
    }

    /**
     * Provide the version string returned during the initial check.
     * @return human readable version string.
     */
    public String getVersionString() {
        return Bundle.getMessage("CSVersionString", getCommandStationType(),getCommandStationSoftwareVersionBCD());
    }

    /**
     * XpressNet command station does provide Ops Mode.
     *
     * @return true if CS type 1 or 2, else false.
     */
    public boolean isOpsModePossible() {
        return cmdStationType != 0x01 && cmdStationType != 0x02;
    }

    // A few utility functions

    /**
     * Get the Lower byte of a locomotive address from the decimal locomotive
     * address.
     * @param address loco address.
     * @return low address byte including DCC offset.
     */
    public static int getDCCAddressLow(int address) {
        /* For addresses below 100, we just return the address, otherwise,
         we need to return the upper byte of the address after we add the
         offset 0xC000. The first address used for addresses over 99 is 0xC064*/
        if (address < 100) {
            return (address);
        } else {
            int temp = address + 0xC000;
            temp = temp & 0x00FF;
            return temp;
        }
    }

    /**
     * Get the Upper byte of a locomotive address from the decimal locomotive
     * address.
     * @param address loco address.
     * @return upper byte after DCC offset.
     */
    public static int getDCCAddressHigh(int address) {
        /* this isn't actually the high byte, For addresses below 100, we
         just return 0, otherwise, we need to return the upper byte of the
         address after we add the offset 0xC000 The first address used for
         addresses over 99 is 0xC064*/
        if (address < 100) {
            return (0x00);
        } else {
            int temp = address + 0xC000;
            temp = temp & 0xFF00;
            temp = temp / 256;
            return temp;
        }
    }

    /**
     * We need to calculate the locomotive address when doing the translations
     * back to text. XpressNet Messages will have these as two elements, which
     * need to get translated back into a single address by reversing the
     * formulas used to calculate them in the first place.
     *
     * @param AH the high order byte of the address
     * @param AL the low order byte of the address
     * @return the address as an integer.
     */
    static public int calcLocoAddress(int AH, int AL) {
        if (AH == 0x00) {
            /* if AH is 0, this is a short address */
            return (AL);
        } 
        
        if ((AH & 0xC0) == 0x80) {
            /* this is accessory address */
            if ((AL & 0x80) == 0x80) {
                /* this is Basic accessory decoder */
                int address;
                
                int part1 ;
                part1 = (AH & 0x3F) ;
                
                int part2 ;
                part2 = ~AL ;
                part2 = (part2 & 0x70) ;
                part2 = part2 << 2 ;
                
                address = part2 | part1 ;
                return (address) ;
            } else {
                /* this is Extended accessory decoder */
                int address;
                
                int part1 ;
                part1 = (AH & 0x3F) ;
                // part1 = part1 + 1 ;
                part1 = part1 << 2 ;
                
                int part2 ;
                part2 = ~AL ;
                part2 = (part2 & 0x70) ;
                part2 = part2 << 4 ;
                
                int part3 ;
                part3 = AL ;
                part3 = (part3 & 0x06) ;
                part3 = part3 >> 1 ;
                
                address = part2 | part1 | part3;
                address = address - 3;
                
                return (address) ;
            }
        }
        
        /* This must be a long locomotive address */
        int address;
        address = ((AH * 256) & 0xFF00);
        address += (AL & 0xFF);
        address -= 0xC000;
        return (address);
    }

    /* To Implement the CommandStation Interface, we have to define the 
     sendPacket function */

    /**
     * Send a specific packet to the rails.
     *
     * @param packet  Byte array representing the packet, including the
     *                error-correction byte. Must not be null.
     * @param repeats Number of times to repeat the transmission.
     */
    @Override
    public boolean sendPacket(byte[] packet, int repeats) {

        if (_tc == null) {
            log.error("Send Packet Called without setting traffic controller");
            return false;
        }

        XNetMessage msg = XNetMessage.getNMRAXNetMsg(packet);
        for (int i = 0; i < repeats; i++) {
            _tc.sendXNetMessage(msg, null);
        }
        return true;
    }

    /*
     * For the command station interface, we need to set the traffic 
     * controller.
     */
    public void setTrafficController(XNetTrafficController tc) {
        _tc = tc;
    }

    private XNetTrafficController _tc = null;

    public void setSystemConnectionMemo(XNetSystemConnectionMemo memo) {
        adaptermemo = memo;
    }

    XNetSystemConnectionMemo adaptermemo;

    @Override
    public String getUserName() {
        if (adaptermemo == null) {
            return Bundle.getMessage("MenuXpressNet");
        }
        return adaptermemo.getUserName();
    }

    @Override
    public String getSystemPrefix() {
        if (adaptermemo == null) {
            return "X";
        }
        return adaptermemo.getSystemPrefix();
    }

    /*
     * Register for logging.
     */
    private static final Logger log = LoggerFactory.getLogger(LenzCommandStation.class);

}
