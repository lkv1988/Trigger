/*
 * Copyright 2015 Kevin Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
