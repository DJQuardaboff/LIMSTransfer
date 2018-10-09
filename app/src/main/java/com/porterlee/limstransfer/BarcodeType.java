package com.porterlee.limstransfer;

import android.os.Build;

public enum BarcodeType {
    Item(false, BuildConfig.is_LIMS_system || BuildConfig.is_EMS_system, BuildConfig.is_LAM_system ? "J" : (BuildConfig.is_EMS_system ? "T" : "E"), BuildConfig.is_LAM_system ? "J" : (BuildConfig.is_EMS_system ? "t" : "e1")),
    Container(false, BuildConfig.is_LIMS_system || BuildConfig.is_LAM_system || BuildConfig.is_EMS_system, BuildConfig.is_EMS_system ? "A" : "M", BuildConfig.is_EMS_system ? "a" : "m1"),
    Location(BuildConfig.is_LIMS_system || BuildConfig.is_LAM_system, false, null, null, BuildConfig.is_EMS_system ? "L" :"V"),
    Invalid(false, false, null, null);

    private final boolean hasCustodyOf;
    private final boolean hasLabCode;
    private final String base32Prefix;
    private final String base64Prefix;
    private final String[] otherPrefixes;

    BarcodeType(boolean hasCustodyOf, boolean hasLabCode, String base32Prefix, String base64Prefix, String... otherPrefixes) {
        this.hasCustodyOf = hasCustodyOf;
        this.hasLabCode = hasLabCode;
        this.base32Prefix = base32Prefix;
        this.base64Prefix = base64Prefix;
        if (otherPrefixes == null || (otherPrefixes.length == 1 && otherPrefixes[0] == null)) {
            this.otherPrefixes = null;
        } else {
            this.otherPrefixes = otherPrefixes;
        }
    }

    public String getBase32Prefix() {
        return base32Prefix;
    }

    public String getBase64Prefix() {
        return base64Prefix;
    }

    public String[] getOtherPrefixes() {
        return otherPrefixes;
    }

    public static String getLocationName(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);

        if (barcodeType.equals(Location)) {
            return barcodeType.getLocationName_fast(barcode);
        }

