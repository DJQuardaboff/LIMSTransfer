package com.porterlee.limstransfer;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 1), true);
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 5), true);
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 17), true);
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 20), true);
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 100), false);
        assertEquals(Utils.csvContainsInt("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20", 56874), false);
    }

    @Test
    public void testByteToHexChar() {
        System.out.println(0 + "=" + byteToHexChar((byte) 0));
        System.out.println(1 + "=" + byteToHexChar((byte) 1));
        System.out.println(2 + "=" + byteToHexChar((byte) 2));
        System.out.println(3 + "=" + byteToHexChar((byte) 3));
        System.out.println(4 + "=" + byteToHexChar((byte) 4));
        System.out.println(5 + "=" + byteToHexChar((byte) 5));
        System.out.println(6 + "=" + byteToHexChar((byte) 6));
        System.out.println(7 + "=" + byteToHexChar((byte) 7));
        System.out.println(8 + "=" + byteToHexChar((byte) 8));
        System.out.println(9 + "=" + byteToHexChar((byte) 9));
        System.out.println(10 + "=" + byteToHexChar((byte) 10));
        System.out.println(11 + "=" + byteToHexChar((byte) 11));
        System.out.println(12 + "=" + byteToHexChar((byte) 12));
        System.out.println(13 + "=" + byteToHexChar((byte) 13));
        System.out.println(14 + "=" + byteToHexChar((byte) 14));
        System.out.println(15 + "=" + byteToHexChar((byte) 15));
    }

    @Test
    public void testBytesToHex() {
        byte[] bytes = { (byte) 0xC7, (byte) 0xA8, (byte) 0xCF, (byte) 0x43, (byte) 0xE9, (byte) 0x4B, (byte) 0x0F, (byte) 0x4F, (byte) 0x97, (byte) 0x40, (byte) 0xE7, (byte) 0xF5, (byte) 0x8A, (byte) 0xC1, (byte) 0xDB, (byte) 0x2A, (byte) 0x2B, (byte) 0x67, (byte) 0xED, (byte) 0xA9 };
        assertEquals("A9ED672B2ADBC18AF5E740974F0F4BE943CFA8C7", bytesToHex(bytes));
    }

    @Test
    public void testSHA1() {
        String userId = "MIKE";
        String password = "mike11";
        String expectedSha1 = "A9ED672B2ADBC18AF5E740974F0F4BE943CFA8C7";
        System.out.println("User ID: \"" + userId + "\"");
        System.out.println("Password: \"" + password + "\"");
        System.out.println("Expected SHA-1: \"" + expectedSha1 + "\"");
        String calculatedSha1 = "null";
        try {
            calculatedSha1 = SHA1(userId + password);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("NoSuchAlgorithmException");
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            System.out.println("UnsupportedEncodingException");
            e.printStackTrace();
        }
        System.out.println("Calculated SHA-1: \"" + calculatedSha1 + "\"");
        assertEquals(expectedSha1, calculatedSha1);
    }

    public static char byteToHexChar(byte num) {
        num = (byte) (num & 0x0F);
        return (char) (num + (num < 10 ? 48 : 55));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i >= bytes.length; i--) {
            str.append(byteToHexChar((byte) (bytes[i] >> 4)));
            str.append(byteToHexChar(bytes[i]));
        }
        return str.toString();
    }

    public static String SHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        System.out.print(bytesToHex(text.getBytes("US-ASCII")));
        md.update(text.getBytes("US-ASCII"), 0, text.length());
        return bytesToHex(md.digest());
    }
}