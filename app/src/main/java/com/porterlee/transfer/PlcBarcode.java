package com.porterlee.transfer;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class PlcBarcode {
    private static final int CUSTODY_OF_LENGTH = 4;
    private static final int LAB_CODE_LENGTH = 3;


    private class ItemType_t {}

    private static ItemType_t ItemType;


    private class ContainerType_t {}

    private static ContainerType_t ContainerType;


    private class LocationType_t {}

    private static LocationType_t LocationType;


    public enum Encoding {
        Base32,
        Base64
    }

    public enum BarcodeType {
        Item(
                ItemType,
                BuildConfig.barcodeType_item_hasLabCode,
                BuildConfig.barcodeType_item_base32Prefix,
                BuildConfig.barcodeType_item_base64Prefix,
                BuildConfig.barcodeType_item_genericPrefixes
        ),
        Container(
                ContainerType,
                BuildConfig.barcodeType_container_hasLabCode,
                BuildConfig.barcodeType_container_base32Prefix,
                BuildConfig.barcodeType_container_base64Prefix,
                BuildConfig.barcodeType_container_genericPrefixes
        ),
        Location(
                LocationType,
                BuildConfig.barcodeType_location_hasCustodyOf,
                BuildConfig.barcodeType_location_base32Prefix,
                BuildConfig.barcodeType_location_base64Prefix,
                BuildConfig.barcodeType_location_genericPrefixes
        );

        private final String nameStr;
        public final boolean hasEcn;
        public final boolean hasLocationName;
        public final boolean hasCustodyOf;
        public final boolean hasLabCode;
        public final String base32Prefix;
        public final String base64Prefix;
        public final String[] genericPrefixes;
        public final List<Pair<String, Encoding>> prefixEncodings = new ArrayList<>();

        BarcodeType(ItemType_t unused, boolean hasLabCode, String base32Prefix, String base64Prefix, String... genericPrefixes) {
            this.nameStr = "Item";
            this.hasEcn = true;
            this.hasLocationName = false;
            this.hasCustodyOf = false;
            this.hasLabCode = hasLabCode;
            this.base32Prefix = base32Prefix;
            this.base64Prefix = base64Prefix;
            if (genericPrefixes == null || (genericPrefixes.length == 1 && genericPrefixes[0] == null)) {
                this.genericPrefixes = null;
            } else {
                this.genericPrefixes = genericPrefixes;
            }
            if (base32Prefix != null && !base32Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base32Prefix, Encoding.Base32));
            if (base64Prefix != null && !base64Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base64Prefix, Encoding.Base64));
            if (genericPrefixes != null) {
                for (String prefix : genericPrefixes) {
                    if (prefix != null && !prefix.isEmpty()) prefixEncodings.add(new Pair<String, Encoding>(prefix, null));
                }
            }
        }

        BarcodeType(ContainerType_t unused, boolean hasLabCode, String base32Prefix, String base64Prefix, String... genericPrefixes) {
            this.nameStr = "Container";
            this.hasEcn = true;
            this.hasLocationName = false;
            this.hasCustodyOf = false;
            this.hasLabCode = hasLabCode;
            this.base32Prefix = base32Prefix;
            this.base64Prefix = base64Prefix;
            if (genericPrefixes == null || (genericPrefixes.length == 1 && genericPrefixes[0] == null)) {
                this.genericPrefixes = null;
            } else {
                this.genericPrefixes = genericPrefixes;
            }
            if (base32Prefix != null && !base32Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base32Prefix, Encoding.Base32));
            if (base64Prefix != null && !base64Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base64Prefix, Encoding.Base64));
            if (genericPrefixes != null) {
                for (String prefix : genericPrefixes) {
                    if (prefix != null && !prefix.isEmpty()) prefixEncodings.add(new Pair<String, Encoding>(prefix, null));
                }
            }
        }

        BarcodeType(LocationType_t unused, boolean hasCustodyOf, String base32Prefix, String base64Prefix, String... genericPrefixes) {
            this.nameStr = "Location";
            this.hasEcn = false;
            this.hasLocationName = true;
            this.hasCustodyOf = hasCustodyOf;
            this.hasLabCode = false;
            this.base32Prefix = base32Prefix;
            this.base64Prefix = base64Prefix;
            if (genericPrefixes == null || (genericPrefixes.length == 1 && genericPrefixes[0] == null)) {
                this.genericPrefixes = null;
            } else {
                this.genericPrefixes = genericPrefixes;
            }
            if (base32Prefix != null && !base32Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base32Prefix, Encoding.Base32));
            if (base64Prefix != null && !base64Prefix.isEmpty()) prefixEncodings.add(new Pair<>(base64Prefix, Encoding.Base64));
            if (genericPrefixes != null) {
                for (String prefix : genericPrefixes) {
                    if (prefix != null && !prefix.isEmpty()) prefixEncodings.add(new Pair<String, Encoding>(prefix, null));
                }
            }
        }

        @Override
        @NonNull
        public String toString() {
            return nameStr;
        }
    }

    private final boolean mIsEcnValid;
    private final Encoding mEcnEncoding;
    private final BarcodeType mBarcodeType;
    private final long mDecodedEcn;
    private final String mBarcode;
    private final String mPrefix;
    private final String mLabCode_or_custodyOf;
    private final String mEcn_or_locationName;

    public boolean isEcnValid() {
        return mIsEcnValid;
    }

    public Encoding getEcnEncoding() {
        return mEcnEncoding;
    }

    public BarcodeType getBarcodeType() {
        return mBarcodeType;
    }

    public String getBarcode() {
        return mBarcode;
    }

    public String getPrefix() {
        return mPrefix;
    }

    public String getLabCode() {
        return mBarcodeType.hasLabCode ? mLabCode_or_custodyOf : null;
    }

    public String getCustodyOf() {
        return mBarcodeType.hasCustodyOf ? mLabCode_or_custodyOf : null;
    }

    public String getEcn() {
        return mBarcodeType.hasEcn ? mEcn_or_locationName : null;
    }

    public Long getDecodedEcn() {
        return mBarcodeType.hasEcn ? mDecodedEcn : null;
    }

    public String getLocationName() {
        return mBarcodeType.hasLocationName ? mEcn_or_locationName : null;
    }

    public boolean isOfType(BarcodeType type) {
        return mBarcodeType == type;
    }

    PlcBarcode(String barcode) {
        mBarcode = barcode;

        if (mBarcode == null || mBarcode.isEmpty()) {
            mIsEcnValid = false;
            mEcnEncoding = null;
            mBarcodeType = null;
            mDecodedEcn = -1;
            mPrefix = null;
            mLabCode_or_custodyOf = null;
            mEcn_or_locationName = null;
            return;
        }

        for (BarcodeType temp_barcodeType : BarcodeType.values()) {
            for (Pair<String, Encoding> prefixEncoding : temp_barcodeType.prefixEncodings) {
                String temp_prefix = prefixEncoding.first;
                Encoding temp_ecnEncoding = prefixEncoding.second;
                if (!mBarcode.startsWith(temp_prefix)) {
                    continue;
                }

                int prefixLength = temp_prefix.length();
                boolean temp_isEcnValid;
                long temp_decodedEcn;
                String temp_labCode_or_custodyOf;
                String temp_ecn_or_locationName;
                if (temp_barcodeType == BarcodeType.Location) {
                    if (temp_barcodeType.hasCustodyOf) {
                        if (mBarcode.length() >= (prefixLength + CUSTODY_OF_LENGTH)) {
                            temp_labCode_or_custodyOf = mBarcode.substring(prefixLength, prefixLength + CUSTODY_OF_LENGTH).trim();
                            temp_ecn_or_locationName = mBarcode.substring(temp_labCode_or_custodyOf.length()).trim();
                        } else {
                            temp_labCode_or_custodyOf = null;
                            temp_ecn_or_locationName = null;
                        }
                    } else {
                        temp_labCode_or_custodyOf = null;
                        temp_ecn_or_locationName = barcode.substring(prefixLength).trim();
                    }

                    temp_isEcnValid = false;
                    temp_decodedEcn = -1;
                } else {
                    if (temp_barcodeType.hasLabCode) {
                        if (mBarcode.length() >= (prefixLength + LAB_CODE_LENGTH)) {
                            temp_labCode_or_custodyOf = mBarcode.substring(prefixLength, prefixLength + LAB_CODE_LENGTH);
                            temp_ecn_or_locationName = mBarcode.substring(prefixLength + temp_labCode_or_custodyOf.length());
                        } else {
                            temp_labCode_or_custodyOf = null;
                            temp_ecn_or_locationName = null;
                        }
                    } else {
                        temp_labCode_or_custodyOf = null;
                        temp_ecn_or_locationName = mBarcode.substring(prefixLength);
                    }

                    if (temp_ecnEncoding != null && temp_ecn_or_locationName != null) {
                        switch (temp_ecnEncoding) {
                            case Base32:
                                temp_isEcnValid = Base32.validate(temp_ecn_or_locationName);
                                if (!temp_isEcnValid) continue;
                                temp_decodedEcn = Base32.decode(temp_ecn_or_locationName);
                                break;
                            case Base64:
                                temp_isEcnValid = Base64.validate(temp_ecn_or_locationName);
                                if (!temp_isEcnValid) continue;
                                temp_decodedEcn = Base64.decode(temp_ecn_or_locationName);
                                break;
                            default:
                                temp_isEcnValid = false;
                                temp_decodedEcn = -1;
                        }
                    } else {
                        temp_isEcnValid = false;
                        temp_decodedEcn = -1;
                    }
                }

                mIsEcnValid = temp_isEcnValid;
                mEcnEncoding = temp_ecnEncoding;
                mBarcodeType = temp_barcodeType;
                mDecodedEcn = temp_decodedEcn;
                mPrefix = temp_prefix;
                mLabCode_or_custodyOf = temp_labCode_or_custodyOf;
                mEcn_or_locationName = temp_ecn_or_locationName;
                return;
            }
        }

        mIsEcnValid = false;
        mEcnEncoding = null;
        mBarcodeType = null;
        mDecodedEcn = -1;
        mPrefix = null;
        mLabCode_or_custodyOf = null;
        mEcn_or_locationName = null;
    }

    public static class Base32 {
        public static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
        public static final int BASE_32_SIZE = 4;

        public static boolean validate(String base32String) {
            if (base32String.length() != BASE_32_SIZE) {
                return false;
            }

            for (int i = 0; i < base32String.length(); i++) {
                if (CHARSET.indexOf(base32String.charAt(i)) < 0) {
                    return false;
                }
            }

            return true;
        }

        public static long decode(String base32String) {
            long base32Long = 0;
            for (int i = 0; i < base32String.length(); i++) {
                base32Long <<= 5;
                base32Long |= getBase32Num(base32String.charAt(i));
            }

            return base32Long;
        }

        public static int getBase32Num(char base32char) {
            int index = CHARSET.indexOf(base32char);
            if (index == -1) {
                throw new IllegalArgumentException("\"" + base32char + "\" is not a base32 character");
            }

            return index;
        }

        public static String encode(final long base32Long) {
            if (base32Long < 0) {
                throw new IllegalArgumentException("number cannot be negative :" + base32Long);
            }

            StringBuilder base32String = new StringBuilder();
            long tempBase32Long = base32Long;
            for (int i = 0; i < BASE_32_SIZE; i++) {
                base32String.insert(0, getBase32Char((int) (tempBase32Long & 0x1f)));
                tempBase32Long >>= 5;
            }

            if (decode(base32String.toString()) != base32Long) {
                throw new IllegalArgumentException("unable to encode \"" + base32Long + "\"");
            }

            return base32String.toString();
        }

        public static char getBase32Char(final int base32Int) {
            if (base32Int < 0 || base32Int > 31) {
                throw new IllegalArgumentException("\"" + base32Int + "\" out of bounds");
            }

            return CHARSET.charAt(base32Int);
        }
    }

    public static class Base64 {
        public static final String CHARSET = "0123456789+ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdefghijklmnopqrstuvwxyz";
        public static final int BASE_64_SIZE = 5;

        public static boolean validate(String base64String) {
            if (base64String.length() != BASE_64_SIZE) {
                return false;
            }

            for (int i = 0; i < base64String.length(); i++) {
                if (CHARSET.indexOf(base64String.charAt(i)) < 0) {
                    return false;
                }
            }

            return true;
        }

        public static long decode(String base64String) {
            long base64Long = 0;
            for (int i = 0; i < base64String.length(); i++) {
                base64Long <<= 6;
                base64Long |= getBase64Num(base64String.charAt(i));
            }

            return base64Long;
        }

        public static int getBase64Num(char base64char) {
            int index = CHARSET.indexOf(base64char);
            if (index == -1) {
                throw new IllegalArgumentException("\"" + base64char + "\" is not a base64 character");
            }

            return index;
        }

        public static String encode(final long base64Long) {
            if (base64Long < 0) {
                throw new IllegalArgumentException("number cannot be negative :" + base64Long);
            }

            StringBuilder base64String = new StringBuilder();
            long tempBase64Long = base64Long;
            for (int i = 0; i < BASE_64_SIZE; i++) {
                base64String.insert(0, getBase64Char((int) (tempBase64Long & 0x3f)));
                tempBase64Long >>= 6;
            }

            if (decode(base64String.toString()) != base64Long) {
                throw new IllegalArgumentException("unable to encode \"" + base64Long + "\"");
            }

            return base64String.toString();
        }

        public static char getBase64Char(final int base64Int) {
            if (base64Int < 0 || base64Int > 63) {
                throw new IllegalArgumentException("\"" + base64Int + "\" out of bounds");
            }

            return CHARSET.charAt(base64Int);
        }
    }
}
