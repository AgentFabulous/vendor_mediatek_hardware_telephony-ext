package org.lineageos.mediatek.telephony;

import android.os.Parcel;
import android.os.Parcelable;

public class MtkQosStatus implements Parcelable {
    public int mQci;
    public int mDlGbr;
    public int mUlGbr;
    public int mDlMbr;
    public int mUlMbr;

    public MtkQosStatus(int qci, int dlGbr, int ulGbr, int dlMbr, int ulMbr) {
        mQci = qci;
        mDlGbr = dlGbr;
        mUlGbr = ulGbr;
        mDlMbr = dlMbr;
        mUlMbr = ulMbr;
    }

    public static MtkQosStatus readFrom(Parcel p) {
        int qci = p.readInt();
        int dlGbr = p.readInt();
        int ulGbr = p.readInt();
        int dlMbr = p.readInt();
        int ulMbr = p.readInt();
        return new MtkQosStatus(qci, dlGbr, ulGbr, dlMbr, ulMbr);
    }

    public void writeTo(Parcel p) {
        p.writeInt(mQci);
        p.writeInt(mDlGbr);
        p.writeInt(mUlGbr);
        p.writeInt(mDlMbr);
        p.writeInt(mUlMbr);
    }

    @Override
    public String toString() {
        return "[qci=" + mQci + ", dlGbr=" + mDlGbr + ", ulGbr=" + mUlGbr
            + ", dlMbr=" + mDlMbr + ", ulMbr=" + mUlMbr + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeTo(dest);
    }

    public static final Parcelable.Creator<MtkQosStatus> CREATOR =
            new Parcelable.Creator<MtkQosStatus>() {
        @Override
        public MtkQosStatus createFromParcel(Parcel source) {
            return MtkQosStatus.readFrom(source);
        }

        @Override
        public MtkQosStatus[] newArray(int size) {
            return new MtkQosStatus[size];
        }
    };
}