        return null;
    }

    private String getLocationName_fast(String barcode) {
        if (hasCustodyOf) {
            return barcode != null ? barcode.substring(getLocationCustodyOf_fast(barcode).length()).trim() : null;
        } else {
            String prefix = getPrefix_fast(barcode);
            return prefix != null ? barcode.substring(prefix.length()).trim() : null;
        }
    }

    public static String getLocationCustodyOf(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);

        if (barcodeType.equals(Location)) {
            return barcodeType.getLocationCustodyOf_fast(barcode);
        }

        return null;
    }

    private String getLocationCustodyOf_fast(String barcode) {
        if (hasCustodyOf) {
            String prefix = getPrefix_fast(barcode);
            if (prefix != null) {
                int prefixLength = prefix.length();
                return barcode.substring(prefixLength, prefixLength + 4).trim();
            }
        }

        return null;
    }

    public static String getEcn(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);

        if (barcodeType.equals(Item) || barcodeType.equals(Container)) {
            return barcodeType.getEcn_fast(barcode);
        }

        return null;
    }

    private String getEcn_fast(String barcode) {
        String prefix = getPrefix_fast(barcode);
        if (prefix != null) {
            int prefixLength = prefix.length();
            String labCode = getLabCode_fast(barcode);
            if (labCode != null) {
                return barcode.substring(prefixLength + labCode.length());
            } else {
                return barcode.substring(prefixLength);
            }
        }

        return null;
    }

    public static String getLabCode(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);

        if (barcodeType.equals(Item) || barcodeType.equals(Container)) {
            return barcodeType.getLabCode_fast(barcode);
        }

        return null;
    }

    private String getLabCode_fast(String barcode) {
        if (hasLabCode) {
            String prefix = getPrefix_fast(barcode);
            if (prefix != null) {
                int prefixLength = prefix.length();
                return barcode.substring(prefixLength, prefixLength + 3);
            }
        }

        return null;
    }

    public static String getPrefix(String barcode) {
        if (barcode != null) {
            for (BarcodeType barcodeType : BarcodeType.values()) {
                if (barcodeType.base32Prefix != null && barcode.startsWith(barcodeType.base32Prefix)) {
                    return barcodeType.base32Prefix;
                } else if (barcodeType.base64Prefix != null && barcode.startsWith(barcodeType.base64Prefix)) {
                    return barcodeType.base64Prefix;
                } else if (barcodeType.otherPrefixes != null) {
                    for (String prefix : barcodeType.otherPrefixes) {
                        if (barcode.startsWith(prefix)) {
                            return prefix;
                        }
                    }
                }
            }
        }

        return null;
    }

    private String getPrefix_fast(String barcode) {
        if (barcode != null) {
            if (base32Prefix != null && barcode.startsWith(base32Prefix)) {
                return base32Prefix;
            } else if (base64Prefix != null && barcode.startsWith(base64Prefix)) {
                return base64Prefix;
            } else if (otherPrefixes != null) {
                for (String prefix : otherPrefixes) {
                    if (barcode.startsWith(prefix)) {
                        return prefix;
                    }
                }
            }
        }

        return null;
    }

    public boolean isOfType(String barcode) {
        if (this.equals(Invalid) || barcode == null) {
            return getBarcodeType(barcode).equals(Invalid);
        }

        if (base32Prefix != null && barcode.startsWith(base32Prefix)) {
            return true;
        } else if (base64Prefix != null && barcode.startsWith(base64Prefix)) {
            return true;
        } else if (otherPrefixes != null) {
            for (String prefix : otherPrefixes) {
                if (barcode.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static BarcodeType getBarcodeType(String barcode) {
        if (barcode != null) {
            for (BarcodeType barcodeType : BarcodeType.values()) {
                if (barcodeType.base32Prefix != null && barcode.startsWith(barcodeType.base32Prefix)) {
                    return barcodeType;
                } else if (barcodeType.base64Prefix != null && barcode.startsWith(barcodeType.base64Prefix)) {
                    return barcodeType;
                } else if (barcodeType.otherPrefixes != null) {
                    for (String prefix : barcodeType.otherPrefixes) {
                        if (barcode.startsWith(prefix)) {
                            return barcodeType;
                        }
                    }
                }
            }
        }

        return Invalid;
    }

    public static boolean getIsBase32(String barcode) {
        if (barcode != null) {
            for (BarcodeType barcodeType : BarcodeType.values()) {
                if (barcodeType.getIsBase32_fast(barcode)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean getIsBase32_fast(String barcode) {
        if (base32Prefix != null) {
            String ecn = getEcn_fast(barcode);
            return ecn != null && Base32.validate(ecn);
        }

        return false;
    }

    public static String convertToBase32(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);
        if (barcode == null || barcodeType.base64Prefix == null || barcodeType.base32Prefix == null) {
            return null;
        } else if (barcodeType.getIsBase32_fast(barcode)) {
            return barcode;
        } else if (!barcodeType.getIsBase64_fast(barcode)) {
            return null;
        }

        int base64PrefixLength = barcodeType.base64Prefix.length();
        String labCode = barcodeType.getLabCode_fast(barcode);
        if (labCode != null) {
            return barcodeType.base32Prefix + labCode + Base32.encode(Base64.decode(barcode.substring(base64PrefixLength + labCode.length())));
        } else {
            return barcodeType.base32Prefix + Base32.encode(Base64.decode(barcode.substring(base64PrefixLength)));
        }
    }

    public static boolean getIsBase64(String barcode) {
        if (barcode != null) {
            for (BarcodeType barcodeType : BarcodeType.values()) {
                if (barcodeType.getIsBase64_fast(barcode)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean getIsBase64_fast(String barcode) {
        if (base64Prefix != null) {
            String ecn = getEcn_fast(barcode);
            return ecn != null && Base64.validate(ecn);
        }

        return false;
    }

    public static String convertToBase64(String barcode) {
        BarcodeType barcodeType = getBarcodeType(barcode);
        if (barcode == null || barcodeType.base32Prefix == null || barcodeType.base64Prefix == null) {
            return null;
        } else if (barcodeType.getIsBase64_fast(barcode)) {
            return barcode;
        } else if (!barcodeType.getIsBase32_fast(barcode)) {
            return null;
        }

        int base32PrefixLength = barcodeType.base32Prefix.length();
        String labCode = barcodeType.getLabCode_fast(barcode);
        if (labCode != null) {
            return barcodeType.base64Prefix + labCode + Base64.encode(Base32.decode(barcode.substring(base32PrefixLength + labCode.length())));
        } else {
            return barcodeType.base64Prefix + Base64.encode(Base32.decode(barcode.substring(base32PrefixLength)));
        }
    }

    public static class Base32 {
        public static final String charsetString = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
        public static final int BASE_32_SIZE = 4;

        public static boolean validate(String base32String) {
            if (base32String.length() != BASE_32_SIZE) {
                return false;
            }

            for (int i = 0; i < base32String.length(); i++) {
                if (charsetString.indexOf(base32String.charAt(i)) < 0) {
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
            int index = charsetString.indexOf(base32char);
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

            return charsetString.charAt(base32Int);
        }
    }

    public static class Base64 {
        public static final String charsetString = "0123456789+ABCDEFGHIJKLMNOPQRSTUVWXYZ-abcdefghijklmnopqrstuvwxyz";
        public static final int BASE_64_SIZE = 5;

        public static boolean validate(String base64String) {
            if (base64String.length() != BASE_64_SIZE) {
                return false;
            }

            for (int i = 0; i < base64String.length(); i++) {
                if (charsetString.indexOf(base64String.charAt(i)) < 0) {
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
            int index = charsetString.indexOf(base64char);
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

            return charsetString.charAt(base64Int);
        }
    }
}
