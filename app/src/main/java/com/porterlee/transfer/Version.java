package com.porterlee.transfer;

public class Version implements Comparable<Version> {
    public final int major;
    public final int minor;
    public final int patch;

    Version(
            int major,
            int minor,
            int patch
    ) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    Version(
            int version_code
    ) {
        this.major = (version_code / 10000) % 100;
        this.minor = (version_code / 100) % 100;
        this.patch = (version_code) % 100;
    }

    public int getVersionCode() {
        return (major * 10000) + (minor * 100) + patch;
    }

    @Override
    public int compareTo(Version v) {
        if (major != v.major) {
            return major - v.major;
        } else if (minor != v.minor) {
            return minor - v.minor;
        } else {
            return patch - v.patch;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Version) {
            return equals((Version) o);
        }
        return false;
    }

    public boolean equals(Version v) {
        return major == v.major
                && minor == v.minor
                && patch == v.patch;
    }
}
