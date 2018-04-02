package com.porterlee.limstransfer;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class BarcodeTypeTest {

    @Test
    public void getBarcodeType_barcodeEqualTo_e1LAB00bWa_shouldReturn_Item() {
        assertEquals(BarcodeType.getBarcodeType("e1LAB00bWa"), BarcodeType.Item);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_m1LAB003Df_shouldReturn_Container() {
        assertEquals(BarcodeType.getBarcodeType("m1LAB003Df"), BarcodeType.Container);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_VAN__ADB______shouldReturn_Location() {
        assertEquals(BarcodeType.getBarcodeType("VAN  ADB     "), BarcodeType.Location);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_00000000_shouldReturn_Invalid() {
        assertEquals(BarcodeType.getBarcodeType("00000000"), BarcodeType.Invalid);
    }
}