package com.porterlee.limstransfer;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class GeneralTest {

    @Test
    public void getBarcodeType_barcodeEqualTo_e1LAB00bWa_shouldReturn_Item() {
        assertEquals(DataManager.BarcodeType.getBarcodeType("e1LAB00bWa"), DataManager.BarcodeType.Item);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_m1LAB003Df_shouldReturn_Container() {
        assertEquals(DataManager.BarcodeType.getBarcodeType("m1LAB003Df"), DataManager.BarcodeType.Container);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_VAN__ADB______shouldReturn_Location() {
        assertEquals(DataManager.BarcodeType.getBarcodeType("VAN  ADB     "), DataManager.BarcodeType.Location);
    }

    @Test
    public void getBarcodeType_barcodeEqualTo_00000000_shouldReturn_Invalid() {
        assertEquals(DataManager.BarcodeType.getBarcodeType("00000000"), DataManager.BarcodeType.Invalid);
    }

    @Test
    public void csvContainsInt_test() {
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 1), true);
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 5), true);
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 17), true);
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 20), true);
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 100), false);
        assertEquals(TransferActivity.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 56874), false);
    }
}