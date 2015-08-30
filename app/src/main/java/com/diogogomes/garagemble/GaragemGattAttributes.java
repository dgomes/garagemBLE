package com.diogogomes.garagemble;

import java.util.HashMap;

/**
 * Created by dgomes on 27/08/15.
 */
public class GaragemGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    /*
    Services extracted from
    https://developer.bluetooth.org/gatt/services/Pages/ServicesHome.aspx
    */
    public static String DEVICE_INFORMATION = "0000180a-0000-1000-8000-00805f9b34fb";

    /*
        Characteristics extracted from:
        https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicsHome.aspx
     */
    public static String MODEL_NUMBER_STRING = "00002a24-0000-1000-8000-00805f9b34fb";
    public static String SERIAL_NUMBER_STRING = "00002a25-0000-1000-8000-00805f9b34fb";
    public static String FIRMWARE_REVISION_STRING = "00002a26-0000-1000-8000-00805f9b34fb";
    public static String HARDWARE_REVISION_STRING = "00002a27-0000-1000-8000-00805f9b34fb";
    public static String SOFTWARE_REVISION_STRING = "00002a28-0000-1000-8000-00805f9b34fb";
    public static String MANUFACTURER_NAME_STRING = "00002a29-0000-1000-8000-00805f9b34fb";

    // Garagem

    public static String GARAGEM_SERVICE = "00002000-0000-1000-8000-00805f9b34fb";
    public static String GARAGEM_CHALLENGE_CHARACTERISTIC_UUID = "00002001-0000-1000-8000-00805f9b34fb";
    public static String GARAGEM_LAST_OPEN_TS_UUID = "00002002-0000-1000-8000-00805f9b34fb";
    public static String GARAGEM_LAST_OPEN_ID_UUID = "00002003-0000-1000-8000-00805f9b34fb";

    public static String SECURITY_SERVICE = "00003000-0000-1000-8000-00805f9b34fb";
    public static String SECURITY_IV_CHARACTERISTIC_UUID = "00003001-0000-1000-8000-00805f9b34fb";
    public static String SECURITY_KEY_CHARACTERISTIC_UUID = "00003002-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put(DEVICE_INFORMATION, "Device Information");
        attributes.put(MODEL_NUMBER_STRING, "Model Number");
        attributes.put(SERIAL_NUMBER_STRING, "Serial Number");
        attributes.put(FIRMWARE_REVISION_STRING, "Firmware Revision");
        attributes.put(HARDWARE_REVISION_STRING, "Hardware Revision");
        attributes.put(SOFTWARE_REVISION_STRING, "Software Revision");
        attributes.put(MANUFACTURER_NAME_STRING, "Manufacturer Name");

        attributes.put(GARAGEM_SERVICE, "Garagem Service");
        attributes.put(GARAGEM_CHALLENGE_CHARACTERISTIC_UUID, "Challenge");
        attributes.put(GARAGEM_LAST_OPEN_TS_UUID, "Last Opened Timestamp");
        attributes.put(GARAGEM_LAST_OPEN_ID_UUID, "Last Opened Identifier");
        attributes.put(SECURITY_SERVICE, "Security Service");
        attributes.put(SECURITY_IV_CHARACTERISTIC_UUID, "Initial Vector");
        attributes.put(SECURITY_KEY_CHARACTERISTIC_UUID, "Shared Key");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
