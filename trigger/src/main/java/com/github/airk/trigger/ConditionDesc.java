package com.github.airk.trigger;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Transportable condition description class
 */
final class ConditionDesc implements Parcelable {
    public static final Parcelable.Creator<ConditionDesc> CREATOR = new Parcelable.Creator<ConditionDesc>() {
        public ConditionDesc createFromParcel(Parcel source) {
            return new ConditionDesc(source);
        }

        public ConditionDesc[] newArray(int size) {
            return new ConditionDesc[size];
        }
    };
    final String ident;
    boolean satisfy;

    public ConditionDesc(String ident) {
        this.ident = ident;
    }

    private ConditionDesc(Parcel in) {
        this.ident = in.readString();
        this.satisfy = in.readByte() != 0;
    }

    @Override
    public String toString() {
        return "Condition{" +
                "ident='" + ident + '\'' +
                ", satisfy=" + satisfy +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.ident);
        dest.writeByte(satisfy ? (byte) 1 : (byte) 0);
    }

    public void setSatisfy(boolean satisfy) {
        this.satisfy = satisfy;
    }
}
